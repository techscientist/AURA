/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.labs.aura.music.crawler;

import com.sun.labs.aura.AuraService;
import com.sun.labs.aura.datastore.DataStore;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.Item.ItemType;
import com.sun.labs.aura.datastore.StoreFactory;
import com.sun.labs.aura.music.Album;
import com.sun.labs.aura.music.Artist;
import com.sun.labs.aura.music.ArtistTag;
import com.sun.labs.aura.music.Event;
import com.sun.labs.aura.music.Photo;
import com.sun.labs.aura.music.Video;
import com.sun.labs.aura.music.util.CommandRunner;
import com.sun.labs.aura.music.util.Commander;
import com.sun.labs.aura.music.web.flickr.FlickrManager;
import com.sun.labs.aura.music.web.lastfm.LastArtist;
import com.sun.labs.aura.music.web.lastfm.LastFM;
import com.sun.labs.aura.music.web.lastfm.SocialTag;
import com.sun.labs.aura.music.web.musicbrainz.MusicBrainz;
import com.sun.labs.aura.music.web.musicbrainz.MusicBrainzAlbumInfo;
import com.sun.labs.aura.music.web.musicbrainz.MusicBrainzArtistInfo;
import com.sun.labs.aura.music.web.spotify.Spotify;
import com.sun.labs.aura.music.web.upcoming.UpcomingEvent;
import com.sun.labs.aura.music.web.upcoming.Upcoming;
import com.sun.labs.aura.music.web.wikipedia.WikiInfo;
import com.sun.labs.aura.music.web.wikipedia.Wikipedia;
import com.sun.labs.aura.music.web.youtube.Youtube;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.util.props.ConfigBoolean;
import com.sun.labs.util.props.ConfigComponent;
import com.sun.labs.util.props.ConfigInteger;
import com.sun.labs.util.props.ConfigString;
import com.sun.labs.util.props.Configurable;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * @author plamere
 */
public class ArtistCrawler implements AuraService, Configurable {

    private LastFM lastFM;
    private MusicBrainz musicBrainz;
    private Wikipedia wikipedia;
    private Youtube youtube;
    private FlickrManager flickr;
    private Upcoming upcoming;
    private Spotify spotify;
    private PriorityQueue<QueuedArtist> artistQueue;
    private Logger logger;
    private Util util;
    private final static String CRAWLER_STATE_FILE = "crawler.state";
    private final static int FLUSH_COUNT = 10;
    private boolean running = false;
    private final static int MAX_FAN_OUT = 5;
    private Set<String> validTags = new HashSet();

    /**
     * Starts running the crawler
     */
    public void start() {
        if (!running) {
            running = true;
            {
                Thread t = new Thread() {

                    @Override
                    public void run() {
                        discoverArtists();
                    }
                };
                t.start();
            }

            {
                Thread t = new Thread() {

                    @Override
                    public void run() {
                        artistUpdater();
                    }
                };
                t.start();
            }
        }
    }

    /**
     * Stops the crawler
     */
    public void stop() {
        running = false;
    }

    public void newProperties(PropertySheet ps) throws PropertyException {
        try {
            logger = ps.getLogger();
            lastFM = new LastFM();
            musicBrainz = new MusicBrainz();
            wikipedia = new Wikipedia();
            youtube = new Youtube();
            flickr = new FlickrManager();
            upcoming = new Upcoming();
            spotify = new Spotify();
            artistQueue = new PriorityQueue(1000, QueuedArtist.PRIORITY_ORDER);
            dataStore = (DataStore) ps.getComponent(PROP_DATA_STORE);
            stateDir = ps.getString(PROP_STATE_DIR);
            filterTags = ps.getBoolean(PROP_FILTER_TAGS);
            updateRateInSeconds = ps.getInt(PROP_UPDATE_RATE);
            util = new Util(dataStore, flickr, youtube);
            createStateFileDirectory();
            loadState();

            if (filterTags) {
                loadTagFilter();
            }
            logger.info("tag filter: " + validTags.size() + " tags");
        } catch (IOException ioe) {
            throw new PropertyException(ioe, "ArtistCrawler", ps.getInstanceName(), "");
        }
    }

