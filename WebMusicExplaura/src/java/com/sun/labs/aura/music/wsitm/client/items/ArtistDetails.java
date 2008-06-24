/*
 * ArtistDetails.java
 *
 * Created on March 30, 2007, 11:03 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.labs.aura.music.wsitm.client.items;

import com.google.gwt.user.client.rpc.IsSerializable;
import com.google.gwt.user.client.ui.Image;
import com.sun.labs.aura.music.wsitm.client.WebLib;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author plamere
 */
public class ArtistDetails implements IsSerializable, Details {
    private final static ItemInfo[] EMPTY_ITEM_INFO = new ItemInfo[0];
    private final static ArtistVideo[] EMPTY_ARTIST_VIDEO = new ArtistVideo[0];
    private final static ArtistPhoto[] EMPTY_ARTIST_PHOTO = new ArtistPhoto[0];
    private final static AlbumDetails[] EMPTY_ALBUM = new AlbumDetails[0];
    private final static ArtistEvent[] EMPTY_EVENT = new ArtistEvent[0];
    
    private String status;
    private String id;
    private String name;
    private String encodedName;
    private String spotifyID;
    private float popularity;
    private float normPopularity;
    private int beginYear;
    private int endYear;
    /**
     *  @gwt.typeArgs <java.lang.String, java.lang.String>
     */
    private Map urls = new HashMap();
    private String biographySummary = "None available";
    private String imageURL;
    private String musicURL;
    private ItemInfo[] similarArtists = EMPTY_ITEM_INFO;
    private ItemInfo[] recommendedArtists = EMPTY_ITEM_INFO;
    private ItemInfo[] frequentTags = EMPTY_ITEM_INFO;
    private ItemInfo[] distinctiveTags = EMPTY_ITEM_INFO;
    private ItemInfo[] collaborations = EMPTY_ITEM_INFO;
    private ArtistVideo[] videos = EMPTY_ARTIST_VIDEO;
    private ArtistPhoto[] photos  = EMPTY_ARTIST_PHOTO;
    private AlbumDetails[] albums = EMPTY_ALBUM;
    private ArtistEvent[] events = EMPTY_EVENT;
  
        
    /**
     * Creates a new instance of ArtistDetails
     */
    public ArtistDetails() {
        setStatus("OK");
    }
    
    public boolean isOK() {
        return getStatus().equals("OK");
    }

    public String getId() {
        return id;
    }

    public  void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ArtistVideo[] getVideos() {
        return videos;
    }

