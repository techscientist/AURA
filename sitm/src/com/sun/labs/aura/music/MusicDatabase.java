/*
 *  Copyright (c) 2008, Sun Microsystems Inc.
 *  See license.txt for license.
 */
package com.sun.labs.aura.music;

import com.sun.labs.aura.datastore.Attention;
import com.sun.labs.aura.datastore.AttentionConfig;
import com.sun.labs.aura.datastore.DBIterator;
import com.sun.labs.aura.datastore.DataStore;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.Item.ItemType;
import com.sun.labs.aura.datastore.StoreFactory;
import com.sun.labs.aura.datastore.User;
import com.sun.labs.aura.datastore.SimilarityConfig;
import com.sun.labs.aura.recommender.TypeFilter;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.ItemAdapter;
import com.sun.labs.aura.util.RemoteComponentManager;
import com.sun.labs.aura.util.Scored;
import com.sun.labs.aura.util.WordCloud;
import com.sun.labs.minion.ResultsFilter;
import com.sun.labs.util.props.ConfigurationManager;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * @author plamere
 */
public class MusicDatabase {

    public enum Popularity {

        ALL, HEAD, MID, TAIL, HEAD_MID, MID_TAIL
    };

    public enum DBOperation {

        ReadOnly, AddAttention, AddItem
    };

    private List<SimType> simTypes;
    private Map<String, RecommendationType> recTypeMap;
    private Random rng = new Random();
    private ArtistTag rockTag = null;
    private Artist mostPopularArtist = null;
    public final static String DEFAULT_RECOMMENDER = "SimToRecent(2)";
    private double skimPercent = 1;
    private RemoteComponentManager rcm;

    public MusicDatabase(ConfigurationManager cm) throws AuraException {
        this.rcm = new RemoteComponentManager(cm, DataStore.class);
        new Album().defineFields(getDataStore());
        new Artist().defineFields(getDataStore());
        new ArtistTag().defineFields(getDataStore());
        new Event().defineFields(getDataStore());
        new Photo().defineFields(getDataStore());
        new Track().defineFields(getDataStore());
        new Venue().defineFields(getDataStore());
        new Video().defineFields(getDataStore());
        new Listener().defineFields(getDataStore());

        initSimTypes();
        initArtistRecommendationTypes();
    }

    public void shutdown() {
        rcm.shutdown();
    }

    /**
     * Gets the datastore
     * @return the datastore
     */
    public DataStore getDataStore() throws AuraException {
        return (DataStore) rcm.getComponent();
    }

    public void flush(ItemAdapter itemAdapter) throws AuraException {
        try {
            itemAdapter.flush(getDataStore());
        } catch (RemoteException rx) {
            throw new AuraException("Error communicating with item store", rx);
        }
    }

    private void initSimTypes() {
        List<SimType> stypes = new ArrayList();
        stypes.add(new FieldSimType("Social Tags", "Similarity based upon Social Tags", Artist.FIELD_SOCIAL_TAGS));
        stypes.add(new FieldSimType("Bio Tags", "Similarity based upon BIO tags", Artist.FIELD_BIO_TAGS));
        stypes.add(new FieldSimType("Blurb Tags", "Similarity based upon tags extracted from reviews", Artist.FIELD_BLURB_TAGS));
        stypes.add(new FieldSimType("Auto Tags", "Similarity based upon Auto tags", Artist.FIELD_AUTO_TAGS));
        stypes.add(new FieldSimType("Related", "Similarity based upon related artists", Artist.FIELD_RELATED_ARTISTS));
        stypes.add(new AllSimType());
        simTypes = Collections.unmodifiableList(stypes);
    }

    private void initArtistRecommendationTypes() {
        List<RecommendationType> rtypes = new ArrayList();
        rtypes.add(new SimpleArtistRecommendationType());
        rtypes.add(new SimToRecentArtistRecommender());
        rtypes.add(new SimToRecentArtistRecommender2());
        rtypes.add(new SimToUserTagCloud());
        rtypes.add(new CollaborativeFilterer());

        recTypeMap = new HashMap();
        for (RecommendationType rtype : rtypes) {
            recTypeMap.put(rtype.getName(), rtype);
        }
    }

    /**
     * Enrolls a listener in the recommender
     * @param openID the openID of the listener
     * @return the listener
     * @throws AuraException if the listener is already enrolled or a problem occurs while enrolling the listener
     */
    public Listener enrollListener(String openID) throws AuraException, RemoteException {
        if (getListener(openID) == null) {
            try {
                User theUser = StoreFactory.newUser(openID, openID);
                return updateListener(new Listener(theUser));
            } catch (RemoteException rx) {
                throw new AuraException("Error communicating with item store", rx);
            }
        } else {
            throw new AuraException("attempting to enroll duplicate listener " +
                    openID);
        }
    }

    /**
     * Update the version of the listener stored in the datastore
     * 
     * @param listener the listener to update
     * @return the listener
     * @throws AuraException if there was an error
     */
    public Listener updateListener(Listener listener) throws AuraException, RemoteException {
        try {
            return new Listener(getDataStore().putUser(listener.getUser()));
        } catch (RemoteException rx) {
            throw new AuraException("Error communicating with item store", rx);
        }
    }

    /**
     * Adds play info for a listener
     * @param listener the listener
     * @param artistID the artist ID
     * @param playCount the playcount
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public void addPlayAttention(String listenerID, String artistID, int playCount) throws AuraException, RemoteException {
        for (int i = 0; i < playCount; i++) {
            Attention attention = StoreFactory.newAttention(listenerID, artistID,
                    Attention.Type.PLAYED, Long.valueOf(playCount));
            getDataStore().attend(attention);
        }
    }

    /**
     * Adds fav info for a listener
     * @param listener the listener
     * @param artistID the artist ID
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public void addFavoriteAttention(String listenerID, String artistID) throws AuraException, RemoteException {
        Attention attention = StoreFactory.newAttention(listenerID, artistID, Attention.Type.LOVED);
        getDataStore().attend(attention);
    }

    public void addViewedAttention(String listenerID, String artistID) throws AuraException, RemoteException {
        Attention attention = StoreFactory.newAttention(listenerID, artistID, Attention.Type.VIEWED);
        getDataStore().attend(attention);
    }

    /**
     * Adds ratings
     * @param listener the listener
     * @param artistID the artist ID
     * @param rating the  rating (0 to 5)
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public void addRating(String listenerID, String artistID, int numStars) throws AuraException, RemoteException {
        if (numStars < 0 || numStars > 5) {
            throw new IllegalArgumentException("numStars must be between 0 and 5");
        }
        Attention attention = StoreFactory.newAttention(listenerID, artistID,
                Attention.Type.RATING, Long.valueOf(numStars));
        getDataStore().attend(attention);
    }

    public int getLatestRating(String listenerID, String artistID) throws AuraException, RemoteException {
        int rating = 0;
        AttentionConfig ac = new AttentionConfig();
        ac.setType(Attention.Type.RATING);
        ac.setSourceKey(listenerID);
        ac.setTargetKey(artistID);
        List<Attention> attns = getDataStore().getLastAttention(ac, 1);
        if (attns.size() > 0) {
            rating = (int) getNumber(attns.get(0));
        }
        return rating;
    }

    public long getNumber(Attention attn) {
        Long val = attn.getNumber();
        return val == null ? 0L : Long.valueOf(val);
    }

    /**
     * Adds an artist with the given musicbrainz ID to the database
     * @param mbaid the musicbrainz ID
     */
    public void addArtist(String mbaid) throws AuraException, RemoteException {
        if (artistLookup(mbaid) == null) {
            Item item = StoreFactory.newItem(ItemType.ARTIST, mbaid, "(unknown)");
            Artist artist = new Artist(item);
            artist.flush(getDataStore());
        }
    }

