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
import com.sun.labs.aura.music.web.lastfm.LastArtist;
import com.sun.labs.aura.music.web.lastfm.LastItem;
import com.sun.labs.aura.music.web.lastfm.LastFM;
import com.sun.labs.aura.music.web.lastfm.LastFM2;
import com.sun.labs.aura.music.web.pandora.Pandora;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.RemoteComponentManager;
import com.sun.labs.aura.util.Scored;
import com.sun.labs.aura.util.Tag;
import com.sun.labs.util.props.ConfigComponent;
import com.sun.labs.util.props.ConfigInteger;
import com.sun.labs.util.props.Configurable;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author plamere
 */
public class ListenerCrawler implements AuraService, Configurable, Crawler {

    private LastFM lastfm;
    private LastFM2 lastfm2;
    private Pandora pandora;
    private MusicDatabase mdb;
    private RemoteComponentManager rcm;
    private boolean running = false;
    private Logger logger;
    private Set<String> crawlsInProgress = new HashSet<String>();


    
    private synchronized void removeFromCrawlList(String uid) {
        crawlsInProgress.remove(uid);
    }

    private synchronized boolean addToCrawlList(String uid) {
        if (crawlsInProgress.contains(uid)) {
            return false;
        } else {
            crawlsInProgress.add(uid);
            return true;
        }
    }

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
        }
    }

    @Override
    public synchronized void stop() {
        running = false;
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

    @Override
    public void add(String newID) throws AuraException, RemoteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void newProperties(PropertySheet ps) throws PropertyException {
        logger = ps.getLogger();
        defaultPeriod = ps.getInt(PROP_DEFAULT_PERIOD);
        newCrawlPeriod = ps.getInt(PROP_NEW_CRAWL_PERIOD);
        rcm = new RemoteComponentManager(ps.getConfigurationManager(), ArtistCrawler.class);
        try {
            lastfm = new LastFM();
            lastfm.setTrace(false);
            lastfm2 = new LastFM2();
            pandora = new Pandora();

            mdb = new MusicDatabase(ps.getConfigurationManager());
        } catch (AuraException ex) {
            throw new PropertyException(ex, ps.getInstanceName(), "musicDatabase", "problems with the music database");
        } catch (IOException ex) {
            throw new PropertyException(ex, ps.getInstanceName(), "", "problems connecting to last.fm/pandora");
        }
    }

    private boolean enqueueArtistToCrawler(LastArtist lA, int popularity) throws AuraException, RemoteException {
        return ((ArtistCrawler)rcm.getComponent()).enqueue(lA, popularity);
    }

    private void periodicallyCrawlAllListeners() {
        FixedPeriod fp = new FixedPeriod(defaultPeriod * 1000L);
        logger.info("Crawling all listeners with a period of " + defaultPeriod + " secs");
        while (running) {
            try {
                fp.start();

                crawlAllListeners();

                fp.end();
            } catch (InterruptedException ex) {
            } catch (AuraException ex) {
                logger.warning("AuraException while crawling users" + ex);
            } catch (RemoteException ex) {
                logger.warning("Remote exception while crawling users" + ex);
            }
        }
    }

    private void periodicallyCrawlNewListeners() {
        long lastCrawl = 0;
        logger.info("Crawling new listeners with a period of " + newCrawlPeriod + " secs");
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
                updateListenerArtists(listener);
                updateListenerTags(listener);
                listener.setLastCrawl();
                listener.incrementUpdateCount();
                mdb.flush(listener);
                
                removeFromCrawlList(listener.getKey());
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
        logger.info("CrawlAllListeners crawling: " + listenerIDs.size());
        for (String id : listenerIDs) {
            Listener listener = mdb.getListener(id);
            if (listener != null) {
                crawlListener(listener, false);
            }
        }
    }

    public long crawlNewListeners(long lastCrawl) throws AuraException, RemoteException {
        long maxCrawl = lastCrawl;
        List<Listener> listeners = getNewListeners(lastCrawl + 1);
        for (Listener listener : listeners) {
            if (listener.getItem().getTimeAdded() > maxCrawl)  {
                maxCrawl = listener.getItem().getTimeAdded();
            }
            crawlListener(listener, false);
        }
        return maxCrawl;
    }

    private void updateListenerArtists(Listener listener) throws AuraException, RemoteException {
        List<Scored<String>> scoredArtistIDs = mdb.getAllArtistsAsIDs(listener.getKey());
        listener.clearFavoriteArtists();
        for (Scored<String> scoredArtistID : scoredArtistIDs) {
            listener.addFavoriteArtist(scoredArtistID.getItem(), (int) scoredArtistID.getScore());
        }
    }

    private void updateListenerTags(Listener listener) throws AuraException, RemoteException {
        ScoredManager<String> sm = new ScoredManager();
        List<Scored<String>> scoredArtistIDs = mdb.getAllArtistsAsIDs(listener.getKey());
        double max = getMax(scoredArtistIDs);
        for (Scored<String> scoredArtistID : scoredArtistIDs) {
            Artist artist = mdb.artistLookup(scoredArtistID.getItem());
            double artistWeight = 100.0 * scoredArtistID.getScore() / max;
            if (artist != null) {
                List<Tag> tags = artist.getSocialTags();
                for (Tag tag : tags) {
                    logger.fine("Adding " + tag.getName() + " " + tag.getCount() + " " + artistWeight + " " +
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
        try {
            List<Integer[]> chartList = lastfm2.getWeeklyChartListByUser(listener.getLastFmName());
            // For all available charts on lastfm
            for (Integer[] ranges : chartList) {
                // If we did not already crawl that chart for the user
                if (!listener.crawledPlayHistory(ranges[0])) {
                    List<LastItem> items = lastfm2.getWeeklyArtistChartByUser(
                            listener.getLastFmName(), ranges[0], ranges[1]);
                    for (LastItem artistItem : items) {
                        // If we don't have the artist in the datastore, add it to
                        // the artist crawler's queue so we get it's info later on
                        Artist artist = mdb.artistLookup(artistItem.getMBID());
                        if (artist==null) {
                            boolean added = enqueueArtistToCrawler(new LastArtist(artistItem.getName(),
                                    artistItem.getMBID()), lastfm.getPopularity(artistItem.getName()));
                            if (added) {
                                logger.fine("ListenerCrawler:WeeklyCharts: Added '" +
                                        artistItem.getName() + "' to artist crawler queue");
                            }
                        }
                        mdb.addPlayAttentionsWithDetails(listener.getKey(),
                                artistItem.getMBID(), "lastfm charts", artistItem.getFreq(), ranges[0]);
                    }
                    listener.addCrawledPlayHistoryDate(ranges[0]);
                    logger.fine("ListenerCrawler:WeeklyCharts: Added plays for listener '" +
                            listener.getKey() + "' for week " + ranges[0]);
                }
            }
        } catch (IOException e) {
            logger.warning("Problem updating weekly charts from last.fm for user " + listener.getName());
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
    @ConfigComponent(type = ArtistCrawler.class)
    public final static String PROP_ARTIST_CRAWLER = "artistCrawler";
    /**
     * the configurable property for default processing period (in seconds)
     */
    @ConfigInteger(defaultValue = 7 * 24 * 60 * 60, range = {1, 60 * 60 * 24 * 365})
    public final static String PROP_DEFAULT_PERIOD = "defaultPeriod";
    protected int defaultPeriod;

    @ConfigInteger(defaultValue =  1 * 60, range = {1, 60 * 60 * 24 * 365})
    public final static String PROP_NEW_CRAWL_PERIOD = "newCrawlPeriod";
    protected int newCrawlPeriod;
}
