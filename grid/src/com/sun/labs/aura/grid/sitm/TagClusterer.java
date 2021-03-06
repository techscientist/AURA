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

package com.sun.labs.aura.grid.sitm;

import com.sun.labs.aura.AuraService;
import com.sun.labs.aura.datastore.DBIterator;
import com.sun.labs.aura.datastore.DataStore;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.SimilarityConfig;
import com.sun.labs.aura.music.ArtistTagRaw;
import com.sun.labs.aura.music.TaggableItem;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.RemoteComponentManager;
import com.sun.labs.aura.util.Scored;
import com.sun.labs.minion.FieldFrequency;
import com.sun.labs.util.props.ConfigComponent;
import com.sun.labs.util.props.ConfigString;
import com.sun.labs.util.props.Configurable;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mailletf
 */
public class TagClusterer implements AuraService, Configurable {

    private Logger logger;
    private RemoteComponentManager rcmStore;
    private ExecutorService threadPool = Executors.newCachedThreadPool();
    private final Queue<String> tags = new LinkedList<String>();
    private final Map<String, List<Scored<String>> > sim = new HashMap<String, List<Scored<String>> >();

    private final int NBR_TO_WRITE = 25000;
    private int outputFileCnt = 0;

    private int totalNbrTags = -1;
    private boolean running = false;

    private int nbrThreads = 8;
    private int nbrSimTags = 1500;


