/*
 * Copyright 2008-2009 Sun Microsystems, Inc. All Rights Reserved.
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

package com.sun.labs.aura.music.crawler;

import com.sun.labs.aura.AuraService;
import com.sun.labs.aura.datastore.Attention;
import com.sun.labs.aura.datastore.AttentionConfig;
import com.sun.labs.aura.datastore.DBIterator;
import com.sun.labs.aura.datastore.DataStore;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.Item.ItemType;
import com.sun.labs.aura.music.Artist;
import com.sun.labs.aura.music.Listener;
import com.sun.labs.aura.music.MusicDatabase;
import com.sun.labs.aura.music.ScoredManager;
import com.sun.labs.aura.music.web.lastfm.LastFM;
import com.sun.labs.aura.music.web.lastfm.LastFM2;
import com.sun.labs.aura.music.web.lastfm.LastItem;
import com.sun.labs.aura.music.web.lastfm.LastUser;
import com.sun.labs.aura.music.web.pandora.Pandora;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.RemoteComponentManager;
import com.sun.labs.aura.util.Scored;
import com.sun.labs.aura.util.Tag;
import com.sun.labs.util.props.ConfigBoolean;
import com.sun.labs.util.props.ConfigComponent;
import com.sun.labs.util.props.ConfigInteger;
import com.sun.labs.util.props.Configurable;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import java.io.IOException;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.python.core.PyDictionary;
import org.python.core.PyList;
import org.python.core.PyTuple;

/**
 *
 * @author plamere
 */
public class ListenerCrawler extends QueueCrawler implements AuraService, Configurable {

    private final int QUEUE_SIZE = 500;
    private RemoteComponentManager rcmArtist;
    private RemoteComponentManager rcmCrawl;

    private Thread discoveryThread;

    /**
     * Minimum play count to be added in the discovery queue
     */
    private final int MIN_PLAY_COUNT = 1250;

    private Pandora pandora;
    private MusicDatabase mdb;
    
    private boolean running = false;
    private boolean needMorenewListeners = false;

    private Random r = new Random();

    public ListenerCrawler() {
        super("Listener", "listener_crawler.state");
        crawlQueue = new PriorityBlockingQueue<QueuedItem>(QUEUE_SIZE, QueueCrawler.PRIORITY_ORDER);
    }

    private synchronized boolean isNeedMoreNewListeners() {
        return needMorenewListeners;
    }

    private synchronized void setNeedMoreNewListeners(boolean newVal) {
        needMorenewListeners = newVal;
    }