    /**
     * Determines if the application has authorization to perform the operation
     * @param appID the application ID
     * @param operation the operation of interest
     * @return true if the application has permission to perform the requested operation.
     */
    public boolean hasAuthorization(String appID, DBOperation operation) {
        return true;        // TBD - write me
    }

    /**
     * Determines if the appID represents a valid application 
     * @param appID the application ID
     * @return true if the application is a valid application
     */
    public boolean isValidApplication(String appID) {
        return true;        // TBD - write me
    }

    /**
     * Adds a tag for a listener for an item
     * @param listener the listener doing the tagging
     * @param item the item being tagged
     * @param tag the tag
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public void addTag(String listenerID, String itemID, String tag) throws AuraException, RemoteException {
        if (getDataStore().getItem(itemID) != null) {
            Attention attention = StoreFactory.newAttention(listenerID, itemID,
                    Attention.Type.TAG, tag);
            getDataStore().attend(attention);
        }
    }

    public void addAttention(String srcKey, String targetKey, Attention.Type type, String value)
            throws AuraException, RemoteException {

        if (getDataStore().getItem(srcKey) == null) {
            throw new AuraException("attention src does not exist");
        }

        if (getDataStore().getItem(targetKey) == null) {
            throw new AuraException("attention target does not exist");
        }

        if (type == Attention.Type.TAG && value == null) {
            throw new AuraException("tag attention must have a value");
        }

        Attention attention = null;
        switch (type) {
            case PLAYED:
                if (value == null) {
                    value = "1";
                }
                try {
                    Long lvalue = Long.parseLong(value);
                    if (lvalue < 1 || lvalue > 1000) {
                        throw new AuraException("Playcount out of valid range");
                    }
                    attention = StoreFactory.newAttention(srcKey, targetKey, type, lvalue);
                } catch (NumberFormatException nfe) {
                    throw new AuraException("value must be numeric");
                }
                break;
            case RATING:
                if (value == null) {
                    throw new AuraException("rating attention must have a value");
                }
                try {
                    Long lvalue = Long.parseLong(value);
                    if (lvalue < 1 || lvalue > 5) {
                        throw new AuraException("rating out of valid range (1-5)");
                    }
                    attention = StoreFactory.newAttention(srcKey, targetKey, type, lvalue);
                } catch (NumberFormatException nfe) {
                    throw new AuraException("value must be numeric");
                }
                break;
            case TAG:
                if (value == null) {
                    throw new AuraException("tag attention must have a value");
                }
                attention = StoreFactory.newAttention(srcKey, targetKey, type, value);
                break;
            default:
                attention = StoreFactory.newAttention(srcKey, targetKey, type);
        }
        getDataStore().attend(attention);
    }

    /**
     * Gets the list of tags applied to the item by the user
     * @param listener the listener 
     * @param item the item 
     * @return
     */
    public List<String> getTags(String listenerID, String itemID) throws AuraException, RemoteException {
        AttentionConfig ac = new AttentionConfig();
        ac.setSourceKey(listenerID);
        ac.setType(Attention.Type.TAG);
        ac.setTargetKey(itemID);

        List<Attention> attns = getDataStore().getAttention(ac);
        List<String> results = new ArrayList(attns.size());
        for (Attention attn : attns) {
            results.add(attn.getString());
        }

        return results;
    }

    /**
     * Gets the list of all tags applied by the user
     * @param listener the listener 
     * @return a list of all tags, scored by there frequency of application
     */
    public List<Scored<String>> getAllTags(String listenerID) throws AuraException, RemoteException {
        ScoredManager<String> sm = new ScoredManager();
        AttentionConfig ac = new AttentionConfig();
        ac.setSourceKey(listenerID);
        ac.setType(Attention.Type.TAG);

        List<Attention> attns = getDataStore().getAttention(ac);
        for (Attention attn : attns) {
            sm.accum(attn.getString(), 1);
        }

        return sm.getAll();
    }

