/*
 *  Copyright (c) 2008, Sun Microsystems Inc.
 *  See license.txt for license.
 */
package com.sun.labs.aura.music.crawler;

import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.Item.ItemType;
import com.sun.labs.aura.music.Artist;
import com.sun.labs.aura.music.Listener;
import com.sun.labs.aura.music.MusicDatabase;
import com.sun.labs.aura.music.ScoredManager;
import com.sun.labs.aura.music.web.lastfm.LastItem;
import com.sun.labs.aura.music.web.lastfm.LastFM;
import com.sun.labs.aura.music.web.pandora.Pandora;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.ItemSchedulerImpl;
import com.sun.labs.aura.util.Scored;
import com.sun.labs.aura.util.Tag;
import com.sun.labs.util.props.ConfigInteger;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * @author plamere
 */
public class ListenerCrawler extends ItemSchedulerImpl {

    private LastFM lastfm;
    private Pandora pandora;
    private MusicDatabase mdb;
    private boolean running = false;
    private Logger logger;
    private int numThreads;

    @Override
    public void start() {
        if (!running) {
            running = true;
            for (int i = 0; i < numThreads; i++) {
                Thread t = new Thread() {

                    @Override
                    public void run() {
                        scheduledCrawlListeners();
                    }
                };
                t.start();
            }
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public void newProperties(PropertySheet ps) throws PropertyException {
        super.newProperties(ps);
        logger = ps.getLogger();
        numThreads = ps.getInt(PROP_NUM_THREADS);
        try {
            lastfm = new LastFM();
            pandora = new Pandora();
            mdb = new MusicDatabase(dataStore);
        } catch (AuraException ex) {
            throw new PropertyException(ex, ps.getInstanceName(), "musicDatabase", "problems with the music database");
        } catch (IOException ex) {
            throw new PropertyException(ex, ps.getInstanceName(), "", "problems connecting to last.fm/pandora");
        }
    }

    private void scheduledCrawlListeners() {
        while (running) {
            String userID = null;
            try {
                userID = getNextItemKey();
                Listener listener = mdb.getListener(userID);
                crawlListener(listener);
            } catch (InterruptedException ex) {
                break;
            } catch (AuraException ex) {
                logger.warning("AuraException while crawling user " + userID + " " + ex);
            } catch (IOException ex) {
                logger.warning("IOException while crawling user " + userID + " " + ex);
            } finally {
                releaseItem(userID, SCHEDULE_DEFAULT);
            }
        }
    }

    public void crawlListener(Listener listener) throws AuraException, RemoteException {
        if (needsCrawl(listener)) {
            logger.info("Crawling listener " + listener.getName());
            int state = listener.getState();
            if (listener.getLastFmName() != null) {
                if ((state & Listener.STATE_INITIAL_LASTFM_CRAWL) != Listener.STATE_INITIAL_LASTFM_CRAWL) {
                    fullCrawlLastFM(listener);
                    state |= Listener.STATE_INITIAL_LASTFM_CRAWL;
                    listener.setState(state);
                } else {
                    weeklyCrawlLastFM(listener);
                }
            }
            weeklyCrawlPandora(listener);
            updateListenerArtists(listener);
            updateListenerTags(listener);
            listener.setLastCrawl();
            listener.flush(dataStore);
        } else {
            logger.info("Skipping listener " + listener.getName());
        }
    }

    private boolean needsCrawl(Listener listener) {
        long delta = System.currentTimeMillis() - listener.getLastCrawl();
        return delta >= getMinCrawlDelta();
    }

    private long getMinCrawlDelta() {
        return defaultPeriod * 1000L;
    }

    public void crawlAllListeners() throws AuraException, RemoteException, IOException {
        List<Item> items = dataStore.getAll(ItemType.USER);
        for (Item item : items) {
            Listener listener = new Listener(item);
            try {
                crawlListener(listener);
            } catch (IOException ioe) {
            }
        }
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
        try {
            logger.fine("Full crawl for " + listener.getName());
            LastItem[] artists = lastfm.getTopArtistsForUser(listener.getLastFmName());
            for (LastItem artistItem : artists) {
                if (artistItem.getMBID() != null) {
                    Artist artist = mdb.artistLookup(artistItem.getMBID());
                    if (artist != null) {
                        mdb.addPlayAttention(listener.getKey(), artist.getKey(), artistItem.getFreq());
                        logger.fine("last.fm full crawl, added play attention for artist " + artistItem.getName());
                    } else {
                        logger.fine("last.fm full crawl, skipping artist " + artistItem.getName());
                    }
                }
            }
        } catch (IOException ex) {
            logger.warning("Problem collecting data from last.fm for user " + listener.getName());
        }
    }

    private void weeklyCrawlPandora(Listener listener) throws AuraException {
        if (listener.getPandoraName() != null) {
            try {
                logger.fine("Pandora crawl for " + listener.getName());
                Set<String> favs = mdb.getFavoriteArtistsAsIDSet(listener.getKey(), 100000);
                List<String> artists = pandora.getFavoriteArtistNamesForUser(listener.getPandoraName());
                for (String artistName : artists) {
                    Artist artist = mdb.artistFindBestMatch(artistName);
                    if (artist != null && !favs.contains(artist.getKey())) {
                        mdb.addFavoriteAttention(listener.getKey(), artist.getKey());
                        logger.fine("pandora crawl, added play attention for artist " + artist.getName());
                    } else {
                        logger.fine("pandora crawl, skipping artist " + artistName);
                    }
                }
            } catch (IOException ex) {
                logger.warning("Problem collecting data from pandora for user " + listener.getName());
            }
        }
    }

    private void weeklyCrawlLastFM(Listener listener) throws AuraException {
        try {
            logger.fine("Weekly crawl for " + listener.getName());
            LastItem[] artists = lastfm.getWeeklyArtistsForUser(listener.getLastFmName());
            for (LastItem artistItem : artists) {
                if (artistItem.getMBID() != null) {
                    Artist artist = mdb.artistLookup(artistItem.getMBID());
                    if (artist != null) {
                        mdb.addPlayAttention(listener.getKey(), artist.getKey(), artistItem.getFreq());
                    }
                }
            }
        } catch (IOException ex) {
            logger.warning("Problem collecting data from last.fm for user " + listener.getName());
        }
    }
    /**
     * the configurable property for the number of threads used by this manager
     */
    @ConfigInteger(defaultValue = 1, range = {0, 1000})
    public final static String PROP_NUM_THREADS = "numThreads";
}