    /**
     * Starts discovering artists. When a new artist is encountered it is 
     * added to the datastore
     */
    private void discoverArtists() {
        long lastTime = 0L;
        int count = 0;
        try {
            primeArtistQueue("The Beatles");
        /*
        primeArtistQueue("Radiohead");
        primeArtistQueue("Miles Davis");
        primeArtistQueue("Britney Spears");
         */
        } catch (AuraException ae) {
            logger.severe("ArtistCrawler Can't talk to the datastore, abandoning crawl");
            return;
        }

        while (running && artistQueue.size() > 0) {
            try {
                QueuedArtist queuedArtist = artistQueue.poll();
                long curTime = System.currentTimeMillis();
                logger.info("Crawling " + queuedArtist + " remaining " + artistQueue.size() + " time " + (curTime - lastTime) + " ms");
                lastTime = curTime;

                Artist artist = collectArtistInfo(queuedArtist);
                if (artist != null) {
                    artist.flush(dataStore);
                    if (count++ % FLUSH_COUNT == 0) {
                        saveState();
                    }
                }
            } catch (AuraException ex) {
                logger.warning("Aura Trouble during crawl " + ex);
            } catch (RemoteException ex) {
                logger.warning("Remote Trouble during crawl " + ex);
            } catch (IOException ex) {
                logger.warning("IO Trouble during crawl " + ex);
            }
        }
    }

    private void updateArtists(boolean force, long period) throws AuraException, RemoteException, InterruptedException {
        List<Item> items = dataStore.getAll(Item.ItemType.ARTIST);
        List<Artist> artists = new ArrayList<Artist>();
        for (Item i : items) {
            artists.add(new Artist(i));
        }
        Collections.sort(artists, Artist.POPULARITY);
        Collections.reverse(artists);
        for (Artist artist : artists) {
            if (force || needsUpdate(artist)) {
                logger.info("Updating " + artist.getName());
                updateArtist(artist);
                try {
                    Thread.sleep(period);
                } catch (InterruptedException e) {
                    break;
                }
            } else {
                logger.info(artist.getName() + " is upto date");
            }
        }
    }

    private void artistUpdater() {
        try {
            while (running) {
                long startTime = System.currentTimeMillis();
                updateArtists(false, 5000L);
                long delta = System.currentTimeMillis() - startTime;
                long delay = updateRateInSeconds * 1000L  - delta;
                if (delay > 0L) {
                    logger.info("ArtistUpdater waiting " + (delay / (1000. * 60 * 60)) + " hours.");
                    Thread.sleep(delay);
                }
            }
        } catch (AuraException ex) {
            logger.warning("trouble in artist updater, shutting down " + ex);
        } catch (RemoteException ex) {
            logger.warning("trouble in artist updater, shutting down " + ex);
        } catch (InterruptedException ex) {
            logger.info("artist updater, interrupted, shutting down");
        }
    }

    private boolean needsUpdate(Artist artist) {
        return (System.currentTimeMillis() - artist.getTimeAdded()) > updateRateInSeconds * 1000L;
    }

    private boolean worthVisiting(LastArtist lartist) throws AuraException, RemoteException {
        if (lartist.getMbaid() == null || lartist.getMbaid().length() == 0) {
            return false;
        }

        if (inArtistQueue(lartist.getMbaid())) {
            return false;
        }

        if (dataStore.getItem(lartist.getMbaid()) != null) {
            return false;
        }
        return true;
    }