    @Override
    public synchronized void start() {
        if (!running) {
            running = true;
            {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        periodicallyCrawlAllListeners();
                    }
                };
                t.start();
            }
            {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        periodicallyCrawlNewListeners();
                    }
                };
                t.start();
            }
            {
                if (enableListenerDiscovery) {
                    discoveryThread = new Thread() {
                        @Override
                        public void run() {
                            try {
                                primeListenerQueue("rj");
                                sleep(30 * 1000L);
                            } catch (InterruptedException ex) {
                            }
                            discoverNewListeners();
                        }
                    };
                    discoveryThread.start();
                }
            }
            {
                Thread t = new Thread() {
                    @Override
                    public void run() {
                        periodicallyUpdateAggregatedCharts();
                    }
                };
                t.start();
            }
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
        logger.info("Saving listener crawler queue state because of shutdown");
        saveState();
    }

    @Override
    public void update(final String id) throws AuraException, RemoteException {
        Thread t = new Thread() {

            @Override
            public void run() {
                try {
                    Listener listener = mdb.getListener(id);
                    if (listener != null) {
                        crawlListener(listener, true);
                    }
                } catch (AuraException ex) {
                    logger.warning("could not crawl listener " + id + " " + ex.getMessage());
                    Logger.getLogger(ListenerCrawler.class.getName()).log(Level.SEVERE, null, ex);
                } catch (RemoteException ex) {
                    logger.warning("could not crawl listener " + id + " " + ex.getMessage());
                }
            }
        };
        t.start();
    }

    private LastFM getLastFM() throws AuraException, RemoteException {
        return (CrawlerController) rcmCrawl.getComponent();
    }

    private LastFM2 getLastFM2() throws AuraException, RemoteException {
        return (CrawlerController) rcmCrawl.getComponent();
    }

    @Override
    public void add(String newID) throws AuraException, RemoteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean enqueue(LastItem item, int priority) throws RemoteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void newProperties(PropertySheet ps) throws PropertyException {
        logger = ps.getLogger();
        defaultPeriod = ps.getInt(PROP_DEFAULT_PERIOD);
        stateDir = ps.getString(PROP_STATE_DIR);
        newCrawlPeriod = ps.getInt(PROP_NEW_CRAWL_PERIOD);
        maxListeners = ps.getInt(PROP_MAX_LISTENERS);
        enableListenerDiscovery = ps.getBoolean(PROP_ENABLE_LISTENER_DISCOVERY);
        nbrChartWeek = ps.getInt(PROP_NBR_CHARTS_WEEK);
        rcmArtist = new RemoteComponentManager(ps.getConfigurationManager(), QueueCrawlerInterface.class);
        rcmCrawl = new RemoteComponentManager(ps.getConfigurationManager(), CrawlerController.class);
        try {
            pandora = new Pandora();

            mdb = new MusicDatabase(ps.getConfigurationManager());
            createStateFileDirectory();
            loadState();
        } catch (AuraException ex) {
            throw new PropertyException(ex, ps.getInstanceName(),
                    "musicDatabase", "problems with the music database");
        } catch (IOException ex) {
            throw new PropertyException(ex, ps.getInstanceName(), "",
                    "problems connecting to last.fm/pandora");
        }
    }

    private boolean enqueueArtistToCrawl(LastItem lA, int popularity) throws AuraException, RemoteException {
        return ((QueueCrawlerInterface)rcmArtist.getComponent()).enqueue(lA, popularity);
    }

    private void periodicallyCrawlAllListeners() {
        FixedPeriod fp = new FixedPeriod(defaultPeriod * 1000L);
        logger.info(crawlerName+"Crawler: Crawling all listeners with a period of " + defaultPeriod + " secs");
        while (running) {
            try {
                fp.start();

                crawlAllListeners();

                fp.end();
            } catch (InterruptedException ex) {
            } catch (AuraException ex) {
                logger.warning("AuraException while crawling users" + ex);
                ex.printStackTrace();
            } catch (RemoteException ex) {
                logger.warning("Remote exception while crawling users" + ex);
                ex.printStackTrace();
            }
        }
    }

    private void periodicallyCrawlNewListeners() {
        long lastCrawl = 0;
        logger.info(crawlerName+"Crawler: Crawling new listeners with a period of " + newCrawlPeriod + " secs");
        FixedPeriod fp = new FixedPeriod(newCrawlPeriod * 1000L);
        while (running) {
            try {
                fp.start();

                lastCrawl = crawlNewListeners(lastCrawl);

                fp.end();
            } catch (InterruptedException ex) {
            } catch (AuraException ex) {
                logger.warning("AuraException while crawling users" + ex);
            } catch (RemoteException ex) {
                logger.warning("Remote exception while crawling users" + ex);
            }
        }
    }

    /**
     * The queue is primed with provided listener
     */
    private void primeListenerQueue(String listenerName) {
        try {
            if (listenerName == null) {
                listenerName = "rj";
            }
            Listener l = mdb.getListener(listenerName);
            if (l == null) {
                logger.info(crawlerName+"Crawler: Priming listener queue with " + listenerName);
                crawlQueue.add(new QueuedItem(listenerName));
                incrementModCounter();
            }
        } catch (AuraException ex) {
            Logger.getLogger(ListenerCrawler.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(ListenerCrawler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void discoverNewListeners() {

        logger.info(crawlerName+"Crawler: Discovery of new listeners enabled");
        int addedCnt = 0;

        while (running) {

            // If we other thread was asking for new listeners and we added enough
            // halt discovery for now
            if (isNeedMoreNewListeners() && addedCnt>=25) {
                logger.info(crawlerName+"Crawler:Discovery: Halting add more listeners since "+addedCnt+" were added.");
                setNeedMoreNewListeners(false);
                addedCnt = 0;
            }

            // If we don't need anymore new listeners added because the actual crawler
            // needs time to catch up, just wait
            if (!isNeedMoreNewListeners()) {
                try {
                    logger.fine(crawlerName + "Crawler:Discovery: No need for more listeners. Sleeping.");
                    Thread.sleep(newCrawlPeriod * 5 * 1000L);
                    continue;
                } catch (InterruptedException ex) {
                    // We could be woken up from sleep by artist crawler
                    // which needs more listeners
                    continue;
                }
            }
            
            if (crawlQueue.size() > 0) {
                try {
                    // if we've reached maxlisteners we are done
                    long nbrListeners = mdb.getDataStore().getItemCount(ItemType.USER);
                    if (nbrListeners >= maxListeners) {
                        logger.info(crawlerName+"Crawler:Discovery: reached max artists (" +
                                maxListeners + "), shutting down");
                        break;
                    }

                    String lastfmId = crawlQueue.poll().getKey();
                    incrementModCounter();
                    logger.info(crawlerName+"Crawler:Discovery: Adding listener '" +
                            lastfmId + "'. QueueSize:" + crawlQueue.size());
                    Listener l = mdb.getListener(lastfmId);
                    if (l != null) {
                        continue;
                    }

                    addedCnt++;
                    l = mdb.enrollListener(lastfmId);                    
                    l.setLastFmName(lastfmId);

                    // Add this listener's neighbours to the queue with a
                    // decreasing probablity as we have more listeners in the queue
                    if (r.nextDouble() > ((double)crawlQueue.size()/(QUEUE_SIZE-300))) {
                        // Only set the last neighbours crawl if we added the neighbours to
                        // the queue. This way, it'll get pickup up when refilling the queue
                        // from the store at a later time if we didn't add the neighbours
                        addNeighboursToQueueForUser(lastfmId);
                        l.setLastNeighboursCrawl();
                    }
                    mdb.updateListener(l);

                } catch (IOException ex) {
                    Logger.getLogger(ListenerCrawler.class.getName()).log(Level.SEVERE, null, ex);
                } catch (AuraException ex) {
                    Logger.getLogger(ListenerCrawler.class.getName()).log(Level.SEVERE, null, ex);
                }
            } else {
                try {
                    // try to find listeners in the store for which we didn't find neightbours
                    List<Listener> items = mdb.listenerGetNeverCrawledNeighbours(10);
                    if (items.size()==0) {
                        logger.info(crawlerName+"Crawler:Discovery: no more listeners " +
                                "to crawl. shutting down");
                        break;
                    } else {
                        logger.info(crawlerName+"Crawler:Discovery: filling queue " +
                                "with neighbours of " + items.size() + " listeners from store");
                        for (Listener l : items) {
                            addNeighboursToQueueForUser(l.getLastFmName());
                            l.setLastNeighboursCrawl();
                            mdb.updateListener(l);
                        }
                    }
                } catch (IOException ex) {
                    Logger.getLogger(ListenerCrawler.class.getName()).log(Level.SEVERE, null, ex);
                } catch (AuraException ex) {
                    Logger.getLogger(ListenerCrawler.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            Thread.yield();
        }
    }

    /**
     * For a given lastfmId, add its neighbours to the crawl queue
     * @param lastfmId Lastfm user id for which to crawl neighbours
     * @throws IOException
     */
    private void addNeighboursToQueueForUser(String lastfmId) throws IOException, AuraException {
        logger.info(crawlerName + "Crawler:Discovery: Crawling neighbours for " +
                lastfmId + ". QueueSize:" + crawlQueue.size());
        String[] neighbours = getLastFM2().getNeighboursForUser(lastfmId);
        for (String n : neighbours) {
            try {
                LastUser lU = getLastFM().getUser(n);
                if (lU.getPlayCount() > MIN_PLAY_COUNT) {
                    QueuedItem qI = new QueuedItem(n, lU.getPlayCount());
                    if (!crawlQueue.contains(qI)) {
                        crawlQueue.add(qI);
                        incrementModCounter();
                    }
                }
            } catch (Throwable t) {
                logger.warning("Problem ("+t+") adding neighbour '"+n+"' to crawler queue");
                t.printStackTrace();
            }
        }
    }
    

    private List<String> getAllListenerIDs() throws AuraException, RemoteException {
        List<String> listenerList = new ArrayList();

        DBIterator iter = mdb.getDataStore().getAllIterator(ItemType.USER);

        try {
            while (iter.hasNext()) {
                Item item = (Item) iter.next();
                listenerList.add(item.getKey());
            }

        } finally {
            iter.close();
        }
        return listenerList;
    }

    private List<Listener> getNewListeners(long lastCrawl) throws AuraException, RemoteException {
        List<Listener> listenerList = new ArrayList<Listener>();

        DBIterator iter = mdb.getDataStore().getItemsAddedSince(ItemType.USER, new Date(lastCrawl));

        try {
            while (iter.hasNext()) {
                Item item = (Item) iter.next();
                Listener listener = new Listener(item);
                if (listener.getUpdateCount() == 0) {
                    listenerList.add(listener);
                }
            }
        } finally {
            iter.close();
        }
        return listenerList;
    }

    public void crawlListener(Listener listener, boolean force) throws AuraException, RemoteException {
        if (force || needsCrawl(listener)) {
            if (addToCrawlList(listener.getKey())) {
                try {
                    logger.info("Crawling listener " + listener.getName());
                    int updateCount = listener.getUpdateCount();
                    if (listener.getLastFmName() != null) {
                        if (updateCount == 0) {
                            fullCrawlLastFM(listener);
                        } else {
                            weeklyCrawlLastFM(listener);
                        }
                    }
                    weeklyCrawlPandora(listener);
                    updateListenerTags(listener);
                    updateListenerAggregatedPlayCharts(listener);
                    listener.setLastCrawl();
                    listener.incrementUpdateCount();
                    mdb.flush(listener);
                } finally {
                    removeFromCrawlList(listener.getKey());
                }
            } else {
                logger.fine("Skipping listener " + listener.getName() +
                        " because another process is already crawling it");
            }
        } else {
            logger.fine("Skipping listener " + listener.getName());
        }
    }

    private boolean needsCrawl(Listener listener) {
        long delta = System.currentTimeMillis() - listener.getLastCrawl();
        return delta >= getMinCrawlDelta() || listener.getUpdateCount() == 0;
    }

    private long getMinCrawlDelta() {
        return defaultPeriod * 1000L;
    }

    public void crawlAllListeners() throws AuraException, RemoteException {
        List<String> listenerIDs = getAllListenerIDs();
        logger.info(crawlerName+"Crawler: Crawling all " + listenerIDs.size() + " listeners...");
        for (String id : listenerIDs) {
            try {
                Listener listener = mdb.getListener(id);
                if (listener != null) {
                    crawlListener(listener, false);
                }
            } catch (Throwable t) {
                logger.warning("Problem ("+t+") crawler listener '"+id+"'");
                t.printStackTrace();
            }
        }
        logger.info(crawlerName+"Crawler: Crawling all " + listenerIDs.size() + " listeners DONE");
    }

    public long crawlNewListeners(long lastCrawl) throws AuraException, RemoteException {
        long maxCrawl = lastCrawl;
        List<Listener> listeners = getNewListeners(lastCrawl + 1);

        // If there are no new listeners to crawl but we want more, ask for them
        if (listeners.size() == 0 && enableListenerDiscovery &&
                mdb.getDataStore().getItemCount(ItemType.USER) < maxListeners) {
            if (!isNeedMoreNewListeners()) {
                logger.info(crawlerName+"Crawler: Requesting more listeners from discovery thread");
                setNeedMoreNewListeners(true);
                discoveryThread.interrupt();
            } else {
                logger.info(crawlerName+"Crawler: Still waiting for more listeners from discovery thread");
            }
        }

        for (Listener listener : listeners) {
            if (listener.getItem().getTimeAdded() > maxCrawl)  {
                maxCrawl = listener.getItem().getTimeAdded();
            }
            crawlListener(listener, false);
        }
        return maxCrawl;
    }

    private void updateListenerAggregatedPlayCharts(Listener listener) throws AuraException, RemoteException {

        logger.fine(crawlerName+"Crawler: Aggregating play charts for listener "+listener.getKey());

        AttentionConfig aC = new AttentionConfig();
        aC.setSourceKey(listener.getKey());
        aC.setType(Attention.Type.PLAYED);
        
        String script =   "# process takes a list of attention as its input \n" +
                          "def process(attns): \n" +
                          "    d = {} \n" +
                          "    for a in attns.toArray(): \n" +
                          "        k = str(a.getTargetKey()) \n" +
                          "        if not k in d: \n" +
                          "            d[k] = 0 \n" +
                          "        d[k] += a.getNumber() \n" +
                          "    return d \n" +
                          "\n" +
                          "#\n" +
                          "# collect takes a list of the results from above \n" +
                          "def collect(results): \n" +
                          "    d = {} \n" +
                          "    for tD in results.toArray(): \n" +
                          "        for k,v in tD.items(): \n" +
                          "            if not k in d: \n" +
                          "                d[k] = 0 \n" +
                          "            d[k] += v \n" +
                          "    return d \n";

        PyDictionary pyDict = (PyDictionary) mdb.getDataStore().processAttention(aC, script, "python");
        PyList items = pyDict.items();
        listener.clearAggregatedPlayCounts();
        for (int i=0; i<items.size(); i++) {
            PyTuple p = (PyTuple) items.get(i);
            listener.setAggregatedPlayCount((String)p.get(0), ((BigInteger)p.get(1)).intValue());
        }
        listener.updatePlayHistoryAggregationHash();
    }

    private void periodicallyUpdateAggregatedCharts() {
        FixedPeriod fp = new FixedPeriod(6 * 60 * 60 * 1000L);
        while (running) {
            try {
                fp.start();

                List<String> listenerIDs = getAllListenerIDs();
                logger.info(crawlerName+"Crawler: Updating aggregated charts for " +
                        listenerIDs.size() + " listeners");
                int cnt = 0;
                long startTime = System.currentTimeMillis();
                for (String id : listenerIDs) {
                    Listener listener = mdb.getListener(id);
                    if (listener != null && listener.playHistoryAggregationNeedsUpdate()) {
                        updateListenerAggregatedPlayCharts(listener);
                        mdb.flush(listener);
                        cnt++;
                    }
                }
                logger.info(crawlerName+"Crawler: Done updating aggregated charts. " +
                        cnt + " needed update. " + ((System.currentTimeMillis() - startTime) / 1000) + "secs");

                fp.end();

            } catch (AuraException ex) {
                logger.warning("Aura Exception while updating agg charts " + ex);
                ex.printStackTrace();
            } catch (RemoteException ex) {
                logger.warning("Remote Exception while updating agg charts " + ex);
            } catch (IOException ex) {
                logger.warning("IO Exception while updating agg charts " + ex);
                ex.printStackTrace();
            } catch (Throwable t) {
                logger.severe("Unexpected error during agg chart updating " + t);
                t.printStackTrace();
            }
        }
    }


    private void updateListenerTags(Listener listener) throws AuraException, RemoteException {
        ScoredManager<String> sm = new ScoredManager();
        List<Scored<String>> scoredArtistIDs = mdb.getWeightedAttendedArtistsAsIDs(listener.getKey());
        double max = getMax(scoredArtistIDs);
        for (Scored<String> scoredArtistID : scoredArtistIDs) {
            Artist artist = mdb.artistLookup(scoredArtistID.getItem());
            double artistWeight = 100.0 * scoredArtistID.getScore() / max;
            if (artist != null) {
                List<Tag> tags = artist.getTags(Artist.TagType.SOCIAL);
                for (Tag tag : tags) {
                    logger.finer("Adding " + tag.getName() + " " + tag.getCount() + " " + artistWeight + " " +
                            tag.getCount() * artistWeight);
                    sm.accum(tag.getName(), tag.getCount() * artistWeight);
                }
            }
        }

        listener.clearSocialTags();
        List<Scored<String>> tags = sm.getAll();
        for (Scored<String> tag : tags) {
            int score = (int) tag.getScore();
            listener.addSocialTag(tag.getItem(), score);
        }
    }

    private void updateListenerWeeklyCharts(Listener listener) throws AuraException {
        logger.info("ListenerCrawler:WeeklyCharts: Crawling listener " + listener.getKey());
        try {
            List<Integer[]> chartList = getLastFM2().getWeeklyChartListByUser(listener.getLastFmName());

            // For all available charts on lastfm
            Collections.reverse(chartList);
            int min = Math.min(chartList.size(), nbrChartWeek);
            for (int i=0; i<min; i++) {
                Integer[] ranges = chartList.get(i);
                long timestamp = ranges[0] * 1000L;
                // If we did not already crawl that chart for the user
                if (!listener.crawledPlayHistory(ranges[0])) {
                    logger.fine("ListenerCrawler:WeeklyCharts: Adding plays for listener '" +
                            listener.getKey() + "' for week " + ranges[0]);
                    List<LastItem> items = getLastFM2().getWeeklyArtistChartByUser(
                            listener.getLastFmName(), ranges[0], ranges[1]);
                    for (LastItem artistItem : items) {
                        // If we don't have the artist in the datastore, add it to
                        // the artist crawler's queue so we get it's info later on
                        Artist artist = mdb.artistLookup(artistItem.getMBID());
                        if (artist==null) {
                            boolean added = enqueueArtistToCrawl(artistItem, -1);
                            if (added) {
                                logger.fine("ListenerCrawler:WeeklyCharts: Added '" +
                                        artistItem.getName() + "' to artist crawler queue");
                            }
                        }
                        mdb.addPlayAttentionsWithDetails(listener.getKey(),
                                artistItem.getMBID(), "lastfm charts", artistItem.getFreq(), timestamp);
                    }
                    listener.addCrawledPlayHistoryDate(ranges[0]);
                    logger.fine("ListenerCrawler:WeeklyCharts: Added plays for listener '" +
                            listener.getKey() + "' for week " + new Date(timestamp));
                    Thread.yield();
                } else {
                    logger.fine("ListenerCrawler:WeeklyCharts: Skipping plays for listener '" +
                            listener.getKey() + "' for week " + new Date(timestamp));
                }
            }
            listener.flush(mdb.getDataStore());
        } catch (Throwable t) {
            logger.warning("Exception ("+t+") updating weekly charts from last.fm for user " + listener.getName());
            t.printStackTrace();
        }
    }

    double getMax(List<Scored<String>> l) {
        double max = -Double.MAX_VALUE;
        for (Scored s : l) {
            if (s.getScore() > max) {
                max = s.getScore();
            }
        }
        return max;
    }

    private void fullCrawlLastFM(Listener listener) throws AuraException {
        updateListenerWeeklyCharts(listener);
    }

    /**
     * Performs the 'weekly' crawl of pandora.  Collects the pandora set of favorite
     * artists for the user and adds 'five star' ratings for the artists if we haven't
     * already rated them.
     * @param listener the listener of interest
     * @throws com.sun.labs.aura.util.AuraException
     */
    private void weeklyCrawlPandora(Listener listener) throws AuraException {
        if (listener.getPandoraName() != null) {
            try {
                logger.fine("Pandora crawl for " + listener.getName());
                List<String> artists = pandora.getFavoriteArtistNamesForUser(listener.getPandoraName());
                for (String artistName : artists) {
                    Artist artist = mdb.artistFindBestMatch(artistName);
                    if (artist != null && !hasRating(listener, artist))  {
                        mdb.addRating(listener.getKey(), artistName, 5);
                        logger.fine("pandora crawl, added rating attention for artist " + artist.getName());
                    } else {
                        logger.fine("pandora crawl, skipping artist " + artistName);
                    }
                }
            } catch (IOException ex) {
                logger.warning("Problem collecting data from pandora for user " + listener.getName());
            }
        }
    }

    /**
     * Determines if the given listener has applied a rating to the given artist
     * @param listener the listener of interest
     * @param artist the artist of interest
     * @return true if the artist has been rted
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    private boolean hasRating(Listener listener, Artist artist) throws AuraException, RemoteException {
        AttentionConfig lac = new AttentionConfig();
        lac.setSourceKey(listener.getKey());
        lac.setTargetKey(artist.getKey());
        lac.setType(Attention.Type.RATING);
        return mdb.getDataStore().getAttention(lac).size() > 0;
    }

    private void weeklyCrawlLastFM(Listener listener) throws AuraException {
        updateListenerWeeklyCharts(listener);
    }
    
    @ConfigComponent(type = DataStore.class)
    public final static String PROP_DATA_STORE = "dataStore";
    @ConfigComponent(type = CrawlerController.class)
    public final static String PROP_CRAWLER_CONTROLLER = "crawlerController";
    @ConfigComponent(type = ArtistCrawler.class)
    public final static String PROP_ARTIST_CRAWLER = "artistCrawler";
    @ConfigInteger(defaultValue = 1000)
    public final static String PROP_MAX_LISTENERS = "maxListeners";
    private int maxListeners;
    @ConfigInteger(defaultValue = 5)
    public final static String PROP_NBR_CHARTS_WEEK = "nbrChartWeek";
    private int nbrChartWeek;
    /**
     * the configurable property for default processing period (in seconds)
     */
    @ConfigInteger(defaultValue = 7 * 24 * 60 * 60, range = {1, 60 * 60 * 24 * 365})
    public final static String PROP_DEFAULT_PERIOD = "defaultPeriod";
    protected int defaultPeriod;
    @ConfigBoolean(defaultValue = false)
    public final static String PROP_ENABLE_LISTENER_DISCOVERY = "enableListenerDiscovery";
    private boolean enableListenerDiscovery;

}
