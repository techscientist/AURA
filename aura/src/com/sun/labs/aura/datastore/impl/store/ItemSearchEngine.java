/*
 * Copyright 2007-2009 Sun Microsystems, Inc. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 * 
 * This code is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * only, as published by the Free Software Foundation.
 * 
 * This code is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is
 * included in the LICENSE file that accompanied this code).
 * 
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA
 * 
 * Please contact Sun Microsystems, Inc., 16 Network Circle, Menlo
 * Park, CA 94025 or visit www.sun.com if you need additional
 * information or have any questions.
 */
package com.sun.labs.aura.datastore.impl.store;

import com.sun.labs.aura.datastore.Indexable;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.SimilarityConfig;
import com.sun.labs.aura.datastore.impl.store.persist.FieldDescription;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.Counted;
import com.sun.labs.aura.util.Scored;
import com.sun.labs.aura.util.WordCloud;
import com.sun.labs.minion.CompositeResultsFilter;
import com.sun.labs.minion.DocumentVector;
import com.sun.labs.minion.FieldInfo;
import com.sun.labs.minion.FieldValue;
import com.sun.labs.minion.IndexableString;
import com.sun.labs.minion.Posting;
import com.sun.labs.minion.QueryStats;
import com.sun.labs.minion.Result;
import com.sun.labs.minion.ResultSet;
import com.sun.labs.minion.ResultsFilter;
import com.sun.labs.minion.SearchEngine;
import com.sun.labs.minion.SearchEngineException;
import com.sun.labs.minion.SearchEngineFactory;
import com.sun.labs.minion.classification.ClassifierModel;
import com.sun.labs.minion.classification.ExplainableClassifierModel;
import com.sun.labs.minion.classification.FeatureCluster;
import com.sun.labs.minion.classification.WeightedFeature;
import com.sun.labs.minion.engine.SearchEngineImpl;
import com.sun.labs.minion.indexer.entry.DocKeyEntry;
import com.sun.labs.minion.indexer.entry.QueryEntry;
import com.sun.labs.minion.indexer.partition.DiskPartition;
import com.sun.labs.minion.indexer.partition.InvFileDiskPartition;
import com.sun.labs.minion.indexer.postings.PostingsIterator;
import com.sun.labs.minion.indexer.postings.PostingsIteratorFeatures;
import com.sun.labs.minion.query.Element;
import com.sun.labs.minion.query.Relation;
import com.sun.labs.minion.retrieval.CompositeDocumentVectorImpl;
import com.sun.labs.minion.retrieval.DocumentVectorImpl;
import com.sun.labs.minion.retrieval.FieldEvaluator;
import com.sun.labs.minion.retrieval.ResultAccessorImpl;
import com.sun.labs.minion.retrieval.ResultImpl;
import com.sun.labs.minion.retrieval.ResultSetImpl;
import com.sun.labs.minion.util.FileLockException;
import com.sun.labs.minion.util.NanoWatch;
import com.sun.labs.minion.util.Util;
import com.sun.labs.util.props.ConfigBoolean;
import com.sun.labs.util.props.ConfigInteger;
import com.sun.labs.util.props.ConfigString;
import com.sun.labs.util.props.Configurable;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import com.sun.labs.util.FileUtil;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A search engine for the data associated with items in the item store.
 * The engine will index all of the data in the map which is of the types that we 
 * know about (strings, dates, and numbers.)
 * 
 * <p>
 * 
 * All values from the map will be saved and therefore available for parametric
 * searching.  String values will also be indexed, tokenized, and vectored so 
 * that they can be used for simple searching as well as item similarity 
 * computations.
 * 
 * <p>
 * 
 * Data values in the map that implement the <code>Indexable</code> interface will
 * be changed into strings by calling the <code>toString</code> method on the object
 * before it is indexed.
 * 
 */
public class ItemSearchEngine implements Configurable {

    private SearchEngine engine;

    private boolean engineWasIntialized;

    private Logger logger;

    private int engineLogLevel;

    @SuppressWarnings(value = "IS2_INCONSISTENT_SYNC",
    justification = "The field is the purpose of the sync blocks")
    private boolean shuttingDown;

    private long flushCheckInterval;

    private Timer flushTimer;

    private String indexDir;