    @Override
    public void start() {
        if (!running) {
            running = true;
            try {

                    //runFindSimilar();
                    runGetTagCounts();


            } catch (RemoteException ex) {
                Logger.getLogger(TagClusterer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                    Logger.getLogger(TagClusterer.class.getName()).log(Level.SEVERE, null, ex);
            } catch (AuraException ex) {
                Logger.getLogger(TagClusterer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public void newProperties(PropertySheet ps) throws PropertyException {
        logger = ps.getLogger();
        fsPath = ps.getString(PROP_FS_PATH);
        rcmStore = new RemoteComponentManager(ps.getConfigurationManager(), DataStore.class);

    }

    private DataStore getDataStore() throws AuraException {
        return (DataStore) rcmStore.getComponent();
    }


    private void runGetTagCounts() throws AuraException, RemoteException, FileNotFoundException, IOException {

        FileOutputStream fos = new FileOutputStream(fsPath + "/rawtags_count.txt");
        OutputStreamWriter out = new OutputStreamWriter(fos);

        int i=0;
        for (FieldFrequency fF : getDataStore().getTopValues(TaggableItem.FIELD_SOCIAL_TAGS_RAW, Integer.MAX_VALUE, false)) {
            out.write((String)fF.getVal() + "\t" + fF.getFreq());
            if (i++ % 1000 == 0) {
                logger.info("  Done "+i);
            }
        }

        out.close();

    }


    private void runDeleteAllRawTags() throws AuraException, RemoteException {

        int i = 0;
        long total = getDataStore().getItemCount(Item.ItemType.ARTIST_TAG_RAW);
        DBIterator<Item> it = getDataStore().getAllIterator(Item.ItemType.ARTIST_TAG_RAW);
        try {
            while (it.hasNext()) {
                ArtistTagRaw atr = new ArtistTagRaw(it.next());
                getDataStore().deleteItem(atr.getKey());
                if (i++ % 100 == 0) {
                    logger.info(i + "/" + total);
                }
            }
        } finally {
            it.close();
        }
    }
    

    private void runFindSimilar() throws AuraException, RemoteException {
        if (running) {
            long startTime = System.currentTimeMillis();
            logger.info("Populating list of tags...");

            for (FieldFrequency fF : getDataStore().getTopValues(TaggableItem.FIELD_SOCIAL_TAGS_RAW, Integer.MAX_VALUE, false)) {
                tags.add(ArtistTagRaw.nameToKey((String) fF.getVal()));
            }
            writeToFile("/alltags.objdump", tags);
            totalNbrTags = tags.size();
            
            logger.info("Populating list of tags done. Got " + totalNbrTags +
                    " tags. Took " + (System.currentTimeMillis() - startTime) + "ms");

            logger.info("Starting "+nbrThreads+" find similar threads, fetching "+nbrSimTags+" similar items per item");
            ArrayList<SimilarityComputer> computerList = new ArrayList<SimilarityComputer>();
            for (int i=0; i<nbrThreads; i++) {
                SimilarityComputer sC = new SimilarityComputer();
                threadPool.submit(sC);
                computerList.add(sC);
            }

            boolean isDone = false;
            while (!isDone) {
                isDone = true;
                for (SimilarityComputer sC : computerList) {
                    if (!sC.isDone) {
                        isDone = false;
                        break;
                    }
                }

                synchronized (sim) {
                    logger.info("   progress...\t" + (outputFileCnt*NBR_TO_WRITE + sim.size()) + "/" + totalNbrTags);
                }
                try {
                    Thread.sleep(30 * 1000L);
                } catch (InterruptedException ex) {
                }

            }

            logger.info("Computation done in " + (System.currentTimeMillis() - startTime) +
                    "ms. Writing out to file...");
            writeMapSplit();

            running = false;

            // this could probably be done in a more graceful way
            logger.info("TagClusterer exiting...");
            System.exit(0);
        }
    }

    private void writeMapSplit() {
        outputFileCnt++;
        Set<String> tagsPresent = sim.keySet();
        writeToFile("/keys-" + outputFileCnt + ".objdump", tagsPresent);
        writeToFile("/tagsim-" + outputFileCnt + ".objdump", sim);
        logger.info(">> Wrote map split #"+outputFileCnt);
        sim.clear();
    }


    private void writeToFile(String fileName, Object obj) {
        try {
            File theFile = new File(fsPath + fileName);
            FileOutputStream outStream = new FileOutputStream(theFile);
            ObjectOutputStream objStream = new ObjectOutputStream(outStream);
            objStream.writeObject(obj);
            objStream.close();
            logger.info("Writing done");
        } catch (IOException ex) {
            Logger.getLogger(TagClusterer.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public static Set<String> readKeySplit(String prefix, int splitNbr) throws IOException {
        return readKeySplit(prefix + "/keys-"+splitNbr+".objdump");
    }

    public static Set<String> readKeySplit(String path) throws IOException {
        File theFile = new File(path);
        FileInputStream inStream = new FileInputStream(theFile);
        ObjectInputStream objStream = new ObjectInputStream(inStream);

        Set<String> s = null;
        try {
            s = (Set<String>) objStream.readObject();
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(TagClusterer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return s;
    }

    public static LinkedList<String> readAllTags(String path) throws IOException, ClassNotFoundException {
        File theFile = new File(path);
        FileInputStream inStream = new FileInputStream(theFile);
        ObjectInputStream objStream = new ObjectInputStream(inStream);
        return (LinkedList<String>) objStream.readObject();
    }

    public static Map<String, List<Scored<String>> > readMapSplit(String prefix, int splitNbr) throws IOException {
        return readMapSplit(prefix + "/tagsim-"+splitNbr+".objdump");
    }

    public static Map<String, List<Scored<String>> > readMapSplit(String path) throws IOException {

        File theFile = new File(path);
        FileInputStream inStream = new FileInputStream(theFile);
        ObjectInputStream objStream = new ObjectInputStream(inStream);

        Map<String, List<Scored<String>>> s = null;
        try {
            s = (Map<String, List<Scored<String>>>) objStream.readObject();
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(TagClusterer.class.getName()).log(Level.SEVERE, null, ex);
        }
        return s;

    }



    public class SimilarityComputer implements Runnable {

        private SimilarityConfig sC = new SimilarityConfig(ArtistTagRaw.FIELD_TAGGED_ARTISTS, nbrSimTags, null);
        public boolean isDone = false;

        @Override
        public void run() {

            String currentTag;

            while (running) {
                synchronized (tags) {
                    if (tags.isEmpty()) {
                        break;
                    } else {
                        currentTag = tags.poll();
                    }
                }

                try {
                    List<Scored<Item>> lsi = getDataStore().findSimilar(currentTag, sC);

                    ArrayList<Scored<String>> sS = new ArrayList<Scored<String>>();
                    for (Scored<Item> sI : lsi) {
                        sS.add(new Scored<String>(sI.getItem().getKey(), sI.getScore()));
                    }
                    synchronized (sim) {
                        sim.put(currentTag, sS);

                        if (sim.size() >= NBR_TO_WRITE) {
                            writeMapSplit();
                        }
                    }

                } catch (AuraException ex) {
                    logger.log(Level.SEVERE, "AuraException for tag "+currentTag, ex);
                    ex.printStackTrace();
                } catch (RemoteException ex) {
                    logger.log(Level.SEVERE, "RemoteException for tag "+currentTag, ex);
                }

            }
            isDone = true;
        }
    }




    public static void main(String[] args) throws FileNotFoundException, IOException {

        // Load data
        Map<String, List<Scored<String>>> s = readMapSplit(args[0]);

        System.out.println("Loaded map with " + s.size() + " elements");
        int i = 0;
        for (String key : s.keySet()) {
            if (s.get(key).size() > 0) {
                System.out.println(key + ":" + s.get(key).size());
                if (i++ > 150) {
                    break;
                }
            }

        }

    }


    @ConfigComponent(type = DataStore.class)
    public final static String PROP_DATA_STORE = "dataStore";

    @ConfigString()
    public final static String PROP_FS_PATH = "fsPath";
    private String fsPath;
}