    /**
     * Gets the Favorite artists IDs for a listener
     * @param listener the listener of interest
     * @param max the maximum number to return
     * @return the set of artist IDs
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public Collection<Artist> getFavoriteArtists(String listenerID, int max) throws AuraException, RemoteException {
        Set<String> ids = getFavoriteArtistsAsIDSet(listenerID, max);
        return artistLookup(ids);
    }

    public Set<String> getFavoriteArtistsAsIDSet(String listenerID, int max) throws AuraException, RemoteException {
        AttentionConfig ac = new AttentionConfig();
        ac.setSourceKey(listenerID);
        ac.setType(Attention.Type.LOVED);

        List<Attention> attns = getDataStore().getLastAttention(ac, max);
        Set<String> results = new HashSet();
        for (Attention attn : attns) {
            results.add(attn.getTargetKey());
        }

        return results;
    }

    public List<Scored<String>> getRecentTopArtistsAsIDs(String listenerID, int max) throws AuraException, RemoteException {
        ScoredManager<String> sm = new ScoredManager();

        AttentionConfig ac = new AttentionConfig();
        ac.setSourceKey(listenerID);
        List<Attention> attns = getDataStore().getLastAttention(ac, max * 100);
        for (Attention attn : attns) {
            if (isArtist(attn.getTargetKey())) {
                int score = getAttentionScore(attn);
                if (score != 0) {
                    sm.accum(attn.getTargetKey(), score);
                }

            }
        }
        return sm.getTopN(max);
    }

    private int getAttentionScore(Attention attn) {
        long attentionValue = getNumber(attn);
        int score = 0;
        if (attn.getType() == Attention.Type.PLAYED) {
            score = (int) (attentionValue == 0L ? 1 : attentionValue);
        } else if (attn.getType() == Attention.Type.LOVED) {
            score = 100;
        } else if (attn.getType() == Attention.Type.DISLIKED) {
            score = -100;
        } else if (attn.getType() == Attention.Type.RATING) {
            if (attentionValue == 5) {
                score = 100;
            } else if (attentionValue == 4) {
                score = 10;
            } else if (attentionValue == 2) {
                score = -10;
            } else if (attentionValue == 1) {
                score = -100;
            }

        }
        return score;
    }

    public List<Scored<String>> getRecentFiveStarArtists(String listenerID, int max) throws AuraException, RemoteException {
        AttentionConfig ac = new AttentionConfig();
        ac.setSourceKey(listenerID);
        ac.setType(Attention.Type.RATING);
        ac.setNumberVal(5L);

        ScoredManager<String> sm = new ScoredManager();
        List<Attention> attns = getDataStore().getLastAttention(ac, max);
        for (Attention attn : attns) {
            sm.accum(attn.getTargetKey(), 1);
        }

        return sm.getTopN(max);
    }

    public List<Scored<String>> getAllArtistsAsIDs(String listenerID) throws AuraException, RemoteException {
        ScoredManager<String> sm = new ScoredManager();
        AttentionConfig ac = new AttentionConfig();
        ac.setSourceKey(listenerID);
        DBIterator<Attention> attentionIterator = getDataStore().getAttentionIterator(ac);
        try {
            while (attentionIterator.hasNext()) {
                Attention attn = attentionIterator.next();
                if (isArtist(attn.getTargetKey())) {
                    sm.accum(attn.getTargetKey(), getAttentionScore(attn));
                }

            }
        } finally {
            attentionIterator.close();
        }

        return sm.getAll();
    }


    /**
     * Gets all of the item keys for items of a particular type
     * @param type the type of interest
     * @return a list containing all of the IDs of that type
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public List<String> getAllItemKeys(ItemType type) throws AuraException, RemoteException {
        List<String> keys = new ArrayList<String>();
        DBIterator<Item> itemIterator = getDataStore().getAllIterator(type);
        try {
            while (itemIterator.hasNext()) {
                Item item = itemIterator.next();
                keys.add(item.getKey());
            }
        } finally {
            itemIterator.close();
        }
        return keys;
    }

    private boolean isArtist(String id) {
        // BUG: fix this, but don't be expensive
        return true;
    }

    public List<Scored<Artist>> getRecommendations(String listenerID, int max) throws AuraException, RemoteException {
        Set<String> skipIDS = getAttendedToArtists(listenerID);
        Artist artist = getRandomGoodArtistFromListener(listenerID);
        List<Scored<Artist>> results = new ArrayList();
        List<Scored<Artist>> simArtists = artistFindSimilar(artist.getKey(), max * 5);
        for (Scored<Artist> sartist : simArtists) {
            if (!skipIDS.contains(sartist.getItem().getKey())) {
                results.add(sartist);
                if (results.size() >= max) {
                    break;
                }

            }
        }
        return results;
    }

    /**
     * Gets the most recent attention that matches the given data
     * @param srcID the desired src ID (typically a listener ID) (or null for any)
     * @param targetID the desired target id (or null for any)
     * @param type the desired attention typ (or null for all types)
     * @param count the return count
     * @return a list of the most recent attentions that match the give set of parameters
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public List<Attention> getRecentAttention(String srcID, String targetID, Attention.Type type, int count)
            throws AuraException, RemoteException {
        AttentionConfig ac = new AttentionConfig();
        ac.setSourceKey(srcID);
        ac.setTargetKey(targetID);
        ac.setType(type);
        return getDataStore().getLastAttention(ac, count);
    }

    public TagCloud tagCloudCreate(
            String id, String name) throws AuraException {
        if (getTagCloud(id) == null) {
            Item item = StoreFactory.newItem(ItemType.TAG_CLOUD, id, name);
            return new TagCloud(item);
        } else {
            throw new AuraException("attempting to create duplicate tagcloud " + id);
        }

    }

    public TagCloud getTagCloud(
            String id) throws AuraException {
        try {
            Item item = getDataStore().getItem(id);
            return new TagCloud(item);
        } catch (RemoteException rx) {
            throw new AuraException("Error communicating with item store", rx);
        }

    }

    private Artist getRandomGoodArtistFromListener(String listenerID) throws AuraException, RemoteException {
        Collection<Artist> artists = getFavoriteArtists(listenerID, 20);
        Artist artist = null;
        if (artists.size() > 0) {
            artist = selectRandom(artists);
        } else {
            artist = getMostPopularArtist();
        }

        return artist;
    }

    private Artist getMostPopularArtist() throws AuraException {
        if (mostPopularArtist == null) {
            List<Artist> popularList = artistGetMostPopular(1);
            if (popularList.size() > 0) {
                mostPopularArtist = popularList.get(0);
            } else {
                throw new AuraException("No artists in database");
            }

        }
        return mostPopularArtist;
    }

    private Set<String> getAttendedToArtists(String listenerID) throws AuraException, RemoteException {
        AttentionConfig ac = new AttentionConfig();
        ac.setSourceKey(listenerID);

        Set<String> ids = new HashSet();
        DBIterator<Attention> attentionIterator = getDataStore().getAttentionIterator(ac);
        try {
            while (attentionIterator.hasNext()) {
                Attention attn = attentionIterator.next();
                if (isArtist(attn.getTargetKey())) {
                    ids.add(attn.getTargetKey());
                }

            }
        } finally {
            attentionIterator.close();
        }

        return ids;
    }

    private <T> T selectRandom(Collection<T> l) {
        if (l.size() > 0) {
            ArrayList<T> list = new ArrayList<T>(l);
            int index = rng.nextInt(list.size());
            return list.get(index);
        } else {
            return null;
        }

    }

    /**
     * Deletes a listener from the data store
     * 
     * @param listener the listener to delete
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public void deleteListener(String listenerID) throws AuraException, RemoteException {
        getDataStore().deleteUser(listenerID);
    }

    /**
     * Gets the attention data for a listener
     * @param listener the listener of interest
     * @param type the type of attention data of interest (null indicates all)
     * @return the list of attention data (sorted by timestamp)
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public List<Attention> getLastAttentionData(String listenerID, Attention.Type type,
            int count) throws AuraException, RemoteException {
        AttentionConfig ac = new AttentionConfig();
        ac.setSourceKey(listenerID);
        ac.setType(type);
        return getDataStore().getLastAttention(ac, count);
    }

    /**
     * Gets the listener from the openID
     * @param openID the openID for the listener
     * @return the listener or null if the listener doesn't exist
     */
    public Listener getListener(
            String openID) throws AuraException, RemoteException {
        try {
            User user = getDataStore().getUser(openID);
            if (user != null) {
                return new Listener(user);
            } else {
                return null;
            }

        } catch (RemoteException rx) {
            throw new AuraException("Error communicating with item store", rx);
        }

    }