    public ItemSearchEngine() {
    }

    /**
     * Creates an item search engine pointed at a particular index directory.
     * @param indexDir
     * @param config
     */
    public ItemSearchEngine(String indexDir, String config) {
        this.indexDir = indexDir;
        logger = Logger.getLogger(getClass().getName());
        try {
            URL cu = ItemSearchEngine.class.getResource(config);

            //
            // Creates the search engine.  We'll use a full blown fields-and-all
            // engine because we need to be able to handle fielded doc vectors
            // and postings.
            engine = SearchEngineFactory.getSearchEngine(indexDir,
                                                         "aardvark_search_engine",
                                                         cu);
        } catch(SearchEngineException see) {
            logger.log(Level.SEVERE, "error opening engine for: " + indexDir,
                       see);
        }
    }

    public void redefineFields(BerkeleyDataWrapper bdw) throws AuraException {
        Map<String, FieldDescription> fields = bdw.getFieldDescriptions();
        for(Map.Entry<String, FieldDescription> e : fields.entrySet()) {
            FieldDescription desc = e.getValue();
            if(desc.isIndexed()) {
                defineField(e.getKey(), desc.getType(), desc.getCapabilities());
            }
        }
    }

    public void regenerateTermStats() {
        try {
            engine.getPM().recalculateTermStats();
        } catch(IOException ex) {
            logger.log(Level.SEVERE, "Error regenerating term stats", ex);
        } catch(FileLockException ex) {
            logger.log(Level.SEVERE, "Error regenerating term stats", ex);
        }
    }

    public void newProperties(PropertySheet ps) throws PropertyException {

        //
        // Load up the search engine.
        logger = ps.getLogger();
        indexDir = ps.getString(PROP_INDEX_DIR);

        //
        // Get rid of the index directory.
        if(ps.getBoolean(PROP_DELETE_INDEX)) {
            logger.info(String.format("Deleting index directory"));
            FileUtil.deleteDirectory(new File(indexDir));
        }

        //
        // If the index directory doesn't exist, then we needed to initialize it.
        engineWasIntialized = !(new File(indexDir).exists());

        boolean copyDir = ps.getBoolean(PROP_COPY_DIR);
        String copyLocation = ps.getString(PROP_COPY_LOCATION);

        //
        // If we want to copy the data into temp storage, do it now.
        if(copyDir) {
            if(copyLocation.equals("")) {
                copyLocation = System.getProperty("java.io.tmpdir");
            }
            File td = new File(new File(new File(copyLocation), String.format("replicant-%s",
                    ps.getString(PROP_PREFIX))), "itemIndex.idx");
            if(!td.mkdirs()) {
                throw new PropertyException(ps.getInstanceName(),
                                            PROP_COPY_DIR,
                                            "Unable to make temporary directory for search index");
            }
            try {
                logger.info(String.format("Copying search index from %s into temp dir %s", indexDir, td));
                FileUtil.dirCopier(new File(indexDir), td);
                indexDir = td.toString();
                logger.info(String.format("Copying completed, indexDir: %s", indexDir));
            } catch(IOException ex) {
                throw new PropertyException(ex, ps.getInstanceName(),
                                            PROP_COPY_DIR,
                                            "Unable to copy search index directory");
            }
        }
        String engineConfig = ps.getString(PROP_ENGINE_CONFIG_FILE);

        try {
            URL config = ItemSearchEngine.class.getResource(engineConfig);

            //
            // Creates the search engine.  We'll use a full blown fields-and-all
            // engine because we need to be able to handle fielded doc vectors
            // and postings.
            engine = SearchEngineFactory.getSearchEngine(indexDir,
                                                         "aardvark_search_engine",
                                                         config);

            if(ps.getBoolean(PROP_REGENERATE_TERM_STATS)) {
                regenerateTermStats();
            }
        } catch(SearchEngineException see) {
            logger.log(Level.SEVERE, "error opening engine for: " + indexDir,
                       see);
        }

        //
        // Set up for periodically flushing the data to disk.
        flushCheckInterval = ps.getInt(PROP_FLUSH_INTERVAL);
        flushTimer = new Timer("ItemSearchEngineFlushTimer");
        flushTimer.scheduleAtFixedRate(new FlushTimerTask(), flushCheckInterval,
                                       flushCheckInterval);

    }