    private boolean inArtistQueue(String mbaid) {
        for (QueuedArtist qartist : artistQueue) {
            if (qartist.getMBaid().equals(mbaid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects the information for an artist
     * @param queuedArtist the artist of interest
     * @return a fully populated artist
     * @throws com.sun.labs.aura.util.AuraException if a problem with the datastore is encountered
     * @throws java.rmi.RemoteException if a communicatin error occurs
     */
    Artist collectArtistInfo(QueuedArtist queuedArtist) throws AuraException, RemoteException, IOException {
        String mbaid = queuedArtist.getMBaid();
        if (mbaid != null && mbaid.length() > 0) {
            Item item = dataStore.getItem(mbaid);
            if (item == null) {

                item = StoreFactory.newItem(ItemType.ARTIST, mbaid, queuedArtist.getArtistName());
                final Artist artist = new Artist(item);
                artist.setPopularity(queuedArtist.getPopularity());
                updateArtist(artist);
                return artist;
            }
        }
        return null;
    }

    private void updateArtist(final Artist artist) {
        CommandRunner runner = new CommandRunner(false, logger.isLoggable(Level.INFO));
        runner.add(new Commander("last.fm") {

            @Override
            public void go() throws Exception {
                addLastFmTags(artist);
                addSimilarArtistsToQueue(artist);
            }
        });

        runner.add(new Commander("musicbrainz+wikipedia") {

            @Override
            public void go() throws Exception {
                addMusicBrainzInfo(artist);
                addWikipediaInfo(artist);
            }
        });

        runner.add(new Commander("flickr") {

            @Override
            public void go() throws Exception {
                addFlickrPhotos(artist);
            }
        });

        runner.add(new Commander("spotify") {

            @Override
            public void go() throws Exception {
                addSpotifyInfo(artist);
            }
        });

        runner.add(new Commander("youtube") {

            @Override
            public void go() throws Exception {
                addYoutubeVideos(artist);
            }
        });

        runner.add(new Commander("upcoming") {

            @Override
            public void go() throws Exception {
                addUpcomingInfo(artist);
            }
        });

        try {
            runner.go();
        } catch (Exception e) {
            // if we get an exception when we are crawling, 
            // we still have some good data, so log the problem
            // but still return the artist so we can add it to the store
            logger.warning("Exception " + e);
        }
    }

    /**
     * Creates the directory for the state file if necessary
     */
    private void createStateFileDirectory() throws IOException {
        File stateDirFile = new File(stateDir);
        if (!stateDirFile.exists()) {
            if (!stateDirFile.mkdirs()) {
                throw new IOException("Can't create state file directory");
            }
        }
    }

    /**
     * Primes the artist queue for discovery. The queue is primed with artists that
     * are similar to the beatles
     */
    private void primeArtistQueue(String artistName) throws AuraException {
        try {
            logger.info("Priming queue with " + artistName);
            LastArtist[] simArtists = lastFM.getSimilarArtists(artistName);
            for (LastArtist simArtist : simArtists) {
                if (worthVisiting(simArtist)) {
                    int popularity = lastFM.getPopularity(simArtist.getArtistName());
                    logger.info("  adding  " + simArtist.getArtistName() + " pop: " + popularity);
                    enqueue(simArtist, popularity);
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(ArtistCrawler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Adds an artist to the discovery queue
     * @param artist the artist to be queued
     * @param popularity the popularity of the artist
     */
    private void enqueue(LastArtist artist, int popularity) {
        artistQueue.add(new QueuedArtist(artist, popularity));
    }

    /**
     * Loads the previously saveed priority queue
     */
    private void loadState() {


        ObjectInputStream ois = null;
        try {
            File stateFile = new File(stateDir, CRAWLER_STATE_FILE);
            FileInputStream fis = new FileInputStream(stateFile);
            ois = new ObjectInputStream(fis);
            ArtistCrawlerState newState = (ArtistCrawlerState) ois.readObject();

            if (newState != null) {
                artistQueue.clear();
                artistQueue.addAll(newState.getArtistQueue());
                logger.info("restored discovery queue with " + artistQueue.size() + " entries");
            }
        } catch (IOException ex) {
            // no worries if there was no file
        } catch (ClassNotFoundException ex) {
            logger.warning("Bad format in feedscheduler statefile " + ex);
        } finally {
            try {
                if (ois != null) {
                    ois.close();
                }
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Saves the priority queue to a file.
     */
    private void saveState() {
        FileOutputStream fos = null;
        try {
            ArtistCrawlerState acs = new ArtistCrawlerState(new ArrayList(artistQueue));
            File stateFile = new File(stateDir, CRAWLER_STATE_FILE);
            fos = new FileOutputStream(stateFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(acs);
            oos.close();
        } catch (IOException ex) {
            logger.warning("Can't save the state of the crawler " + ex);
        } finally {
            try {
                if (fos != null) {
                    fos.close();
                }
            } catch (IOException ex) {
            }
        }
    }

    /**
     * Adds musicbrainz info to the aritst
     * @param artist the artist to be augmented
     * @throws com.sun.labs.aura.util.AuraException if a datastore error occurs
     */
    private void addMusicBrainzInfo(Artist artist) throws AuraException, RemoteException, IOException {
        MusicBrainzArtistInfo mbai = musicBrainz.getArtistInfo(artist.getKey());
        artist.setBeginYear(mbai.getBeginYear());
        artist.setEndYear(mbai.getEndYear());

        for (String name : mbai.getURLMap().keySet()) {
            artist.addUrl(name, mbai.getURLMap().get(name));
        }

        for (MusicBrainzAlbumInfo mbalbum : mbai.getAlbums()) {
            String mbrid = mbalbum.getId();
            Item albumItem = dataStore.getItem(mbrid);
            if (albumItem == null) {
                albumItem = StoreFactory.newItem(ItemType.ALBUM, mbrid, mbalbum.getTitle());
                Album album = new Album(albumItem);
                album.setAsin(mbalbum.getAsin());
                album.flush(dataStore);
            }
            artist.addAlbum(mbrid);
        }

        for (String id : mbai.getCollaborators()) {
            artist.addRelatedArtist(id);
        }

    }

    private void addSpotifyInfo(Artist artist) throws IOException {
        String id = spotify.getSpotifyIDforArtist(artist.getName());
        artist.setSpotifyID(id);
    }

    /**
     * Adds wikipedia info to the artist
     * @param artist the artist of interest
     */
    private void addWikipediaInfo(Artist artist) throws IOException {
        String query = (String) artist.getUrls().get("Wikipedia");
        if (query == null) {
            query = artist.getName();
        }
        WikiInfo wikiInfo = wikipedia.getWikiInfo(query);
        artist.setBioSummary(wikiInfo.getSummary());
        addBioTags(artist, wikiInfo.getFullText());
    }

    private void addBioTags(Artist artist, String description) {
        String words = normalizeText(" " + description + " ");
        for (String tag : validTags) {
            int count = findMatches(tag, words);
            if (count > 0) {
                artist.addBioTag(tag, count);
                // logger.info("Adding " + tag + ":" + count);
            }
        }
    }

    private String normalizeText(String s) {
        s = s.replaceAll("[^\\w\\s]", " ").toLowerCase();
        s = s.replaceAll("[\\s]+", " ");
        return s;
    }

    private int findMatches(String tag, String text) {
        Pattern p = Pattern.compile("\\s" + tag + "\\s");
        Matcher m = p.matcher(text);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private void addYoutubeVideos(Artist artist) throws AuraException, RemoteException, IOException {
        List<Video> videos = util.collectYoutubeVideos(artist.getName(), 24);
        for (Video video : videos) {
            artist.addVideo(video.getKey());
        }
    }

    private void addFlickrPhotos(Artist artist) throws AuraException, RemoteException, IOException {
        List<Photo> photos = util.collectFlickrPhotos(artist.getName(), 24);
        for (Photo photo : photos) {
            artist.addPhoto(photo.getKey());
        }
    }

    private void addLastFmTags(Artist artist) throws AuraException, RemoteException, IOException {
        SocialTag[] tags = lastFM.getArtistTags(artist.getName());
        for (SocialTag tag : tags) {
            if (isValidTag(tag)) {
                 int normFreq = (tag.getFreq() + 1) * (tag.getFreq() + 1);
                artist.addSocialTag(tag.getName(), normFreq);
            }
        }
    }

    private boolean isValidTag(SocialTag tag) {
        if (filterTags) {
            return validTags.contains(ArtistTag.normalizeName(tag.getName()));
        } else {
            return true;
        }
    }

    private void addSimilarArtistsToQueue(Artist artist) throws AuraException, RemoteException, IOException {
        LastArtist[] simArtists = lastFM.getSimilarArtists(artist.getName());
        int fanOut = 0;
        for (LastArtist simArtist : simArtists) {
            if (worthVisiting(simArtist)) {
                int popularity = lastFM.getPopularity(simArtist.getArtistName());
                enqueue(simArtist, popularity);
                if (fanOut++ >= MAX_FAN_OUT) {
                    break;
                }
            }
        }
    }

    private void addUpcomingInfo(Artist artist) throws AuraException, RemoteException, IOException {
        int count = 0;
        int MAX_EVENTS = 5;
        List<UpcomingEvent> events = upcoming.searchEventsByArtist(artist.getName());
        for (UpcomingEvent event : events) {
            Item item = StoreFactory.newItem(ItemType.EVENT, event.getEventID(), event.getName());
            Event itemEvent = new Event(item);
            itemEvent.setName(event.getName());
            itemEvent.setDate(event.getDate());
            itemEvent.setVenueName(event.getVenue());
            itemEvent.flush(dataStore);
            artist.addEvent(itemEvent.getKey());
            if (count++ >= MAX_EVENTS) {
                break;
            }
        }
    }

    private void loadTagFilter() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(ArtistCrawler.class.getResourceAsStream("taglist.txt")));
            String line;
            try {
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String tag = ArtistTag.normalizeName(line);
                    validTags.add(tag);
                    validTags.add(normalizeText(line));
                }
            } finally {
                in.close();
            }
        } catch (IOException ioe) {
            logger.warning("Couldn't read the tagfilter list");
        }
    }
    /**
     * the configurable property for the itemstore used by this manager
     */
    @ConfigComponent(type = DataStore.class)
    public final static String PROP_DATA_STORE = "dataStore";
    private DataStore dataStore;
    /** the directory for the crawler.state */
    @ConfigString(defaultValue = "artistCrawler")
    public final static String PROP_STATE_DIR = "crawlerStateDir";
    private String stateDir;
    @ConfigBoolean(defaultValue = true)
    public final static String PROP_FILTER_TAGS = "filterTags";
    private boolean filterTags;
    @ConfigInteger(defaultValue = 60 * 60 * 24 * 7 * 2)
    public final static String PROP_UPDATE_RATE = "updateRateInSeconds";
    private int updateRateInSeconds;
}

/**
 * Represents an artist in the queue. The queue is sorted by inverse popularity
 * (highly popular artists move to the head of the queue).
 */
class QueuedArtist implements Serializable {

    public final static Comparator<QueuedArtist> PRIORITY_ORDER = new Comparator<QueuedArtist>() {

        public int compare(QueuedArtist o1, QueuedArtist o2) {
            return o1.getPriority() - o2.getPriority();
        }
    };
    private LastArtist lastArtist;
    private int popularity;

    public QueuedArtist() {
    }

    /**
     * Creates a QueuedArtist
     * @param artist the artist of interest
     * @param popularity the artist popularity
     */
    public QueuedArtist(LastArtist artist, int popularity) {
        lastArtist = artist;
        this.popularity = popularity;
    }

    /**
     * Gets the artist name
     * @return the artist name
     */
    public String getArtistName() {
        return lastArtist.getArtistName();
    }

    /**
     * Gets the musicbrainz ID for the artist
     * @return the MB ID for the artist
     */
    public String getMBaid() {
        return lastArtist.getMbaid();
    }

    /**
     * Gets the priority for this queued artist
     * @return the priority
     */
    public int getPriority() {
        return -popularity;
    }

    /**
     * Gets the popularity for this artist
     * @return the popularity (higher is more popular)
     */
    public int getPopularity() {
        return popularity;
    }

    @Override
    public String toString() {
        return getPopularity() + "/" + getArtistName();
    }
}

/**
 * Represents the state of the crawler so it can be easily saved
 * and restored
 */
class ArtistCrawlerState implements Serializable {

    private List<QueuedArtist> artistQueue;

    /**
     * Creates the state
     * @param visited the set of visited artist names
     * @param queue the queue of artists to be visited
     */
    ArtistCrawlerState(List<QueuedArtist> queue) {
        artistQueue = queue;
    }

    /**
     * Gets the artist queue
     * @return the artist queue
     */
    List<QueuedArtist> getArtistQueue() {
        return artistQueue;
    }
}