    /**
     * Searches for artists that match the given name
     * @param artistName the name to search for
     * @param returnCount the number of artists to return
     * @return a list fo artists scored by how well they match the query
     * @throws com.sun.labs.aura.util.AuraException
     */
    public List<Scored<Artist>> artistSearch(String artistName, int returnCount) throws AuraException {
        artistName = normalizeTextForQuery(artistName);
        String squery = "(aura-type = artist) <AND> (aura-name <matches> \"*" + artistName + "*\")";
        List<Scored<Item>> scoredItems = query(squery, returnCount);
        return convertToScoredArtistList(scoredItems);
    }



    private String normalizeTextForQuery(String text) {
        return text.replaceAll("\"", "?");
    }

    /**
     * Looks up an Artist by the ID of the artist
     * @param artistID the musicbrainz id of the artist
     * @return the artist or null if the artist could not be found
     * @throws com.sun.labs.aura.util.AuraException
     */
    public Artist artistLookup(
            String artistID) throws AuraException {
        Item item = getItem(artistID);
        if (item != null) {
            typeCheck(item, ItemType.ARTIST);
            return new Artist(item);
        }

        return null;
    }

    /**
     * Looks up a collection of artists by id
     * @param ids the collection of ids for the artist
     * @return the collection of artists
     * @throws com.sun.labs.aura.util.AuraException
     */
    public Collection<Artist> artistLookup(Collection<String> ids) throws AuraException {
        Collection<Item> items = getItems(ids);
        Collection<Artist> artists = new ArrayList<Artist>();
        for (Item item : items) {
            typeCheck(item, ItemType.ARTIST);
            artists.add(new Artist(item));
        }

        return artists;
    }

    /**
     * Gets the similarity types for the system. The simlarity types control
     * the type of artist similarity used.
     * @return the list of SimTypes
     */
    public List<SimType> getSimTypes() {
        return simTypes;
    }

    /**
     * Gets the recommendation types for the system.
     * @return
     */
    public List<RecommendationType> getArtistRecommendationTypes() {
        return new ArrayList(recTypeMap.values());
    }

    public RecommendationType getArtistRecommendationType(
            String recTypeName) {
        return recTypeMap.get(recTypeName);
    }

    public boolean isValidRecommendationType(String name) {
        for (String rname : recTypeMap.keySet()) {
            if (rname.equalsIgnoreCase(name)) {
                return true;
            }

        }
        return false;
    }

    /**
     * Given an artist query, find the best matching artist
     * @param artistName an artist query
     * @return the best matching artist or null if no match could be found.
     * @throws com.sun.labs.aura.util.AuraException
     */
    public Artist artistFindBestMatch(
            String artistName) throws AuraException {
        List<Scored<Artist>> artists = artistSearch(artistName, 1);
        if (artists.size() == 1) {
            return artists.get(0).getItem();
        }

        return null;
    }

    /**
     * Finds the best matching artist tag
     * @param artistTagName the name of the artist tag
     * @return the best matching artist tag or null if none could be found.
     * @throws com.sun.labs.aura.util.AuraException
     */
    public ArtistTag artistTagFindBestMatch(
            String artistTagName) throws AuraException {
        List<Scored<ArtistTag>> artistTags = artistTagSearch(artistTagName, 1);
        if (artistTags.size() == 1) {
            return artistTags.get(0).getItem();
        }

        return null;
    }

    /**
     * Find the most similar artist to a given artist
     * @param artistID the ID of the seed artist
     * @param count the number of similar artists to return
     * @return a list of artists scored by their similarity to the seed artist.
     * @throws com.sun.labs.aura.util.AuraException
     */
    public List<Scored<Artist>> artistFindSimilar(String artistID, int count) throws AuraException {
        List<Scored<Item>> simItems = findSimilar(artistID, Artist.FIELD_SOCIAL_TAGS, count, ItemType.ARTIST);
        return convertToScoredArtistList(simItems);
    }

    /**
     * Find the most similar artist to a given artist
     * @param artistID the ID of the seed artist
     * @param count the number of similar artists to return
     * @param popularity the popularity of the resulting artists
     * @return a list of artists scored by their similarity to the seed artist.
     * @throws com.sun.labs.aura.util.AuraException
     */
    public List<Scored<Artist>> artistFindSimilar(String artistID, int count, Popularity popularity) throws AuraException {
        List<Scored<Item>> simItems = findSimilar(artistID, Artist.FIELD_SOCIAL_TAGS, count, ItemType.ARTIST, popularity);
        return convertToScoredArtistList(simItems);
    }

    /**
     * Find the most similar artist to a given tagcloud
     * @param tagCloudID the ID of the tag cloud
     * @param count the number of similar artists to return
     * @return a list of artists scored by their similarity to the seed artist.
     * @throws com.sun.labs.aura.util.AuraException
     */
    public List<Scored<Artist>> tagCloudFindSimilarArtists(TagCloud tagCloud, int count) throws AuraException {
        return wordCloudFindSimilarArtists(tagCloud.getWordCloud(), count);
    }

    /**
     * Find the most similar artist to a given WordCloud
     * @param tagCloudID the ID of the tag cloud
     * @param count the number of similar artists to return
     * @return a list of artists scored by their similarity to the seed artist.
     * @throws com.sun.labs.aura.util.AuraException
     */
    public List<Scored<Artist>> wordCloudFindSimilarArtists(WordCloud wc, int count) throws AuraException {
        return wordCloudFindSimilarArtists(wc, count, Popularity.ALL);
    }

    /**
     * Find the most similar artist to a given WordCloud
     * @param tagCloudID the ID of the tag cloud
     * @param count the number of similar artists to return
     * @return a list of artists scored by their similarity to the seed artist.
     * @param popularity the popularity of the resulting artists
     * @throws com.sun.labs.aura.util.AuraException
     */
    public List<Scored<Artist>> wordCloudFindSimilarArtists(WordCloud wc, int count, Popularity pop) throws AuraException {
        List<Scored<Item>> simItems = findSimilar(wc, Artist.FIELD_SOCIAL_TAGS, count, ItemType.ARTIST, pop);
        return convertToScoredArtistList(simItems);
    }

