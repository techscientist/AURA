package com.sun.labs.aura.datastore.impl.store;

import com.sun.labs.aura.datastore.DBIterator;
import com.sleepycat.je.DatabaseException;
import com.sun.labs.aura.AuraService;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.datastore.Attention;
import com.sun.labs.aura.datastore.Attention.Type;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.Item.ItemType;
import com.sun.labs.aura.datastore.ItemEvent;
import com.sun.labs.aura.datastore.ItemListener;
import com.sun.labs.aura.datastore.User;
import com.sun.labs.aura.datastore.impl.DSBitSet;
import com.sun.labs.aura.datastore.impl.PartitionCluster;
import com.sun.labs.aura.datastore.impl.Replicant;
import com.sun.labs.aura.datastore.impl.store.persist.FieldDescription;
import com.sun.labs.aura.datastore.impl.store.persist.PersistentAttention;
import com.sun.labs.aura.datastore.impl.store.persist.ItemImpl;
import com.sun.labs.aura.util.Scored;
import com.sun.labs.minion.DocumentVector;
import com.sun.labs.minion.FieldFrequency;
import com.sun.labs.minion.IndexListener;
import com.sun.labs.minion.ResultsFilter;
import com.sun.labs.minion.SearchEngine;
import com.sun.labs.minion.WeightedField;
import com.sun.labs.minion.util.NanoWatch;
import com.sun.labs.util.props.ConfigBoolean;
import com.sun.labs.util.props.ConfigComponent;
import com.sun.labs.util.props.ConfigString;
import com.sun.labs.util.props.Configurable;
import com.sun.labs.util.props.ConfigurationManager;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import java.io.File;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of the item store using the berkeley database as a back
 * end.
 */
