/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.music;

import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.StoreFactory;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.ItemAdapter;
import com.sun.labs.aura.util.Tag;
import java.util.List;
import java.util.Set;

/**
 *
 * Represents a social tag that has been applied to an artist
 */
public class ArtistTag extends ItemAdapter {
    public final static String FIELD_POPULARITY = "popularity";
    public final static String FIELD_DESCRIPTION = "description";
    public final static String FIELD_TAGGED_ARTISTS = "taggedArtists";
    public final static String FIELD_VIDEOS = "videos";
    public final static String FIELD_PHOTOS = "photos";

    /**
     * Wraps an Item as an ArtistTag
     * @param item the item to be turned into an ArtistTag
     * @throws com.sun.labs.aura.aardvark.util.AuraException
     */
    public ArtistTag(Item item) {
        super(item, Item.ItemType.ARTIST_TAG);
    }

    /**
     * Creates a new ArtistTag
     * @param key the key for the ArtistTag
     * @param name the name of the ArtistTag
     * @throws com.sun.labs.aura.aardvark.util.AuraException
     */
    public ArtistTag(String name) throws AuraException {
        this(StoreFactory.newItem(Item.ItemType.ARTIST_TAG, "artist-tag:" + name, name));
    }

    /**
     * Gets the popularity of the ArtistTag
     * @return the popularity
     */
    public float getPopularity() {
        return getFieldAsFloat(FIELD_POPULARITY);
    }

    /**
     * Sets the popularity of the ArtistTag
     * @param popularity the ArtistTag
     */
    public void setPopularity(float popularity) {
        setField(FIELD_POPULARITY, popularity);
    }

    /**
     * Gets the description of the tag
     * @return the description of the tag
     */
    public String getDescription() {
        return getFieldAsString(FIELD_DESCRIPTION, "");
    }

    /**
     * Sets the biography summary of the ArtistTag
     * @param biography summary the ArtistTag
     */
    public void setDescription(String description) {
        setField(FIELD_DESCRIPTION, description);
    }


    /**
     * Gets the artists that have been tagged with the social tag
     * @return tag map
     */

    public List<Tag> getTaggedArtist() {
        return getTagsAsList(FIELD_TAGGED_ARTISTS);
    }

    /**
     * Adds a an artist to the artisttag
     * @param mbaid the musicbrainzid of the artist
     * @param count tag count
     */
    public void addTaggedArtist(String mbaid, int count) {
        addTag(FIELD_TAGGED_ARTISTS, mbaid, count);
    }

    /**
     * Adds a video to an ArtistTag
     * @param videoID id of the video
     */
    public void addVideo(String videoId) {
        appendToField(FIELD_VIDEOS, videoId);
    }

    /**
     * Get the videos associated with an ArtistTag
     * @return videos id set
     */
    public Set<String> getVideos() {
        return getFieldAsStringSet(FIELD_VIDEOS);
    }


    /**
     * Get the photos associated with an ArtistTag
     * @return photos map
     */
    public Set<String> getPhotos() {
        return getFieldAsStringSet(FIELD_PHOTOS);
    }

    /**
     * Adds a photo to an ArtistTag
     * @param photoID id of the photo
     */
    public void addPhoto(String photoId) {
        appendToField(FIELD_PHOTOS, photoId);
    }
}