    /**
     * Find the most similar tag cloud to a given tagcloud
     * @param tagCloudID the ID of the tag cloud
     * @param count the number of similar artists to return
     * @return a list of artists scored by their similarity to the seed artist.
     * @throws com.sun.labs.aura.util.AuraException
     */
    public List<Scored<TagCloud>> tagCloudFindSimilarTagClouds(TagCloud tagCloud, int count) throws AuraException {
        // BUG: fix this once the new similarity methods are in place
        List<Scored<Item>> simItems = findSimilar(tagCloud.getKey(), Artist.FIELD_SOCIAL_TAGS, count, ItemType.TAG_CLOUD);
        return convertToScoredTagCloudList(simItems);
    }

    /**
     * Find the most similar listenr to a given listeners
     * @param userID the ID of the user
     * @param count the number of similar listeners to return
     * @return a list of listeners scored by their similarity to the seed listener.
     * @throws com.sun.labs.aura.util.AuraException
     */
    public List<Scored<Listener>> listenerFindSimilar(String userID, int count) throws AuraException {
        List<Scored<Item>> simItems = findSimilar(userID, count, ItemType.USER);
        return convertToScoredListenerList(simItems);
    }

    /**
     * Find the most similar artist to a given artist
     * @param artistID the ID of the seed artist
     * @param field the field to use for similarity
     * @param count the number of similar artists to return
     * @return a list of artists scored by their similarity to the seed artist.
     * @throws com.sun.labs.aura.util.AuraException
     */
    public List<Scored<Artist>> artistFindSimilar(String artistID, String field, int count) throws AuraException {
        return artistFindSimilar(artistID, field, count, Popularity.ALL);
    }

    /**
     * Find the most similar artist to a given artist
     * @param artistID the ID of the seed artist
     * @param field the field to use for similarity
     * @param count the number of similar artists to return
     * @param popularity the popularity of the resulting artists
     * @return a list of artists scored by their similarity to the seed artist.
     * @throws com.sun.labs.aura.util.AuraException
     */
    public List<Scored<Artist>> artistFindSimilar(String artistID, String field, int count, Popularity popularity) throws AuraException {
        List<Scored<Item>> simItems = findSimilar(artistID, field, count, ItemType.ARTIST, popularity);
        return convertToScoredArtistList(simItems);
    }

    public List<Scored<ArtistTag>> artistTagSearch(String artistTagName, int returnCount) throws AuraException {
        artistTagName = normalizeTextForQuery(artistTagName);
        String query = "(aura-type = ARTIST_TAG) <AND> (aura-name <matches> \"*" + artistTagName + "*\")";
        List<Scored<Item>> scoredItems = query(query, returnCount);
        return convertToScoredArtistTagList(scoredItems);
    }