public class BerkeleyItemStore implements Replicant, Configurable, AuraService,
        IndexListener {

    /**
     * The location of the BDB/JE Database Environment
     */
    @ConfigString(defaultValue = "/tmp/aura")
    public final static String PROP_DB_ENV = "dbEnv";

    protected String dbEnvDir;

    /**
     * The prefix that we're storing data for.
     */
    @ConfigString
    public static final String PROP_PREFIX = "prefix";

    private DSBitSet prefixCode;

    @ConfigBoolean(defaultValue = false)
    public final static String PROP_OVERWRITE = "overwrite";

    protected boolean overwriteExisting;

    /**
     * The search engine that will store item info
     */
    @ConfigComponent(type = ItemSearchEngine.class)
    public static final String PROP_SEARCH_ENGINE =
            "itemSearchEngine";

    protected ItemSearchEngine searchEngine;

    /**
     * Our partition cluster.
     */
    @ConfigComponent(type =
    com.sun.labs.aura.datastore.impl.PartitionCluster.class)
    public static final String PROP_PARTITION_CLUSTER = "partitionCluster";

    private PartitionCluster partitionCluster;

    /**
     * ComponentRegistry will be non-null if we're running in a RMI environment
     */
    protected ConfigurationManager cm = null;

    /**
     * The wrapper around all the BDB/JE implementation
     */
    protected BerkeleyDataWrapper bdb;

    /**
     * A map to store listeners for each type of item
     */
    protected Map<ItemType, Set<ItemListener>> listenerMap;
    
    /**
     * A queue of change events that need to be sent
     */
    private ConcurrentLinkedQueue<ChangeEvent> changeEvents;

    /**
     * A queue of create events that need to be sent
     */
    private ConcurrentLinkedQueue<ItemImpl> createEvents;

    /**
     * Indicates if the item store has been closed.  Once the store is
     * closed, no more operators are permitted.
     */
    protected boolean closed = false;

    /**
     * A logger for messages/debug info
     */
    protected Logger logger;

    /**
     * Constructs an empty item store, ready to be configured.
     */
    public BerkeleyItemStore() {
        listenerMap = new HashMap<ItemType, Set<ItemListener>>();
        changeEvents = new ConcurrentLinkedQueue<ChangeEvent>();
        createEvents = new ConcurrentLinkedQueue<ItemImpl>();
    }

    /**
     * Sets the properties for the item store, opening the database
     * environment and the entity store.  This must be called immediately
     * after the object is instantiated.
     * 
     * @param ps the set of properties
     * @throws com.sun.labs.util.props.PropertyException
     */
    public void newProperties(PropertySheet ps) throws PropertyException {
        logger = ps.getLogger();

        prefixCode = DSBitSet.parse(ps.getString(PROP_PREFIX));

        //
        // Get the database environment
        dbEnvDir = ps.getString(PROP_DB_ENV);
        File f = new File(dbEnvDir);
        f.mkdirs();

        //
        // See if we should overwrite any existing database at that path
        overwriteExisting = ps.getBoolean(PROP_OVERWRITE);

        //
        // Configure and open the environment and entity store
        try {
            bdb = new BerkeleyDataWrapper(dbEnvDir, logger, overwriteExisting);
        } catch(DatabaseException e) {
            logger.severe("Failed to load the database environment at " +
                    dbEnvDir + ": " + e);
        }

        //
        // Get the search engine from the config system
        searchEngine = (ItemSearchEngine) ps.getComponent(PROP_SEARCH_ENGINE);
        searchEngine.getSearchEngine().addIndexListener(this);

        //
        // Get the configuration manager, which we'll use to export things, if
        // necessary.
        cm = ps.getConfigurationManager();

        //
        // Fetch the partition cluster with this prefix.
        partitionCluster =
                (PartitionCluster) ps.getComponent(PROP_PARTITION_CLUSTER);
        Replicant exported = (Replicant) cm.getRemote(this, partitionCluster);
        try {
            partitionCluster.addReplicant(exported);
        } catch(RemoteException rx) {
            throw new PropertyException(ps.getInstanceName(),
                    PROP_PARTITION_CLUSTER, "Unable to add " +
                    "replicant to partition cluster.");
        }
    }

    public DSBitSet getPrefix() {
        return prefixCode;
    }

    /**
     * Close up the entity store and the database environment.
     */
    public void close() throws AuraException {
        closed = true;
        System.out.println(new Date() + ": Closing BDB...");
        bdb.close();
        System.out.println(new Date() + ": Shutting down search engine...");
        searchEngine.getSearchEngine().removeIndexListener(this);
        searchEngine.shutdown();
        System.out.println(new Date() + ": Done closing search engine");
    }

    public void defineField(ItemType itemType, String field)
            throws AuraException, RemoteException {
        defineField(itemType, field, null, null);
    }

    public void defineField(ItemType itemType, String field, EnumSet<Item.FieldCapability> caps, 
            Item.FieldType fieldType) throws AuraException, RemoteException {
        bdb.defineField(itemType, field, caps, fieldType);
        
        //
        // If this field is going to be dealt with by the search engine, then
        // send it there.
        if(caps != null && caps.size() > 0) {
            searchEngine.defineField(itemType, field, caps, fieldType);
        }
        
    }
    
    /**
     * Get all the instances of a particular type of item from the store
     * 
     * @param itemType the type of item to fetch
     * @return all of those items
     */
    public List<Item> getAll(ItemType itemType) throws AuraException {
        return bdb.getAll(itemType);
    }

    public Item getItem(String key) throws AuraException {
        return bdb.getItem(key);
    }

    public User getUser(String key) throws AuraException {
        return (User) bdb.getItem(key);
    }

    public User getUserForRandomString(String randStr) throws AuraException {
        return bdb.getUserForRandomString(randStr);
    }
    
    public Item putItem(Item item) throws AuraException {
        boolean existed = false;
        if(item instanceof ItemImpl) {
            ItemImpl itemImpl = (ItemImpl) item;
            
            //
            // Walk the fields, make sure they're defined, and figure out whether
            // we need to re-index this item.
            boolean mustIndex = false;
            Set<String> setFields = itemImpl.getSetFields();
            for(Map.Entry<String,Serializable> e : itemImpl) {
                FieldDescription fd;
                try {
                    fd = bdb.fieldByName.get(e.getKey());
                } catch(DatabaseException ex) {
                    throw new AuraException("Error getting field description ", ex);
                }
                if(fd == null) {
                    throw new AuraException("Item " + item.getKey() + 
                            " contains unknown field " + e.getKey());
                }
                if(fd.mustIndex() && setFields.contains(e.getKey())) {
                    mustIndex = true;
                }
            }
            
            
            //
            // If this was a remote object, its transient map will be null
            // and storeMap will be a no-op.  If it was a local object then
            // storeMap will serialize the map (if there is one).
            itemImpl.storeMap();
            ItemImpl prev = bdb.putItem(itemImpl);
            if(prev != null) {
                existed = true;
            }

            if(mustIndex || itemImpl.isNew()) {
                //
                // The item was modified in a way that requires indexing.
                searchEngine.index(itemImpl);
            }

            //
            // Finally, send out relevant events.
            if(existed) {
                itemChanged(itemImpl, ItemEvent.ChangeType.AURA);
            } else {
                itemCreated(itemImpl);
            }
            return itemImpl;
        } else {
            throw new AuraException("Unsupported Item type");
        }

    }

    public User putUser(User user) throws AuraException {
        return (User) putItem(user);
    }

    /**
     * Deletes just a user from the item store, not touching the attention.
     */
    public void deleteUser(String userKey) throws AuraException {
        deleteItem(userKey);
    }
    
    /**
     * Deletes just an item from the item store, not touching the attention.
     */
    public void deleteItem(String itemKey) throws AuraException {
        bdb.deleteItem(itemKey);
    }

    public List<Item> getItems(User user, Type attnType,
            ItemType itemType)
            throws AuraException {
        return bdb.getItems(user.getKey(), attnType, itemType);
    }

    public DBIterator<Item> getItemsAddedSince(ItemType type,
            Date timeStamp)
            throws AuraException {
        DBIterator<Item> res =
                bdb.getItemsAddedSince(type, timeStamp.getTime());
        res = (DBIterator<Item>)cm.getRemote(res);
        return res;
    }

    public List<Attention> getAttentionForSource(String srcKey)
            throws AuraException {
        return bdb.getAttentionForSource(srcKey);
    }

    public List<Attention> getAttentionForSource(String srcKey,
                                                Attention.Type type)
            throws AuraException {
        return bdb.getAttentionForSource(srcKey, type);
    }

    public List<Attention> getAttentionForTarget(String itemKey)
            throws AuraException {
        return bdb.getAttentionForTarget(itemKey);
    }

    public Attention attend(Attention att) throws AuraException {
        //
        // Create the attention and add references to it from the item and
        // the user. (bdb wrapper does this)
        PersistentAttention pa = new PersistentAttention(att);
        bdb.putAttention(pa);
        return pa;
    }
    
    public void removeAttention(String srcKey, String targetKey,
                                Attention.Type type)
            throws AuraException {
        bdb.removeAttention(srcKey, targetKey, type);
    }

    public void deleteAttention(String itemKey)
            throws AuraException, RemoteException {
        bdb.deleteAttention(itemKey);
    }

    public DBIterator<Attention> getAttentionSince(Date timeStamp)
            throws AuraException {
        DBIterator<Attention> res =
                bdb.getAttentionAddedSince(timeStamp.getTime());

        return (DBIterator<Attention>) cm.getRemote(res);
    }

    public DBIterator<Attention> getAttentionForSourceSince(String key,
            Date timeStamp) throws AuraException {
        DBIterator<Attention> res =
                bdb.getAttentionForSourceSince(key, timeStamp.getTime());
        return (DBIterator<Attention>) cm.getRemote(res);
    }
    
    public DBIterator<Attention> getAttentionForTargetSince(String key,
            Date timeStamp) throws AuraException {
        DBIterator<Attention> res =
                bdb.getAttentionForTargetSince(key, timeStamp.getTime());
        return (DBIterator<Attention>) cm.getRemote(res);
    }

    public List<Attention> getLastAttentionForSource(String srcKey,
            int count)
            throws AuraException, RemoteException {
        return getLastAttentionForSource(srcKey, null, count);
    }

    public List<Attention> getLastAttentionForSource(String srcKey,
            Type type,
            int count)
            throws AuraException, RemoteException {
        return bdb.getLastAttentionForUser(srcKey, type, count);
    }

    public List<Scored<Item>> query(String query, int n, ResultsFilter rf)
            throws AuraException, RemoteException {
        return query(query, "-score", n, rf);
    }

    public List<Scored<Item>> query(String query, String sort, int n, ResultsFilter rf)
            throws AuraException, RemoteException {
        NanoWatch sw = new NanoWatch();
        sw.start();
        List<Scored<Item>> res =
                keysToItems(searchEngine.query(query, sort, n, rf));
        sw.stop();
        logger.info("q " + query + " " + sw.getTimeMillis());
        return res;
    }

    public List<Scored<Item>> getAutotagged(String autotag, int n)
            throws AuraException, RemoteException {
        NanoWatch sw = new NanoWatch();
        sw.start();
        List<Scored<Item>> res =
                keysToItems(searchEngine.getAutotagged(autotag, n));
        sw.stop();
        logger.info("gat " + autotag + " " + sw.getTimeMillis());
        return res;
    }
    
    public List<Scored<String>> getTopAutotagTerms(String autotag, int n)
            throws AuraException, RemoteException {
        return searchEngine.getTopFeatures(autotag, n);
    }
    
    public List<Scored<String>> findSimilarAutotags(String autotag, int n)
            throws AuraException, RemoteException {
        return searchEngine.findSimilarAutotags(autotag, n);
    }
 
    public List<Scored<String>> explainSimilarAutotags(String a1, String a2,
            int n)
            throws AuraException, RemoteException {
        return searchEngine.explainSimilarAutotags(a1, a2, n);
    }
    
    public DocumentVector getDocumentVector(String key) {
        return searchEngine.getDocumentVector(key);
    }
    
    public DocumentVector getDocumentVector(String key, String field) {
        return searchEngine.getDocumentVector(key, field);
    }
    
    public DocumentVector getDocumentVector(String key, WeightedField[] fields) {
        return searchEngine.getDocumentVector(key, fields);
    }
    
    public List<FieldFrequency> getTopValues(String field, int n,
            boolean ignoreCase) throws AuraException, RemoteException {
        return searchEngine.getSearchEngine().getTopFieldValues(field, n, ignoreCase);
    }
    
    /**
     * Finds a the n most similar items to the given item.
     * @param key the item that we want to find similar items for
     * @param n the number of similar items to return
     * @return the set of items most similar to the given item, ordered by 
     * similarity to the given item.  The similarity of the items is based on 
     * all of the indexed text associated with the item in the data store.
     */
    public List<Scored<Item>> findSimilar(DocumentVector dv, int n, ResultsFilter rf)
            throws AuraException, RemoteException {
        NanoWatch sw = new NanoWatch();
        sw.start();
        List<Scored<String>> fsr = searchEngine.findSimilar(dv, n, rf);
        sw.stop();
        logger.info("fs " + dv.getKey() + " " + sw.getTimeMillis());
        List<Scored<Item>> res = keysToItems(fsr);
        return res;
    }

    public List<Scored<String>> getTopTerms(String key, String field, int n)
            throws AuraException, RemoteException {
        return searchEngine.getTopTerms(key, field, n);
    }

    public List<Scored<String>> getExplanation(String key, String autoTag,
            int n)
            throws AuraException, RemoteException {
        return searchEngine.getExplanation(key, autoTag, n);
    }

    /**
     * Transforms a list of scored keys (as from the search engine) to a list of scored
     * items. 
     * @param s the list of keys to transform
     * @return a set of items, organized by score
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    private List<Scored<Item>> keysToItems(List<Scored<String>> s)
            throws AuraException, RemoteException {
        List<Scored<Item>> ret = new ArrayList<Scored<Item>>();
        for(Scored<String> ss : s) {
            Item item = getItem(ss.getItem());
            if(item == null) {
                logger.info("null item for key: " + ss.getItem());
                continue;
            }
            ret.add(new Scored<Item>(item, ss));
        }
        return ret;
    }

    public void addItemListener(ItemType itemType, ItemListener listener)
            throws AuraException {
        //
        // Find the set of listeners for this type and add it, adding a set to
        // track these listeners if there isn't one.
        synchronized(listenerMap) {
            Set<ItemListener> l = listenerMap.get(itemType);
            if(l == null) {
                l = new HashSet<ItemListener>();
                listenerMap.put(itemType, l);
            }
            l.add(listener);
        }
    }

    public void removeItemListener(ItemType itemType, ItemListener listener)
            throws AuraException {
        //
        // If we were givn a null item type, remove from all types
        synchronized(listenerMap) {
            if(itemType == null) {
                for(ItemType t : listenerMap.keySet()) {
                    Set<ItemListener> l = listenerMap.get(t);
                    l.remove(listener);
                }
            } else {
                Set<ItemListener> l = listenerMap.get(itemType);
                l.remove(listener);
            }
        }
    }

    public long getItemCount(ItemType type) {
        return bdb.getItemCount(type);
    }

    public long getAttentionCount() {
        return bdb.getAttentionCount();
    }

    /**
     * Internal method to handle sending/queueing item changed events.
     */
    private void itemChanged(ItemImpl item, ItemEvent.ChangeType ctype) {
        //
        // Queue the event for later delivery
        changeEvents.offer(new ChangeEvent(item, ctype));
    }

    /**
     * Adds an item to a itemType->changeType->item map of maps.
     */
    private void addItem(ChangeEvent ce,
            Map<ItemType, Map<ItemEvent.ChangeType, List<ItemImpl>>> eventsByType,
            ItemType type) {

        //
        // Our per-item type map.
        Map<ItemEvent.ChangeType, List<ItemImpl>> eventMap =
                eventsByType.get(type);
        if(eventMap == null) {
            eventMap = new HashMap<ItemEvent.ChangeType, List<ItemImpl>>();
            eventsByType.put(type, eventMap);
        }

        //
        // Our per-change type map and list of items.
        List<ItemImpl> l = eventMap.get(ce.type);
        if(l == null) {
            l = new ArrayList<ItemImpl>();
            eventMap.put(ce.type, l);
        }
        l.add(ce.item);
    }

    /**
     * Sends any queued up change events when the changed data has been flushed.
     * 
     * @param keys the set of keys that are in a partition that has just been
     * dumped to disk.  These are really strings.
     */
    private synchronized void sendChangedEvents(Set<Object> keys) {

        //
        // OK, this is a bit tricky:  we want to send events by item type and
        // then by change type, so we need a map to a map.  We also want to send
        // events of all types, so we'll have a separate map from change type
        // to item for that.
        Map<ItemType, Map<ItemEvent.ChangeType, List<ItemImpl>>> eventsByType =
                new HashMap<ItemType, Map<ItemEvent.ChangeType, List<ItemImpl>>>();

        //
        // Process our stored change events against the keys we were given.
        // We'll try to process however many elements are in the queue at 
        // this point.
        int n = changeEvents.size();
        for(int i = 0; i < n; i++) {
            ChangeEvent ce = changeEvents.poll();

            //
            // We probably shouldn't get null, but just in case.
            if(ce == null) {
                break;
            }

            //
            // If this item is in our set, then process it.
            if(keys.contains(ce.item.getKey())) {

                //
                // Add the autotags.
                List<Scored<String>> autotags =
                        searchEngine.getAutoTags(ce.item.getKey());
                if(autotags != null) {
                    ce.item.setField("autotag", (Serializable) autotags);
                    try {
                        ce.item.storeMap();
                        bdb.putItem(ce.item);
                    } catch(AuraException ae) {
                        logger.log(Level.SEVERE, "Error adding autotags to " +
                                ce.item.getKey(), ae);
                    }
                }
                //
                // Add this item to the all events type map and the per-events
                // type map.
                addItem(ce, eventsByType, null);
                addItem(ce, eventsByType, ce.item.getType());
            } else {
                //
                // Put it back on the queue for the next guy.
                changeEvents.offer(ce);
            }
        }

        //
        // For each type for which there is at least one listener:
        for(ItemType itemType : listenerMap.keySet()) {

            Map<ItemEvent.ChangeType, List<ItemImpl>> te =
                    eventsByType.get(itemType);
            if(te == null) {
                continue;
            }

            //
            // Send the events of each change type to each of the listeners.
            for(Map.Entry<ItemEvent.ChangeType, List<ItemImpl>> e : te.entrySet()) {
                for(ItemListener il : listenerMap.get(itemType)) {
                    try {
                        il.itemChanged(new ItemEvent(e.getValue().
                                toArray(new ItemImpl[0]), e.getKey()));
                    } catch(RemoteException ex) {
                        logger.log(Level.SEVERE, "Error sending change events",
                                ex);
                    }
                }
            }
        }
    }

    /**
     * Internal method to handle sending/queueing item created events.
     */
    private void itemCreated(ItemImpl item) {
        //
        // Queue up this item to be sent out
        createEvents.offer(item);
    }

    private void addItem(ItemImpl item, Map<ItemType, List<ItemImpl>> m,
            ItemType type) {
        List<ItemImpl> l = m.get(type);
        if(l == null) {
            l = new ArrayList<ItemImpl>();
            m.put(type, l);
        }
        l.add(item);
    }

    /**
     * Sends any queued up create events
     */
    private synchronized void sendCreatedEvents(Set<Object> keys) {

        Map<ItemType, List<ItemImpl>> newItems =
                new HashMap<ItemType, List<ItemImpl>>();

        //
        // Process the new items we've accumulated, sending events for those
        // that are in our set of keys.  We'll process however much stuff is 
        // on the queue when we get here.
        int n = createEvents.size();
        for(int i = 0; i < n; i++) {
            ItemImpl ie = createEvents.poll();
            if(ie == null) {
                break;
            }
            if(keys.contains(ie.getKey())) {
                //
                // Add the autotags.
                List<Scored<String>> autotags = searchEngine.getAutoTags(ie.getKey());
                if(autotags != null) {
                    ie.setField("autotag", (Serializable) autotags);
                    ie.storeMap();
                    try {
                        bdb.putItem(ie);
                    } catch(AuraException ae) {
                        logger.log(Level.SEVERE, "Error adding autotags to " +
                                ie.getKey(), ae);
                    }
                }
                addItem(ie, newItems, null);
                addItem(ie, newItems, ie.getType());
            } else {
                createEvents.offer(ie);
            }
        }

        //
        // For each type of item for which there is a listener, batch up
        // the items of that type and send them off together.
        for(ItemType itemType : listenerMap.keySet()) {

            List<ItemImpl> l = newItems.get(itemType);
            if(l == null) {
                continue;
            }

            for(Iterator it = listenerMap.get(itemType).iterator(); it.hasNext(); ) {
                ItemListener il = (ItemListener)it.next();
                try {
                    il.itemCreated(new ItemEvent(l.toArray(new ItemImpl[0])));
                } catch(RemoteException ex) {
                    logger.log(Level.SEVERE,"Error sending new item events " +
                            "from BIS. Removing listener.\n(" +
                            ex.getMessage() + ")");
                    it.remove();
                }
            }
        }
    }

    public void start() {
    }

    public void stop() {
        try {
            cm.shutdown();
            System.out.println(new Date() + ": Closing Berkeley Item Store...");
            close();
            System.out.println(new Date() + ": Stopped");
        } catch(AuraException ae) {
            System.out.println("Error closing item store" + ae);
            ae.printStackTrace();
        }
    }

    public void partitionAdded(SearchEngine e, Set<Object> keys) {
        sendCreatedEvents(keys);
        sendChangedEvents(keys);
    }
}