    public SearchEngine getSearchEngine() {
        return engine;
    }

    public boolean engineWasInitialized() {
        return engineWasIntialized;
    }

    public void defineField(String fieldName,
                            Item.FieldType fieldType,
                            EnumSet<Item.FieldCapability> caps) throws
            AuraException {
        EnumSet<FieldInfo.Attribute> attr =
                EnumSet.of(
                FieldInfo.Attribute.SAVED,
                FieldInfo.Attribute.INDEXED,
                FieldInfo.Attribute.VECTORED);
        if(caps.contains(Item.FieldCapability.TOKENIZED)) {
            attr.add(FieldInfo.Attribute.TOKENIZED);
        }

        if(attr.contains(FieldInfo.Attribute.SAVED) && fieldType == null) {
            throw new IllegalArgumentException("Indexed field " + fieldName +
                    " requires field type to be specified.");
        }

        //
        // We may have been passed a type when it's not necessary, so we'll 
        // just hide that from the engine.
        FieldInfo.Type defineType = FieldInfo.Type.valueOf(fieldType.toString());
        try {
            engine.defineField(new FieldInfo(fieldName, attr, defineType));
        } catch(SearchEngineException ex) {
            throw new AuraException("Error defining field " + fieldName, ex);
        }
    }

    /**
     * Indexes an item.  Note that the data indexed may not be available immediately
     * for searching, depending on the configuration of the indexer.
     * 
     * @param item the item to index
     * @return <code>true</code> if the item was added to the index, <code>false</code>
     * otherwise.
     */
    public boolean index(Item item) {
        if(shuttingDown) {
            return false;
        }

        try {

            //
            // Get the item's map, and make a map for ourselves of just the 
            // stuff that we want to index.
            Map<String, Object> im = new HashMap<String, Object>();

            //
            // Add the data that we want in every map.
            //im.put("aura-id", item.getID());
            im.put("aura-key", item.getKey());
            im.put("aura-name", item.getName());
            im.put("aura-type", item.getType().toString());

            //
            // Index the elements of the map that require indexing.
            for(Map.Entry<String, Serializable> e : item) {
                Serializable val = e.getValue();

                //
                // We need to make sure that if an item changes, it doesn't
                // get an ever-growing set of autotags, so we won't add any
                // autotags when indexing.
                if(e.getKey().equalsIgnoreCase("autotag")) {
                    continue;
                }

                //
                // OK, first up, make sure that we have an appropriately defined
                // field for this name.  We'll need to make sure that we're not
                // clobbering field types as we go.
                FieldInfo fi = engine.getFieldInfo(e.getKey());

                if(fi == null) {
                    //
                    // We should have had this field defined, so we can skip this
                    // one.
                    continue;
                }

                FieldInfo.Type type = getType(val);

                //
                // Ignore stuff we don't know how to handle.
                if(type == FieldInfo.Type.NONE) {
                    continue;
                }

                //
                // Now get a value to put in the index map.
                Object indexVal = val;
                if(indexVal instanceof Map) {
                    indexVal = ((Map) indexVal).values();
                } else if(indexVal instanceof WordCloud) {
                    indexVal = ((WordCloud) indexVal).getWords().values();
                } else if(val instanceof Indexable ||
                        val instanceof String) {
                    //
                    // The content might contain XML or HTML, so let's get
                    // rid of that stuff.
                    indexVal = new IndexableString(indexVal.toString(),
                                                   IndexableString.Type.HTML);
                }
                im.put(e.getKey(), indexVal);
            }

            engine.index(item.getKey(), im);
            return true;
        } catch(SearchEngineException ex) {
            logger.log(Level.SEVERE, "Exception indexing " + item.getKey(), ex);
        }
        return false;
    }

