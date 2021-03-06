/*
 * Copyright 2007-2009 Sun Microsystems, Inc. All Rights Reserved.
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

package com.sun.labs.aura.music;

import com.sun.labs.aura.datastore.DataStore;
import com.sun.labs.aura.util.ItemAdapter;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.StoreFactory;
import java.rmi.RemoteException;
import java.util.EnumSet;

/**
 *
 * @author fm223201
 */
public class Venue extends ItemAdapter {

    public final static String FIELD_ADDRESS = "address";
    public final static String FIELD_CITY = "city";
    public final static String FIELD_STATE = "state";
    public final static String FIELD_COUNTRY = "country";
    
    /**
     * Wraps an Item as a Venue
     * @param item the item to be turned into a venue
     * @throws com.sun.labs.aura.aardvark.util.AuraException
     */
    public Venue(Item item) {
        super(item, Item.ItemType.VENUE);
    }

    public Venue() {
    }

    /**
     * Creates a new photo
     * @param key the key for the photo
     * @param name the name of the photo
     * @throws com.sun.labs.aura.aardvark.util.AuraException
     */
    public Venue(String key, String name) throws AuraException {
        this(StoreFactory.newItem(Item.ItemType.VENUE, key, name));
    }

  public void defineFields(DataStore ds) throws AuraException {
        try {
            ds.defineField(FIELD_ADDRESS, Item.FieldType.STRING, StoreFactory.INDEXED);
            ds.defineField(FIELD_CITY, Item.FieldType.STRING, StoreFactory.INDEXED);
            ds.defineField(FIELD_COUNTRY, Item.FieldType.STRING, StoreFactory.INDEXED);
            ds.defineField(FIELD_STATE, Item.FieldType.STRING, StoreFactory.INDEXED);
        } catch(RemoteException rx) {
            throw new AuraException("Error defining fields for Venue", rx);
        }
    }

    /**
     * Gets the street address of the venue
     * @return the address
     */
    public String getAddress() {
        return getFieldAsString(FIELD_ADDRESS, "");
    }

    /**
     * Sets the address of the venue
     * @param address the address
     */
    public void setAddress(String address) {
        setField(FIELD_ADDRESS, address);
    }

    /**
     * Gets the city of the venue
     * @return the address
     */
    public String getCity() {
        return getFieldAsString(FIELD_CITY, "");
    }

    /**
     * Sets the city of the venue
     * @param city the city
     */
    public void setCity(String city) {
        setField(FIELD_CITY, city);
    }
    
    /**
     * Gets the state of the venue
     * @return the state
     */
    public String getState() {
        return getFieldAsString(FIELD_STATE, "");
    }

    /**
     * Sets the state of the venue
     * @param state the state
     */
    public void setState(String state) {
        setField(FIELD_STATE, state);
    }
    
    /**
     * Gets the country of the venue
     * @return the country
     */
    public String getCountry() {
        return getFieldAsString(FIELD_COUNTRY, "");
    }

    /**
     * Sets the country of the venue
     * @param country the country
     */
    public void setCountry(String country) {
        setField(FIELD_COUNTRY, country);
    }
    
}