    public List<Scored<String>> artistExplainSimilarity(String artistID1, String artistID2, int count) throws AuraException {
        try {
            return getDataStore().explainSimilarity(artistID1, artistID2, new SimilarityConfig(Artist.FIELD_SOCIAL_TAGS, count));
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    public List<Scored<String>> artistExplainSimilarity(WordCloud cloud, String artistID1, int count) throws AuraException {
        try {
            return getDataStore().explainSimilarity(cloud, artistID1, new SimilarityConfig(Artist.FIELD_SOCIAL_TAGS, count));
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    public List<Scored<String>> artistExplainSimilarity(String artistID1, String artistID2, String field, int count) throws AuraException {
        try {
            return getDataStore().explainSimilarity(artistID1, artistID2, new SimilarityConfig(field, count));
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    public float artistGetNormalizedPopularity(Artist artist) throws AuraException {
        return artist.getPopularity() / getMostPopularArtist().getPopularity();
    }

    public List<String> artistGetMostPopularNames(int count) throws AuraException {
        List<String> artistNames = new ArrayList();
        for (Artist artist : artistGetMostPopular(count)) {
            artistNames.add(artist.getName());
        }

        return artistNames;

    }

    public List<Artist> artistGetMostPopular(int count) throws AuraException {
        try {
            List<Scored<Item>> items = getDataStore().query("aura-type=ARTIST", "-popularity", count, null);
            List<Artist> artists = new ArrayList<Artist>();
            for (Scored<Item> i : items) {
                artists.add(new Artist(i.getItem()));
            }

            return artists;

        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }
    }

    public List<Listener> listenerGetMostActive(int count) throws AuraException {
        try {
            // TBD - activity field has not been added to the listner yet.
            List<Scored<Item>> items = getDataStore().query("aura-type=USER", "-score", count, null);
            List<Listener> listeners = new ArrayList<Listener>();
            for (Scored<Item> i : items) {
                listeners.add(new Listener(i.getItem()));
            }
            return listeners;

        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }
    }

    public List<ArtistTag> artistTagGetMostPopular(int count) throws AuraException {
        try {
            List<Scored<Item>> items = getDataStore().query("aura-type=ARTIST_TAG", "-popularity", count, null);
            List<ArtistTag> artistTags = new ArrayList();
            for (Scored<Item> i : items) {
                artistTags.add(new ArtistTag(i.getItem()));
            }

            return artistTags;
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    public List<String> artistTagGetMostPopularNames(int count) throws AuraException {
        List<ArtistTag> artistTags = artistTagGetMostPopular(count);
        List<String> artistTagNames = new ArrayList();
        for (ArtistTag artistTag : artistTags) {
            artistTagNames.add(artistTag.getName());
        }

        return artistTagNames;
    }

    public float artistTagGetNormalizedPopularity(ArtistTag aTag) throws AuraException {
        if (rockTag == null) {
            rockTag = artistTagLookup(ArtistTag.nameToKey("rock"));
        }

        return aTag.getPopularity() / rockTag.getPopularity();
    }

    public ArtistTag artistTagLookup(
            String artistTagID) throws AuraException {
        Item item = getItem(artistTagID);
        if (item != null) {
            typeCheck(item, ItemType.ARTIST_TAG);
            return new ArtistTag(item);
        }

        return null;
    }

    /**
     * Looks up a collection of artistTags by id
     * @param ids the collection of ids for the artistTags
     * @return the collection of artists
     * @throws com.sun.labs.aura.util.AuraException
     */
    public Collection<ArtistTag> artistTagLookup(Collection<String> ids) throws AuraException {
        Collection<Item> items = getItems(ids);
        Collection<ArtistTag> results = new ArrayList<ArtistTag>();
        for (Item item : items) {
            typeCheck(item, ItemType.ARTIST_TAG);
            results.add(new ArtistTag(item));
        }

        return results;
    }

    public List<Scored<ArtistTag>> artistTagFindSimilar(String id, int count) throws AuraException {
        List<Scored<Item>> simItems = findSimilar(id, ArtistTag.FIELD_TAGGED_ARTISTS, count, ItemType.ARTIST_TAG);
        return convertToScoredArtistTagList(simItems);
    }

    public List<Scored<ArtistTag>> artistGetDistinctiveTags(String id, int count) throws AuraException {
        return artistGetDistinctiveTags(id, Artist.FIELD_SOCIAL_TAGS, count);
    }

    public WordCloud artistGetDistinctiveTagNames(
            String id, int count) throws AuraException {
        try {
            return getDataStore().getTopTerms(id, Artist.FIELD_SOCIAL_TAGS, count);
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    private List<Scored<ArtistTag>> artistGetDistinctiveTags(String id, String field, int count) throws AuraException {
        try {
            List<Scored<ArtistTag>> artistTags = new ArrayList();

            WordCloud tagNames = getDataStore().getTopTerms(id, field, count);
            for (Scored<String> scoredTagName : tagNames) {
                ArtistTag artistTag = artistTagLookup(ArtistTag.nameToKey(scoredTagName.getItem()));
                // not all tags may be in the database yet
                if (artistTag != null) {
                    artistTags.add(new Scored<ArtistTag>(artistTag, scoredTagName.getScore()));
                }

            }
            return artistTags;
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    public Album albumLookup(
            String albumID) throws AuraException {
        Item item = getItem(albumID);
        if (item != null) {
            typeCheck(item, ItemType.ALBUM);
            return new Album(item);
        }

        return null;
    }

    /**
     * Looks up a collection of albums by id
     * @param ids the collection of ids for the albums
     * @return the collection of artists
     * @throws com.sun.labs.aura.util.AuraException
     */
    public Collection<Album> albumLookup(Collection<String> ids) throws AuraException {
        Collection<Item> items = getItems(ids);
        Collection<Album> results = new ArrayList<Album>();
        for (Item item : items) {
            typeCheck(item, ItemType.ALBUM);
            results.add(new Album(item));
        }

        return results;
    }

    public Event eventLookup(
            String eventID) throws AuraException {
        Item item = getItem(eventID);
        if (item != null) {
            typeCheck(item, ItemType.EVENT);
            return new Event(item);
        }

        return null;
    }

    /**
     * Looks up a collection of events by id
     * @param ids the collection of ids for the events
     * @return the collection of events
     * @throws com.sun.labs.aura.util.AuraException
     */
    public Collection<Event> eventLookup(Collection<String> ids) throws AuraException {
        Collection<Item> items = getItems(ids);
        Collection<Event> results = new ArrayList<Event>();
        for (Item item : items) {
            typeCheck(item, ItemType.EVENT);
            results.add(new Event(item));
        }

        return results;
    }

    public Photo photoLookup(
            String photoID) throws AuraException {
        Item item = getItem(photoID);
        if (item != null) {
            typeCheck(item, ItemType.PHOTO);
            return new Photo(item);
        }

        return null;
    }

    /**
     * Looks up a collection of photos by id
     * @param ids the collection of ids for the photos
     * @return the collection of photos
     * @throws com.sun.labs.aura.util.AuraException
     */
    public Collection<Photo> photoLookup(Collection<String> ids) throws AuraException {
        Collection<Item> items = getItems(ids);
        Collection<Photo> results = new ArrayList<Photo>();
        for (Item item : items) {
            typeCheck(item, ItemType.PHOTO);
            results.add(new Photo(item));
        }

        return results;
    }

    public Track trackLookup(
            String trackID) throws AuraException {
        Item item = getItem(trackID);
        if (item != null) {
            typeCheck(item, ItemType.TRACK);
            return new Track(item);
        }

        return null;
    }

    /**
     * Looks up a collection of tracks by id
     * @param ids the collection of ids for the tracks
     * @return the collection of tracks
     * @throws com.sun.labs.aura.util.AuraException
     */
    public Collection<Track> trackLookup(Collection<String> ids) throws AuraException {
        Collection<Item> items = getItems(ids);
        Collection<Track> results = new ArrayList<Track>();
        for (Item item : items) {
            typeCheck(item, ItemType.TRACK);
            results.add(new Track(item));
        }

        return results;
    }

    public Video videoLookup(
            String videoID) throws AuraException {
        Item item = getItem(videoID);
        if (item != null) {
            typeCheck(item, ItemType.VIDEO);
            return new Video(item);
        }

        return null;
    }

    /**
     * Looks up a collection of Video by id
     * @param ids the collection of ids for the Video
     * @return the collection of Video
     * @throws com.sun.labs.aura.util.AuraException
     */
    public Collection<Video> videoLookup(Collection<String> ids) throws AuraException {
        Collection<Item> items = getItems(ids);
        Collection<Video> results = new ArrayList<Video>();
        for (Item item : items) {
            typeCheck(item, ItemType.VIDEO);
            results.add(new Video(item));
        }

        return results;
    }

    private Item getItem(String id) throws AuraException {
        try {
            return getDataStore().getItem(id);
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    private Collection<Item> getItems(Collection<String> ids) throws AuraException {
        try {
            return getDataStore().getItems(ids);
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    private List<Scored<Item>> query(String query, int count) throws AuraException {
        try {
            return getDataStore().query(query, "-score", count, null);
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    private void typeCheck(Item item, ItemType expected) throws AuraException {
        if (item.getType() != expected) {
            throw new AuraException("Mismatched item type, expected " + expected + ", found " + item.getType());
        }

    }

    private List<Scored<Artist>> convertToScoredArtistList(List<Scored<Item>> items) {
        List<Scored<Artist>> artistList = new ArrayList();
        for (Scored<Item> scoredItem : items) {
            artistList.add(new Scored<Artist>(new Artist(scoredItem.getItem()), scoredItem.getScore()));
        }

        return artistList;
    }

    private List<Scored<TagCloud>> convertToScoredTagCloudList(List<Scored<Item>> items) {
        List<Scored<TagCloud>> tagCloudList = new ArrayList();
        for (Scored<Item> scoredItem : items) {
            tagCloudList.add(new Scored<TagCloud>(new TagCloud(scoredItem.getItem()), scoredItem.getScore()));
        }

        return tagCloudList;
    }

    private List<Scored<Listener>> convertToScoredListenerList(List<Scored<Item>> items) {
        List<Scored<Listener>> listenerList = new ArrayList();
        for (Scored<Item> scoredItem : items) {
            listenerList.add(new Scored<Listener>(new Listener(scoredItem.getItem()), scoredItem.getScore()));
        }

        return listenerList;
    }

    private List<Scored<ArtistTag>> convertToScoredArtistTagList(List<Scored<Item>> items) {
        List<Scored<ArtistTag>> artistTagList = new ArrayList();
        for (Scored<Item> scoredItem : items) {
            artistTagList.add(new Scored<ArtistTag>(new ArtistTag(scoredItem.getItem()), scoredItem.getScore()));
        }

        return artistTagList;
    }

    private List<Scored<Item>> findSimilar(String id, int count, ItemType type) throws AuraException {
        try {
            List<Scored<Item>> simItems = getDataStore().findSimilar(id, getFindSimilarConfig(count, new TypeFilter(type)));
            return simItems;
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    private List<Scored<Item>> findSimilar(String id, int count, ItemType type, Popularity pop) throws AuraException {
        try {
            List<Scored<Item>> simItems = getDataStore().findSimilar(id, getFindSimilarConfig(count,
                    new PopularityAndTypeFilter(type, pop, getMostPopularArtist().getPopularity())));
            return simItems;
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    private List<Scored<Item>> findSimilar(String id, String field, int count, ItemType type) throws AuraException {
        try {
            List<Scored<Item>> simItems = getDataStore().findSimilar(id, getFindSimilarConfig(field, count, new TypeFilter(type)));
            return simItems;
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    private List<Scored<Item>> findSimilar(String id, String field, int count, ItemType type, Popularity pop) throws AuraException {
        try {
            List<Scored<Item>> simItems = getDataStore().findSimilar(id, getFindSimilarConfig(field, count,
                    new PopularityAndTypeFilter(type, pop, getMostPopularArtist().getPopularity())));
            return simItems;
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    private List<Scored<Item>> findSimilar(WordCloud wc, String field, int count, ItemType type) throws AuraException {
        try {
            List<Scored<Item>> simItems = getDataStore().findSimilar(wc, getFindSimilarConfig(field, count, new TypeFilter(type)));
            return simItems;
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }

    }

    private List<Scored<Item>> findSimilar(WordCloud wc, String field, int count, ItemType type, Popularity pop) throws AuraException {
        try {
            List<Scored<Item>> simItems = getDataStore().findSimilar(wc, getFindSimilarConfig(field, count,
                    new PopularityAndTypeFilter(type, pop, getMostPopularArtist().getPopularity())));
            return simItems;
        } catch (RemoteException ex) {
            throw new AuraException("Can't talk to the datastore " + ex, ex);
        }











    }

    private class FieldSimType
            implements SimType {

        private String name;
        private String description;
        private String field;

        FieldSimType(String name, String description, String fieldName) {
            this.name = name;
            this.description = description;
            this.field = fieldName;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public List<Scored<Artist>> findSimilarArtists(String artistID, int count) throws AuraException {
            return artistFindSimilar(artistID, field, count);
        }

        public List<Scored<Artist>> findSimilarArtists(String artistID, int count,
                MusicDatabase.Popularity pop) throws AuraException {
            return artistFindSimilar(artistID, field, count, pop);
        }

        public List<Scored<String>> explainSimilarity(String id1, String id2, int count) throws AuraException {
            try {
                return getDataStore().explainSimilarity(id1, id2, new SimilarityConfig(field, count));
            } catch (RemoteException ex) {
                throw new AuraException("Can't talk to the datastore " + ex, ex);
            }
        }

        private List<Scored<ArtistTag>> getDistinctiveTags(String id, int count) throws AuraException {
            return artistGetDistinctiveTags(id, field, count);
        }
    }

    private class AllSimType implements SimType {

        private String name;
        private String description;

        AllSimType() {
            this.name = "All";
            this.description = "Artist similarity based upon all fields";
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public List<Scored<Artist>> findSimilarArtists(String artistID, int count) throws AuraException {
            List<Scored<Item>> simItems = findSimilar(artistID, count, ItemType.ARTIST);
            return convertToScoredArtistList(simItems);
        }

        public List<Scored<Artist>> findSimilarArtists(String artistID, int count,
                MusicDatabase.Popularity pop) throws AuraException {
            List<Scored<Item>> simItems = findSimilar(artistID, count, ItemType.ARTIST, pop);
            return convertToScoredArtistList(simItems);
        }

        public List<Scored<String>> explainSimilarity(String artistID1, String artistID2, int count) throws AuraException {
            return artistExplainSimilarity(artistID1, artistID2, count);
        }
    }

    private class SimpleArtistRecommendationType implements RecommendationType {

        public String getName() {
            return "SimpleArtist";
        }

        public String getDescription() {
            return "a simple recommender that just returns artists that are similar to " +
                    " a single artist, selected at random, that the listener likes";
        }

        public RecommendationSummary getRecommendations(String listenerID, int count, RecommendationProfile rp)
                throws AuraException, RemoteException {
            Set<String> skipIDS = getAttendedToArtists(listenerID);
            Artist artist = getRandomGoodArtistFromListener(listenerID);
            List<Recommendation> results = new ArrayList();
            List<Scored<Artist>> simArtists = artistFindSimilar(artist.getKey(), count * 5);
            for (Scored<Artist> sartist : simArtists) {
                if (!skipIDS.contains(sartist.getItem().getKey())) {
                    skipIDS.add(sartist.getItem().getKey());
                    List<Scored<String>> reason = artistExplainSimilarity(artist.getKey(), sartist.getItem().getKey(), 20);
                    results.add(new Recommendation(sartist.getItem().getKey(),
                            sartist.getScore(), reason));
                    if (results.size() >= count) {
                        break;
                    }
                }
            }
            String reason = "Artists similarity to " + artist.getName();
            return new RecommendationSummary(reason, results);
        }

        public ItemType getType() {
            return ItemType.ARTIST;
        }
    }

    private class SimToRecentArtistRecommender implements RecommendationType {

        public String getName() {
            return "SimToRecent";
        }

        public String getDescription() {
            return "Finds artists that are similar to the recently played artists";
        }

        public RecommendationSummary getRecommendations(String listenerID, int count, RecommendationProfile rp)
                throws AuraException, RemoteException {
            ArtistScoreManager sm = new ArtistScoreManager(true);
            List<Scored<String>> artistIDs = getRecentTopArtistsAsIDs(listenerID, 20);
            Set<String> skipIDS = getAttendedToArtists(listenerID);
            StringBuilder sb = new StringBuilder();

            sb.append("Similar to recently played artists like ");

            Collection<String> ids = getIDs(artistIDs);
            for (Artist artist : artistLookup(ids)) {
                if (artist != null) {
                    sb.append(artist.getName());
                    sb.append(",");
                    sm.addSeedArtist(artist);
                    List<Scored<Artist>> simArtists = artistFindSimilar(artist.getKey(), count * 3);
                    for (Scored<Artist> simArtist : simArtists) {
                        if (!skipIDS.contains(simArtist.getItem().getKey())) {
                            // BUG: this should  include the score of the seed artist.
                            sm.accum(simArtist.getItem(), artist.getKey(), 1.0 * simArtist.getScore());
                        }
                    }
                }
            }

            List<Recommendation> results = new ArrayList();
            for (Scored<Artist> sartist : sm.getTop(count)) {
                List<Scored<String>> thisReason = sm.getReason(sartist.getItem());
                results.add(new Recommendation(sartist.getItem().getKey(), sartist.getScore(), thisReason));
            }
            return new RecommendationSummary(sb.toString(), results);
        }

        public ItemType getType() {
            return ItemType.ARTIST;
        }
    }

    public void setSkimPercent(double skimPercent) {
        this.skimPercent = skimPercent;
    }

    private List<String> getIDs(List<Scored<String>> scoredIds) {
        List<String> results = new ArrayList<String>();
        for (Scored<String> ss : scoredIds) {
            results.add(ss.getItem());
        }

        return results;
    }

    private SimilarityConfig getFindSimilarConfig(String field, int count, ResultsFilter filter) {
        SimilarityConfig fsc = new SimilarityConfig(field, count, filter);
        fsc.setSkimPercent(skimPercent);
        fsc.setReportPercent(1.);
        return fsc;
    }

    private SimilarityConfig getFindSimilarConfig(int count, ResultsFilter filter) {
        SimilarityConfig fsc = new SimilarityConfig(count, filter);
        fsc.setSkimPercent(skimPercent);
        fsc.setReportPercent(1.);
        return fsc;
    }

    private List<Recommendation> getSimilarArtists(List<Scored<String>> seedArtists, Set<String> skipArtists, int count)
            throws AuraException {
        List<Recommendation> recommendations = new ArrayList();
        for (Scored<String> seedArtist : seedArtists) {
            List<Scored<Artist>> simArtists = artistFindSimilar(seedArtist.getItem(), count);
            for (Scored<Artist> sa : simArtists) {
                if (!skipArtists.contains(sa.getItem().getKey())) {
                    skipArtists.add(sa.getItem().getKey());
                    List<Scored<String>> reason = new ArrayList();
                    reason.add(new Scored<String>(seedArtist.getItem(), sa.getScore()));
                    recommendations.add(new Recommendation(sa.getItem().getKey(), sa.getScore(), reason));
                    if (recommendations.size() >= count) {
                        break;
                    }

                }
            }
        }
        return recommendations;
    }

    /**
     * Converts the string to a Popularity
     * @param s the string
     * @return the popularity or none if none can be found
     */
    public Popularity toPopularity(
            String s) {
        for (Popularity p : Popularity.values()) {
            if (p.name().equalsIgnoreCase(s)) {
                return p;
            }

        }
        return null;
    }

    private class SimToRecentArtistRecommender2 implements RecommendationType {

        public String getName() {
            return "SimToRecent(2)";
        }

        public String getDescription() {
            return "Finds artists that are similar to the recently played artists (version 2)";
        }

        public RecommendationSummary getRecommendations(String listenerID, int count, RecommendationProfile rp)
                throws AuraException, RemoteException {
            List<Scored<String>> artistIDs = getRecentTopArtistsAsIDs(listenerID, count / 2);
            Set<String> skipIDS = getAttendedToArtists(listenerID);
            List<Recommendation> recommendations = getSimilarArtists(artistIDs, skipIDS, count);
            return new RecommendationSummary("similarity to recent artists", recommendations);
        }

        public ItemType getType() {
            return ItemType.ARTIST;
        }
    }

    private class SimToUserTagCloud implements RecommendationType {

        public String getName() {
            return "SimToUserTagCloud";
        }

        public String getDescription() {
            return "Finds artists that are similar to users tag cloud";
        }

        public RecommendationSummary getRecommendations(String listenerID, int count, RecommendationProfile rp)
                throws AuraException, RemoteException {
            List<Scored<Item>> items = getDataStore().findSimilar(listenerID,
                    getFindSimilarConfig(Listener.FIELD_SOCIAL_TAGS,
                    count * 5, new TypeFilter(ItemType.ARTIST)));
            List<Recommendation> results = new ArrayList();
            Set<String> skipIDS = getAttendedToArtists(listenerID);

            for (Scored<Item> item : items) {
                if (!skipIDS.contains(item.getItem().getKey())) {
                    List<Scored<String>> reason = getDataStore().explainSimilarity(listenerID,
                            item.getItem().getKey(), new SimilarityConfig(Listener.FIELD_SOCIAL_TAGS, count));
                    results.add(new Recommendation(item.getItem().getKey(), item.getScore(), reason));
                    if (results.size() >= count) {
                        break;
                    }
                }
            }
            return new RecommendationSummary("Similarity to your personal tag cloud", results);
        }

        public ItemType getType() {
            return ItemType.ARTIST;
        }
    }

    private class CollaborativeFilterer implements RecommendationType {

        private final static int MAX_LISTENERS = 10;

        public String getName() {
            return "CollaborativeFilterer";
        }

        public String getDescription() {
            return "Finds favorites artists from similar users";
        }

        public RecommendationSummary getRecommendations(String listenerID, int count, RecommendationProfile rp)
                throws AuraException, RemoteException {

            Set<String> skipIDs = getAttendedToArtists(listenerID);
            List<Scored<Listener>> simListeners = listenerFindSimilar(listenerID, MAX_LISTENERS);
            ArtistScoreManager sm = new ArtistScoreManager(true);
            for (Scored<Listener> sl : simListeners) {
                if (sl.getItem().getKey().equals(listenerID)) {
                    continue;
                }
                List<Scored<String>> artistIDs = getRecentTopArtistsAsIDs(sl.getItem().getKey(), count * 5);
                for (Scored<String> sartistID : artistIDs) {
                    if (!skipIDs.contains(sartistID.getItem())) {
                        // System.out.println("id " + sartistID.getItem());
                        Artist artist = artistLookup(sartistID.getItem());
                        if (artist != null) {
                            sm.accum(artist, sartistID.getItem(), sl.getScore() * sartistID.getScore());
                        }
                    }
                }
            }

            List<Recommendation> results = new ArrayList();
            for (Scored<Artist> sartist : sm.getTop(count)) {
                List<Scored<String>> thisReason = sm.getReason(sartist.getItem());
                results.add(new Recommendation(sartist.getItem().getKey(), sartist.getScore(), thisReason));
            }
            return new RecommendationSummary("Favorite Artists from similar listeners", results);
        }

        public ItemType getType() {
            return ItemType.ARTIST;
        }
    }
}