    /**
     * Gets the field type appropriate for an object.
     * @param val the value that we want the appropriate type for
     * @return the appropriate type, or <code>FieldInfo.Type.NONE</code> if there
     * is no appropriate type.
     */
    private FieldInfo.Type getType(Object val) {
        if(val instanceof Indexable || val instanceof Indexable[] ||
                val instanceof Posting || val instanceof Posting[]) {
            return FieldInfo.Type.STRING;
        }

        if(val instanceof String || val instanceof String[]) {
            return FieldInfo.Type.STRING;
        }

        if(val instanceof Date || val instanceof Date[]) {
            return FieldInfo.Type.DATE;
        }

        if(val instanceof Integer || val instanceof Integer[] ||
                val instanceof Long || val instanceof Long[]) {
            return FieldInfo.Type.INTEGER;
        }

        if(val instanceof Float || val instanceof Float[] ||
                val instanceof Double || val instanceof Double[]) {
            return FieldInfo.Type.FLOAT;
        }


        //
        // The type of a map is the type of its values.  Arbitrary, but fun!
        if(val instanceof Map) {
            return getType(((Map) val).values());
        }

        //
        // Figure out an appropriate type for a collection.  We first want to 
        // make sure that all of the elements are of the same type.  This would
        // be a lot easier if we had real generic types, but there you go.  We'll
        // ignore zero length collections, because how would we know if it was 
        // indexed or not, eh?
        //
        // Once we figure out that everything is the same type, then we can
        // return a field type.  The underlying search engine can handle the
        // collection for itself.
        if(val instanceof Collection) {
            Collection c = (Collection) val;
            if(c.size() > 0) {
                Iterator i = c.iterator();
                Object o = i.next();
                FieldInfo.Type type = getType(o);
                if(type == FieldInfo.Type.NONE) {
                    return type;
                }
                while(i.hasNext()) {
                    Object o2 = i.next();
                    if(!o2.getClass().equals(o.getClass())) {
                        return FieldInfo.Type.NONE;
                    }
                }
                //
                // Return the type for the first object.
                return type;
            }
        }

        return FieldInfo.Type.NONE;

    }

    /**
     * Gets a set of attributes suitable for a given field type.
     */
    private EnumSet<FieldInfo.Attribute> getAttributes(FieldInfo.Type type) {
        //
        // Everything's saved.
        EnumSet<FieldInfo.Attribute> ret = EnumSet.of(FieldInfo.Attribute.SAVED);
        //
        // Strings get indexed.
        if(type == FieldInfo.Type.STRING) {
            ret.addAll(FieldInfo.getIndexedAttributes());
        }
        return ret;
    }

    public void delete(String key) {
        engine.delete(key);
    }

    /**
     * Gets the document associated with a given key.
     * @param key the key of the entry that we want the document vector for
     * @return the document vector associated with the key.  If the entry with this
     * key has not been indexed or if an error occurs while fetching the docuemnt,
     * then <code>null</code> will be returned.
     */
    public DocumentVector getDocumentVector(String key, SimilarityConfig config) {
        if(config.getField() == null && config.getFields() == null) {
            try {
                return engine.getDocumentVector(key);
            } catch(SearchEngineException ex) {
                return null;
            }
        } else if(config.getField() != null) {
            return engine.getDocumentVector(key, config.getField());
        } else {
            return engine.getDocumentVector(key, config.getFields());
        }
    }

    public DocumentVector getDocumentVector(WordCloud cloud,
                                            SimilarityConfig config) {

        //
        // Get weighted features from the cloud.  We'll only handle things with 
        // positive weights.
        List<WeightedFeature> feat = new ArrayList<WeightedFeature>();
        for(Scored<String> s : cloud) {
            if(s.getScore() > 0) {
                feat.add(new WeightedFeature(s.getItem(), (float) s.getScore()));
            }
        }
        if(config.getFields() == null) {
            DocumentVectorImpl dvi = new DocumentVectorImpl(engine,
                                                            feat.toArray(
                    new WeightedFeature[0]));
            dvi.setField(config.getField());
            return dvi;
        } else {
            return new CompositeDocumentVectorImpl(engine, feat.toArray(
                    new WeightedFeature[0]), config.getFields());
        }
    }

