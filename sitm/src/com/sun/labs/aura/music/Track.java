/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.music;

import com.sun.labs.aura.util.ItemAdapter;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.StoreFactory;
import java.util.Set;

/**
 *
 * @author fm223201
 */
public class Track extends ItemAdapter {

    public final static String FIELD_ARTISTS_ID = "artistId";
    public final static String FIELD_SECS = "secs";
    public final static String FIELD_MD5 = "md5";
    public final static String FIELD_LYRICS = "lyrics";
    
    
    /**
     * Wraps an Item as a track
     * @param item the item to be turned into a track
     * @throws com.sun.labs.aura.aardvark.util.AuraException
     */
    public Track(Item item) {
        super(item, Item.ItemType.TRACK);
    }

    /**
     * Creates a new track
     * @param key the key for the track
     * @param name the name of the track
     * @throws com.sun.labs.aura.aardvark.util.AuraException
     */
    public Track(String key, String name) throws AuraException {
        this(StoreFactory.newItem(Item.ItemType.TRACK, key, name));
    }
   
    /**
     * Get the videos associated with an artist
     * @return videos id set
     */
    public Set<String> getArtistId() {
        return (Set<String>) getFieldAsStringSet(FIELD_ARTISTS_ID);
    }
    
   /**
     * Adds an artist to this track
     * @param artistId id of the artist
     */
    public void addArtistId(String artistId) {
        appendToField(FIELD_ARTISTS_ID, artistId);
    }
    
     /**
     * Gets the md5 of the track
     * @return the md5
     */
    public String getMD5() {
        return getFieldAsString(FIELD_MD5, "");
    }

    /**
     * Sets the MD5 of the track
     * @param md5 the md5
     */
    public void setMD5(String md5) {
        setField(FIELD_MD5, md5);
    }
    
    /**
     * Gets the lyrics of the track
     * @return the lyrics
     */
    public String getLyrics() {
        return getFieldAsString(FIELD_LYRICS, "");
    }

    /**
     * Sets the lyrics of the track
     * @param lyrics the lyrics
     */
    public void setImgUrl(String lyrics) {
        setField(FIELD_LYRICS, lyrics);
    }
    
     /**
     * Gets the length of the track in secs
     * @return the number of secs
     */
    public int getSecs() {
        return this.getFieldAsInt(FIELD_SECS);
    }

    /**
     * Sets the length of the track in secs
     * @param secs length in secs
     */
    public void setSecs(int secs) {
        setField(FIELD_SECS, secs);
    }
    
}