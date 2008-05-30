/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.music;

import com.sun.labs.aura.util.ItemAdapter;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.StoreFactory;
import java.util.Date;

/**
 *
 * @author fm223201
 */
public class Event extends ItemAdapter {

    public final static String FIELD_VENUE_NAME = "venueName";
    public final static String FIELD_DATE = "date";
    
    /**
     * Wraps an Item as an Event
     * @param item the item to be turned into an event
     * @throws com.sun.labs.aura.aardvark.util.AuraException
     */
    public Event(Item item) {
        super(item, Item.ItemType.EVENT);
    }

    /**
     * Creates a new photo
     * @param key the key for the photo
     * @param name the name of the photo
     * @throws com.sun.labs.aura.aardvark.util.AuraException
     */
    public Event(String key, String name) throws AuraException {
        this(StoreFactory.newItem(Item.ItemType.EVENT, key, name));
    }

    /**
     * Gets the id of the venue
     * @return the id
     */
    public String getVenueName() {
        return getFieldAsString(FIELD_VENUE_NAME, "");
    }

    /**
     * Sets the id of the venue
     * @param id the venue
     */
    public void setVenueName(String name) {
        setField(FIELD_VENUE_NAME, name);
    }
    
    /**
     * Sets the event date
     * @param date the date of the event
     */
    public void setDate(String date) {
        setField(FIELD_DATE, date);
    }

    /**
     * Gets the date of the event
     * @return the date of the event
     */
    public String getDate() {
        return getFieldAsString((FIELD_DATE));
    }
}        