    /**
     * Finds the n most-similar items to the given item, based on the data in the 
     * provided field.
     * @param dv the document vector for the item of interest
     * @param config the configuration for the find similar operation
     * @return the set of items most similar to the given item, based on the
     * data indexed into the given field.  Note that the returned set may be
     * smaller than the number of items requested!
     * @see #getDocumentVector
     */
    public List<Scored<String>> findSimilar(DocumentVector dv,
                                            SimilarityConfig config)
            throws AuraException {

        //
        // Recover from having been serialized.
        dv.setEngine(engine);

        List<Scored<String>> ret = new ArrayList<Scored<String>>();
        NanoWatch nw = new NanoWatch();
        nw.start();
        ResultSet sim = null;
        try {
            sim = dv.findSimilar("-score", config.getSkimPercent());

            Set<String> include = config.getInclude();
            if(include != null && include.size() > 0) {
                ResultSet inc = ((SearchEngineImpl) engine).allTerms(include,
                                                                     config.
                        getFieldNames(), false);
                sim = sim.intersect(inc);
            }

            //
            // See if we need to exclude some terms.
            Set<String> exclude = config.getExclude();
            if(exclude != null && exclude.size() > 0) {
                ResultSet exc = ((SearchEngineImpl) engine).anyTerms(exclude,
                                                                     config.
                        getFieldNames(), false);
                sim = sim.difference(exc);
            }
            for(Result r : sim.getResults(0, config.getN(), config.getFilter())) {
                ResultImpl ri = (ResultImpl) r;
                ret.add(new Scored<String>(ri.getKey(),
                                           ri.getScore(),
                                           ri.getSortVals(),
                                           ri.getDirections()));
            }
        } catch(SearchEngineException see) {
            throw new AuraException("Error getting similar items", see);
        }
        nw.stop();
        int nt = 0;
        int np = 0;
        if(config.getFilter() instanceof CompositeResultsFilter) {
            CompositeResultsFilter crf = (CompositeResultsFilter) config.
                    getFilter();
            nt = crf.getTested();
            np = crf.getPassed();
        }
        if(logger.isLoggable(Level.FINER)) {
            logger.finer(String.format(
                    "fsgr %s docs: %d test: %d pass: %d gr: %.2f",
                    dv.getKey(),
                    sim.size(),
                    nt, np,
                    nw.getTimeMillis()));
        }
        return ret;
    }

    public WordCloud getTopTerms(String key, String field, int n)
            throws AuraException, RemoteException {
        DocumentVectorImpl dv =
                (DocumentVectorImpl) getDocumentVector(key,
                                                       new SimilarityConfig(
                field));
        if(dv == null) {
            return new WordCloud();
        }
        WeightedFeature[] wf = dv.getFeatures();
        Util.sort(wf, WeightedFeature.getInverseWeightComparator());
        WordCloud ret = new WordCloud();
        for(int i = 0; i < wf.length && i < n; i++) {
            ret.add(new Scored<String>(wf[i].getName(), wf[i].getWeight()));
        }
        return ret;
    }

    public List<Counted<String>> getTopTermCounts(String key, String field,
                                                  int n)
            throws AuraException, RemoteException {

        DocumentVectorImpl dv =
                (DocumentVectorImpl) getDocumentVector(key,
                                                       new SimilarityConfig(
                field));
        List<Counted<String>> ret = new ArrayList<Counted<String>>();
        if(dv == null) {
            return ret;
        }
        WeightedFeature[] wf = dv.getFeatures();
        Util.sort(wf, WeightedFeature.INV_COUNT_COMPARATOR);
        for(int i = 0; i < wf.length && i < n; i++) {
            ret.add(new Counted<String>(wf[i].getName(), wf[i].getFreq()));
        }
        return ret;
    }