    public void setVideos(ArtistVideo[] videos) {
        this.videos = videos;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public ItemInfo[] getSimilarArtists() {
        return similarArtists;
    }

    public void setSimilarArtists(ItemInfo[] similarArtists) {
        this.similarArtists = similarArtists;
    }
    
    public String toString() {
       return getName(); 
    }

    public int getBeginYear() {
        return beginYear;
    }

    public void setBeginYear(int beginYear) {
        this.beginYear = beginYear;
    }

    public int getEndYear() {
        return endYear;
    }

    public void setEndYear(int endYear) {
        this.endYear = endYear;
    }

    public Map getUrls() {
        return urls;
    }

    public void setUrls(Map urls) {
        this.urls = urls;
    }

    public String getBiographySummary() {
        return biographySummary;
    }

    public void setBiographySummary(String biographySummary) {
        this.biographySummary = biographySummary;
    }

    public String getImageURL() {
        return imageURL;
    }

    public void setImageURL(String imageURL) {
        this.imageURL = imageURL;
    }

    public String getMusicURL() {
        fixupMusicURL();
        return musicURL;
    }

    // one crawl tacked on the '?play' command, we don't
    // want it so, lets strip it off.
    private void fixupMusicURL() {
        if (musicURL != null) {
            if (musicURL.endsWith("?play")) {
                int last = musicURL.lastIndexOf("?play");
                musicURL = musicURL.substring(0, last);
            }
        }
    }

    public void setMusicURL(String musicURL) {
        this.musicURL = musicURL;
    }

    public ArtistPhoto[] getArtistPhotos() {
        return getPhotos();
    }

    public void setArtistPhotos(ArtistPhoto[] photos) {
        this.setPhotos(photos);
    }

    public AlbumDetails[] getAlbums() {
        return albums;
    }

    public void setAlbums(AlbumDetails[] albums) {
        this.albums = albums;
    }

    public ArtistEvent[] getEvents() {
        return events;
    }

    public void setEvents(ArtistEvent[] events) {
        this.events = events;
    }
    
    public boolean isActive() {
        return getEndYear() == 0;
    }

    public float getPopularity() {
        return popularity;
    }

    public void setPopularity(float popularity) {
        this.popularity = popularity;
    }
    
    public float getNormPopularity() {
        return normPopularity;
    }

    public void setNormPopularity(float normPopularity) {
        this.normPopularity = normPopularity;
    }    

    public ItemInfo[] getRecommendedArtists() {
        return recommendedArtists;
    }

    public void setRecommendedArtists(ItemInfo[] recommendedArtists) {
        this.recommendedArtists = recommendedArtists;
    }

    public ItemInfo[] getFrequentTags() {
        return frequentTags;
    }

    public void setFrequentTags(ItemInfo[] frequentTags) {
        this.frequentTags = frequentTags;
    }

    public ItemInfo[] getDistinctiveTags() {
        return distinctiveTags;
    }

    public void setDistinctiveTags(ItemInfo[] distinctiveTags) {
        this.distinctiveTags = distinctiveTags;
    }

    public ArtistPhoto[] getPhotos() {
        return photos;
    }

    public void setPhotos(ArtistPhoto[] photos) {
        this.photos = photos;
    }
    
    /**
     * Ensures proper conditiosn (ie no null arrays)
     */
            
    public void fixup() {
        if (similarArtists == null) {
            similarArtists = EMPTY_ITEM_INFO;
        }
        if (recommendedArtists == null) {
            recommendedArtists = EMPTY_ITEM_INFO;
        }
        
        if (frequentTags == null) {
            frequentTags = EMPTY_ITEM_INFO;
        }
        
        if (distinctiveTags == null) {
            distinctiveTags = EMPTY_ITEM_INFO;
        }
        if (videos == null) {
            videos = EMPTY_ARTIST_VIDEO;
        }
        if (photos == null) {
            photos = EMPTY_ARTIST_PHOTO;
        }
        if (albums == null) {
            albums = EMPTY_ALBUM;
        }
        if (events == null) {
            events = EMPTY_EVENT;
        }
    }

    public String getEncodedName() {
        return encodedName;
    }

    public void setEncodedName(String encodedName) {
        this.encodedName = encodedName;
    }
    
    public String getSpotifyId() {
        return spotifyID;
    }
    
    public void setSpotifyId(String spotifyId) {
        this.spotifyID=spotifyId;
    }

    public ItemInfo[] getCollaborations() {
        return collaborations;
    }

    public void setCollaborations(ItemInfo[] collaborations) {
        this.collaborations = collaborations;
    }

    /**
     * Get the artist's image and use an album cover as a fallback
     * @param thumbnail return a thumbnail sized image
     * @return
     */
    public Image getBestArtistImage(boolean thumbnail) {
        Image img = null;
        if (photos.length > 0) {
            if (thumbnail) {
                img = new Image(photos[0].getThumbNailImageUrl());
            } else {
                img = new Image(photos[0].getSmallImageUrl());
            }
        } else if (albums.length > 0) {
            img = new Image(albums[0].getAlbumArt());
            if (thumbnail) {
                img.setVisibleRect(0, 0, 75, 75);
            }
        }
        return img;
    }

    public String getBestArtistImageAsHTML() {
        String imgHtml = "";
        if (photos.length > 0) {
            imgHtml = photos[0].getHtmlWrapper();
        } else if (albums.length > 0) {
            AlbumDetails album = albums[0];
            imgHtml = WebLib.createAnchoredImage(album.getAlbumArt(), album.getAmazonLink(), "margin-right: 10px; margin-bottom: 10px");
        }
        return imgHtml;
    }
}

