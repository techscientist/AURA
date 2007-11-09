
/*
 * FeedCrawler.java
 * 
 * Created on Oct 28, 2007, 9:00:00 PM
 * 
 */
package com.sun.labs.aura.aardvark.crawler;

import com.sun.labs.aura.aardvark.store.Attention;
import com.sun.labs.aura.aardvark.store.item.Entry;
import com.sun.labs.aura.aardvark.store.item.Item;
import com.sun.labs.aura.aardvark.store.ItemStore;
import com.sun.labs.aura.aardvark.store.item.User;
import com.sun.labs.aura.aardvark.store.SimpleAttention;
import com.sun.labs.aura.aardvark.store.item.User;
import com.sun.labs.aura.aardvark.util.AuraException;
import com.sun.labs.util.props.ConfigComponent;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The FeedCrawler is responsible for crawling rss/atom feeds and adding
 * items and attention data associated with the feeds to the item store.
 * 
 * Specific tasks:
 * <ul>
 *  <li> Noticing when a new user item is added to the item store and adding that
 *       user's 'starred item feed' item to the set of feeds in the item store 
 *       if it doesn't already exist
 *  </li>
 *  <li> For each user in the item store
 *      <ul>
 *          <li>Periodically fetch the feed, associated with the user,  
 *          <li>Adding new attention data for the user to the item store
 *          <li> Adding new entry items to the item store
 *      </ul>
 *  </li>
 * </ul>
 *    
 * @author plamere
 */
public class SimpleFeedCrawler implements FeedCrawler {
    
    private int  feedPullCount = 0;
    private int  feedErrorCount = 0;

    /**
     * the configurable property for the UserRefreshManager used by this manager
     */

    @ConfigComponent(type = UserRefreshManager.class)
    public final static String PROP_USER_REFRESH_MANAGER = "userRefreshManager";
    private UserRefreshManager userRefreshManager;

    /**
     * the configurable property for the itemstore used by this manager
     */
    @ConfigComponent(type = ItemStore.class)
    public final static String PROP_ITEM_STORE = "itemStore";
    private ItemStore itemStore;

    private final static Entry[] EMPTY_ENTRY = new Entry[0];
    private volatile Thread crawlerThread;
    private Logger logger;

    public SimpleFeedCrawler() {
    }

    public void newProperties(PropertySheet ps) throws PropertyException {
        userRefreshManager = (UserRefreshManager) ps.getComponent(PROP_USER_REFRESH_MANAGER);
        itemStore = (ItemStore) ps.getComponent(PROP_ITEM_STORE);
        logger = ps.getLogger();
    }

    /**
     * Starts running the crawler
     */
    public synchronized void start() {
        if (crawlerThread == null) {
            crawlerThread = new Thread() {

            @Override
                public void run() {
                            crawler();
                        }
                    };
            crawlerThread.setName("Crawler");
            crawlerThread.start();
        }
    }

    /**
     * Stops running the crawler
     */
    public synchronized void stop() {
        if (crawlerThread != null) {
            crawlerThread = null;
            userRefreshManager.close();
        }
    }

    /**
     * Crawl the data for all of the users in the system.
     */
    private void crawler() {
        while (crawlerThread != null) {
            User user = null;
            try {
                user = userRefreshManager.getNextUserForRefresh();
                if (user != null) {
                    logger.info("Collecting feed for " + user);
                    collectDataForUser(user);
                }
            } catch (InterruptedException ex) {
            } catch (AuraException ex) {
                logger.log(Level.SEVERE, "aura exception", ex);
            } catch (Throwable t) {
                logger.log(Level.SEVERE, "general exception", t);
            } 
        }
    }

    /**
     * Collects the attention data for a particular user and adds it to the item
     * store.  Also adds new entries to the store as necessary.
     * 
     * @param user the user of interest
     * @throws AuraException if an exxception occurs while adding the entry to 
     * the store or updating the user
     */
    private void collectDataForUser(User user) throws AuraException {
        long lastFeedPullTime = user.getLastFetchTime();
        long now = System.currentTimeMillis();

        try {
            Entry[] entries = getNewStarredEntriesFromUser(user, lastFeedPullTime);
            for (Entry entry : entries) {
                attend(user, entry, Attention.Type.STARRED);
            }
        } finally {
            user.setLastFetchTime(now);
            itemStore.put(user);
            userRefreshManager.release(user);
        }
    }

    /**
     * Collects the most recent entries for the user
     * @param user the user of interest
     * @param lastFeedPullTime the last time this feed was collected
     * @return an array of entries. Note that timestamps can be unreliable, the set
     * of entries returned may contained entries that are not new.
     * @throws AuraException if an error occurs getting the feed or the user.
     */
    private Entry[] getNewStarredEntriesFromUser(User user, long lastFeedPullTime) throws AuraException {
        URL feed = user.getStarredItemFeedURL();
        Entry[] entries = getNewestEntries(feed, lastFeedPullTime);
        return entries;
    }

    /**
     * Record an attention datapoint for the user
     * @param user the user of interest
     * @param item the item that the user as attended to
     * @param type the type of attention
     * @throws AuraException if an error occurs while storing the attention data
     */
    private void attend(User user, Item item, Attention.Type type) throws AuraException {
        Attention attention = new SimpleAttention(user.getID(), item.getID(), type);
        if (isUniqueAttention(attention)) {
            logger.info("adding attention " + attention);
            itemStore.attend(attention);
        }
    }

    /**
     * Returns the newest set of entries from a feed
     * @param feedUrl the url of the feed
     * @param lastPullTime the time of the last pull
     * @return an array of entries. Note that timestamps can be unreliable, the set
     * of entries returned may contained entries that are not new.
     * @throws AuraException
     */
    private Entry[] getNewestEntries(URL feedUrl, long lastPullTime) {
        try {
            List<Entry> entries = FeedUtils.processFeed(itemStore, feedUrl);
            feedPullCount++;
            return entries.toArray(EMPTY_ENTRY);
        } catch (AuraException ex) {
            logger.warning("FeedException while reading " + feedUrl);
            feedErrorCount++;
        }
        return EMPTY_ENTRY;
    }

    /**
     * Determines if the attention data is unique.  An attention datapoint is considered unique
     * if either the user or the attention type is unique for a particular item.
     * NOTE: In future versions, we will likely need a more detailed way of determining if an
     * attention is unique.  For instance, a music listener may listened to the same track multimple times,
     * These should be considered unique.
     * @param attention the attention to test
     * @return true if the attention data is unique
     */
    private boolean isUniqueAttention(Attention attention) {
        Item item = itemStore.get(attention.getItemID());
        List<Attention> attentionList = item.getAttentionData();
        for (Attention att : attentionList) {
            if (att.getType() == attention.getType() && att.getUserID() == attention.getUserID()) {
                return false;
            }
        }
        return true;
    }



    public int getFeedErrorCount() {
        return feedErrorCount;
    }

    public int getFeedPullCount() {
        return feedPullCount;
    }


    public static void main(String[] args) {
        for (String s : args) {
            FeedUtils.dumpFeed(s);
        }
    }

    public Feed createFeed(URL feedUrl) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}