    public List<Counted<String>> getTermCounts(String term, String field, int n,
                                               ResultsFilter rf)
            throws AuraException, RemoteException {
        FieldInfo fi = engine.getFieldInfo(field);
        if(fi == null) {
            return new ArrayList<Counted<String>>();
        }
        PostingsIteratorFeatures feat = new PostingsIteratorFeatures();
        feat.setFields(((SearchEngineImpl) engine).getPM().getMetaFile().
                getFieldArray(field));
        List<Counted<String>> ret = new ArrayList<Counted<String>>();

        ResultAccessorImpl ri = null;
        if(rf != null) {
            ri = new ResultAccessorImpl();
        }
        for(DiskPartition p : ((SearchEngineImpl) engine).getPM().
                getActivePartitions()) {
            QueryEntry qe = p.getTerm(term);
            if(qe == null) {
                continue;
            }
            if(ri != null) {
                ri.setPartition((InvFileDiskPartition) p);
            }
            PostingsIterator pi = qe.iterator(feat);
            while(pi.next()) {
                //
                // We won't return deleted documents.
                if(!p.isDeleted(pi.getID())) {

                    //
                    // We won't return documents that don't pass the filter.
                    if(rf != null) {
                        ri.setCurrDoc(pi.getID());
                        if(!rf.filter(ri)) {
                            continue;
                        }
                    }

                    //
                    // Return the document key and the frequency.
                    DocKeyEntry dke = p.getDocumentTerm(pi.getID());
                    ret.add(new Counted<String>(dke.getName().toString(),
                                                pi.getFreq()));
                }
            }
        }
        Counted[] arr = ret.toArray(new Counted[0]);
        Util.sort(arr, Counted.INV_COUNT_COMPARATOR);
        ret.clear();
        for(int i = 0; i < n && i < arr.length; i++) {
            ret.add((Counted<String>) arr[i]);
        }
        return ret;
    }

    public List<Scored<String>> getTopFeatures(String autotag, int n) {
        ClassifierModel cm = ((SearchEngineImpl) engine).getClassifier(autotag);
        if(cm == null) {
            return new ArrayList<Scored<String>>();
        }
        PriorityQueue<FeatureCluster> q =
                new PriorityQueue<FeatureCluster>(n,
                                                  FeatureCluster.weightComparator);
        for(FeatureCluster fc : cm.getFeatures()) {
            if(q.size() < n) {
                q.offer(fc);
            } else {
                FeatureCluster top = q.peek();
                if(fc.getWeight() > top.getWeight()) {
                    q.poll();
                    q.offer(fc);
                }
            }
        }
        List<Scored<String>> ret = new ArrayList<Scored<String>>();
        while(q.size() > 0) {
            FeatureCluster fc = q.poll();
            ret.add(new Scored<String>(fc.getHumanReadableName(), fc.getWeight()));
        }
        Collections.reverse(ret);
        return ret;
    }

    public List<Scored<String>> findSimilarAutotags(String autotag, int n)
            throws AuraException, RemoteException {
        List<FieldValue> l = ((SearchEngineImpl) engine).getSimilarClassifiers(
                autotag, n);
        List<Scored<String>> ret = new ArrayList<Scored<String>>();
        for(FieldValue fv : l) {
            ret.add(new Scored<String>(fv.getValue(), fv.getScore()));
        }
        return ret;
    }

    public List<Scored<String>> explainSimilarAutotags(String a1, String a2,
                                                       int n)
            throws AuraException, RemoteException {
        List<WeightedFeature> l = ((SearchEngineImpl) engine).
                getSimilarClassifierTerms(a1, a2, n);
        List<Scored<String>> ret = new ArrayList<Scored<String>>();
        for(WeightedFeature wf : l) {
            ret.add(new Scored<String>(wf.getName(), wf.getWeight()));
        }
        return ret;
    }

    /**
     * Gets an explanation as to why a given autotag would be applied to 
     * a given document.
     * 
     * @param key the key of th item for which we want an explanation
     * @param autoTag the autotag that we want to explain
     * @param n the number of terms to return
     * @return a list of the terms that contribute the most towards the
     * autotagging.  The score associated with a term is the proportion of 
     * contribution towards the autotagging.
     */
    public List<Scored<String>> getExplanation(String key, String autoTag,
                                               int n)
            throws AuraException, RemoteException {
        ClassifierModel cm = ((SearchEngineImpl) engine).getClassifierManager().
                getClassifier(autoTag);
        if(cm == null || !(cm instanceof ExplainableClassifierModel)) {
            logger.warning("Not an explainable classifier: " + autoTag);
            return new ArrayList<Scored<String>>();
        }

        List<WeightedFeature> wf =
                ((ExplainableClassifierModel) cm).explain(key);
        List<Scored<String>> ret = new ArrayList<Scored<String>>();
        for(Iterator<WeightedFeature> i = wf.iterator(); i.hasNext() &&
                ret.size() < n;) {
            WeightedFeature f = i.next();
            ret.add(new Scored<String>(f.getName(), f.getWeight()));
        }
        return ret;
    }

    /**
     * Gets a list of the keys for the items that have a field with a given value.
     * @param name the name of the field
     * @param val the value
     * @param n the number of keys to return
     * @return a list of the keys of the items whose fields have the given value
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public List<Scored<String>> find(String name, String val, int n) throws
            AuraException, RemoteException {
        FieldEvaluator fe = new FieldEvaluator(name, Relation.Operator.EQUALS,
                                               val);
        ResultSet rs = fe.eval(engine);
        List<Scored<String>> ret = new ArrayList<Scored<String>>();
        try {
            for(Result r : rs.getResults(0, n)) {
                ret.add(new Scored<String>(r.getKey(), r.getScore()));
            }
        } catch(SearchEngineException see) {
            throw new AuraException("Error finding items", see);
        }
        return ret;
    }

    public List<Scored<String>> query(String query, String sort, int n,
                                      ResultsFilter rf) throws AuraException,
            RemoteException {
        List<Scored<String>> ret = new ArrayList<Scored<String>>();
        try {
            for(Result r : engine.search(query, sort).getResults(0, n, rf)) {
                ResultImpl ri = (ResultImpl) r;
                ret.add(new Scored<String>(ri.getKey(),
                                           ri.getScore(),
                                           ri.getSortVals(),
                                           ri.getDirections()));
            }
        } catch(SearchEngineException see) {
            //
            // The search engine exception may be wrapping an exception that
            // we don't want to send across the wire, so we should see what if
            // there's one of these in there.
            Throwable ex = see.getCause();
            if(ex instanceof com.sun.labs.minion.retrieval.parser.ParseException ||
                    ex instanceof java.text.ParseException) {
                throw new AuraException("Error parsing query: " +
                        ex.getMessage());
            }
            throw new AuraException("Error finding items", see);
        }
        return ret;
    }

    public List<Scored<String>> query(Element query, String sort, int n,
                                      ResultsFilter rf) throws AuraException,
            RemoteException {
        List<Scored<String>> ret = new ArrayList<Scored<String>>();
        try {
            for(Result r : engine.search(query, sort).getResults(0, n, rf)) {
                ResultImpl ri = (ResultImpl) r;
                ret.add(new Scored<String>(ri.getKey(),
                                           ri.getScore(),
                                           ri.getSortVals(),
                                           ri.getDirections()));
            }
        } catch(SearchEngineException see) {
            //
            // The search engine exception may be wrapping an exception that
            // we don't want to send across the wire, so we should see what if
            // there's one of these in there.
            Throwable ex = see.getCause();
            if(ex instanceof com.sun.labs.minion.retrieval.parser.ParseException ||
                    ex instanceof java.text.ParseException) {
                throw new AuraException("Error parsing query: " +
                        ex.getMessage());
            }
            throw new AuraException("Error finding items", see);
        }
        return ret;
    }

    /**
     * Gets the items that have had a given autotag applied to them.
     * @param autotag the tag that we want items to have been assigned
     * @param n the number of items that we want
     * @return a list of the item keys that have had a given autotag applied.  The
     * list is ordered by the confidence of the tag assignment
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public List<Scored<String>> getAutotagged(String autotag, int n)
            throws AuraException, RemoteException {
        try {

            List<Scored<String>> ret = new ArrayList<Scored<String>>();

            ResultSetImpl rs = (ResultSetImpl) engine.search(String.format(
                    "autotag = \"%s\"", autotag));
            for(Result r : rs.getResultsForScoredField(0, n, "autotag", autotag,
                                                       "autotag-score")) {
                ret.add(new Scored<String>(r.getKey(), r.getScore()));
            }
            return ret;


        } catch(SearchEngineException ex) {
            throw new AuraException("Error searching for autotag " + autotag, ex);
        }
    }

    /**
     * Gets a list of scored strings consisting of the autotags assigned to
     * an item and their associated classifier scores.  This requires rather
     * deeper knowledge of the field store than I am comfortable with, but using
     * the document abstraction would require fetching all of the field values.
     * 
     * <p>
     * 
     * We should probably fix the document abstraction so that it fetches on
     * demand, but not before the open house, eh?
     * 
     * <p>
     * 
     * autotagfix
     * 
     * @param key the key for the document whose autotags we want
     * @return a list of scored strings where the item is the autotag and the score
     * is the classifier score associated with the autotag.
     */
    public List<Scored<String>> getAutoTags(String key) {
        DocKeyEntry dke = ((SearchEngineImpl) engine).getDocumentTerm(key);
        if(dke == null) {
            //
            // No document by that name here...
            return null;
        }
        List<String> autotags = (List<String>) ((InvFileDiskPartition) dke.
                getPartition()).getFieldStore().getSavedFieldData("autotag",
                                                                  dke.getID(),
                                                                  true);
        if(autotags.size() == 0) {

            //
            // No tags.
            return null;
        }
        List<Double> autotagScores = (List<Double>) ((InvFileDiskPartition) dke.
                getPartition()).getFieldStore().getSavedFieldData(
                "autotag-score", dke.getID(), true);
        if(autotags.size() != autotagScores.size()) {
            logger.warning("Mismatched autotags and scores: " + autotags + " " +
                    autotagScores);
        }
        List<Scored<String>> ret = new ArrayList<Scored<String>>();
        int lim = Math.min(autotags.size(), autotagScores.size());
        for(int i = 0; i < lim; i++) {
            ret.add(new Scored<String>(autotags.get(i), autotagScores.get(i)));
        }
        return ret;
    }

    public synchronized void shutdown() {
        try {
            //
            // Stop listening for things and shut down the engine.
            shuttingDown = true;
            logger.log(Level.INFO, "Shutting down search engine");
            System.err.format("Query Stats:\n%s\n",
                              engine.getQueryStats().dump());
            engine.close();
        } catch(SearchEngineException ex) {
            logger.log(Level.WARNING, "Error closing index data engine", ex);
        }
    }

    /**
     * Get the size of the index, in bytes
     * @return the size in bytes
     */
    public long getSize() {
        File top = new File(indexDir);
        if(top.exists()) {
            return getSize(top);
        }
        return 0;
    }

    protected long getSize(File f) {
        if(f.isFile()) {
            return f.length();
        } else if(f.isDirectory()) {
            long total = 0;
            File[] children = f.listFiles();
            for(File c : children) {
                total += getSize(c);
            }
            return total;
        }
        return 0;
    }

    /**
     * A timer task for flushing the engine periodically.
     */
    class FlushTimerTask extends TimerTask {

        @Override
        public void run() {
            try {
                long curr = System.currentTimeMillis();
                engine.flush();
            } catch(SearchEngineException ex) {
                logger.log(Level.SEVERE, "Error flushing engine data", ex);
            }
        }
    }
    /**
     * The resource to load for the engine configuration.  This gives us the
     * opportunity to use different configs as necessary (e.g., for testing).
     * Note that any resource named by this property must be accessible via
     * <code>Class.getResource()</code>!
     */
    @ConfigString(defaultValue = "itemSearchEngineConfig.xml")
    public static final String PROP_ENGINE_CONFIG_FILE = "engineConfigFile";

    @ConfigBoolean(defaultValue = false)
    public static final String PROP_REGENERATE_TERM_STATS =
            "regenerateTermStats";

    /**
     * Whether we should copy the index data when we are started.
     */
    @ConfigBoolean(defaultValue = false)
    public static final String PROP_COPY_DIR = "copyDir";

    /**
     * The location to which index data should be copied, if we're copying it.
     */
    @ConfigString(defaultValue = "")
    public static final String PROP_COPY_LOCATION = "copyLocation";

    /**
     * The prefix for the replicant containing this index.
     */
    @ConfigString(mandatory = false)
    public static final String PROP_PREFIX = "prefix";

    /**
     * Whether we should delete our index directory at startup.
     * Use with caution, eh?
     */
    @ConfigBoolean(defaultValue = false)
    public static final String PROP_DELETE_INDEX = "deleteIndex";

    /**
     * The configurable index directory.
     */
    @ConfigString(defaultValue = "itemData.idx")
    public static final String PROP_INDEX_DIR = "indexDir";

    /**
     * The interval (in milliseconds) between index flushes.
     */
    @ConfigInteger(defaultValue = 3000, range = {1, 3000000})
    public static final String PROP_FLUSH_INTERVAL = "flushInterval";

}
