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

package com.sun.labs.aura.aardvark;

import com.sun.labs.aura.datastore.DataStore;
import com.sun.labs.aura.util.Tag;
import com.sun.labs.aura.util.ItemAdapter;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.StoreFactory;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.Scored;
import com.sun.syndication.feed.synd.SyndEntry;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;

/**
 * An BlogEntry representation of a Item
 * @author plamere
 */
public class BlogEntry extends ItemAdapter {
    public final static String FIELD_FEED_KEY = "feedKey";
    public final static String FIELD_CONTENT = "content";
    public final static String FIELD_SYND_ENTRY = "syndEntry";
    public final static String FIELD_TAG = "tag";
    public final static String FIELD_AUTHOR = "author";
    public final static String FIELD_AUTHORITY = "authority";
    public final static String FIELD_PUBLISH_DATE = "publish-date";
    public final static String FIELD_AUTOTAG = "autotag";

    /**
     * Wraps a Item as blog entry
     * @param item the item to be turned into a blog entry
     * @throws com.sun.labs.aura.aardvark.util.AuraException
     */
    public BlogEntry(Item item) {
        super(item, Item.ItemType.BLOGENTRY);
    }

    /**
     * Creates a new blog entry
     * @param key the key for the blog entry
     * @param name the name of the blog entry
     * @throws com.sun.labs.aura.aardvark.util.AuraException
     */
    public BlogEntry(String key, String name) throws AuraException {
        this(StoreFactory.newItem(Item.ItemType.BLOGENTRY, key, name));
    }
    
    public void defineFields(DataStore store) throws AuraException {
        try {
            store.defineField(FIELD_FEED_KEY, Item.FieldType.STRING, StoreFactory.INDEXED);
            store.defineField(FIELD_TAG, Item.FieldType.STRING, StoreFactory.INDEXED);
            store.defineField(FIELD_CONTENT, Item.FieldType.STRING, StoreFactory.INDEXED_TOKENIZED);
            store.defineField(FIELD_SYND_ENTRY);
            store.defineField(FIELD_AUTHOR, Item.FieldType.STRING, StoreFactory.INDEXED);
            store.defineField(FIELD_AUTHORITY, Item.FieldType.INTEGER, StoreFactory.INDEXED);
            store.defineField(FIELD_PUBLISH_DATE, Item.FieldType.DATE, StoreFactory.INDEXED);
            store.defineField(FIELD_AUTOTAG, Item.FieldType.STRING, StoreFactory.INDEXED);
        } catch(RemoteException rx) {
            throw new AuraException("Error defining fields for BlogEntry", rx);
        }
    }

    /**
     * Gets the author of the entry
     * @return the author
     */
    public String getAuthor() {
        return getFieldAsString(FIELD_AUTHOR, "");
    }

    /**
     * Sets the author of the entry
     * @param author the author
     */
    public void setAuthor(String author) {
        setField(FIELD_AUTHOR, author);
    }

    /**
     * Gets the authority of the entry
     * @return the authority of the entry
     */
    public float getAuthority() {
        return getFieldAsFloat(FIELD_AUTHORITY);
    }

    /**
     * Sets the published date of the entry
     * @param date the published date of the entry
     */
    public void setPublishDate(Date date) {
        setFieldAsObject(FIELD_PUBLISH_DATE, date);
    }

    /**
     * Gets the publish date of the entry
     * @return the publish date of the entry
     */
    public Date getPublishDate() {
        return (Date) getFieldAsObject(FIELD_PUBLISH_DATE);
    }


    /**
     * Sets the authority of the entry
     * @param authority the authority (1.0 is most, 0.0 is least)
     */
    public void setAuthority(float authority) {
        setField(FIELD_AUTHORITY, authority);
    }

    /**
     * Gets the content of the entry
     * @return the entry content
     */
    public String getContent() {
        return getFieldAsString(FIELD_CONTENT, "");
    }

    /**
     * Sets the content of the entry
     * @param content the entry content
     */
    public void setContent(String content) {
        setField(FIELD_CONTENT, content);
    }

    /**
     * Gets the ID of the owning feed
     * @return the id of the owning feed
     */
    public String getFeedKey() {
        return getFieldAsString(FIELD_FEED_KEY);
    }

    /**
     * Sets the ID of the owning feed
     * @param feedID the id of the owning feed
     */
    public void setFeedKey(String key) {
        setField(FIELD_FEED_KEY, key);
    }

    /**
     * Gets the ordered list of tags applied to this entry
     * @return the ordered list of tags
     */
    public List<Tag> getTags() {
        return getTagsAsList(FIELD_TAG);
    }

    /**
     * Gets the ordered list of tags applied to this entry
     * @return the ordered list of tags
     */
    public List<Scored<String>> getAutoTags() {
        List<Scored<String>> list =  (List<Scored<String>>) getFieldAsObject(FIELD_AUTOTAG);
        if (list == null) {
            list = new ArrayList<Scored<String>>(0);
        }
        return list;
    }

    /**
     * Adds a tag to the entry
     * @param tag the tag name
     * @param count the frequency of the tag
     */
    public void addTag(String tag, int count) {
        addTag(FIELD_TAG, tag, count);
    }

    /**
     * Gets the title of the entry
     * @return the title of the entry
     */
    public String getTitle() {
        return getName();
    }



    /**
     * Gets the SyndEntry associated with this entry
     * @return the synd entry
     */
    public SyndEntry getSyndEntry() {
        return (SyndEntry) getFieldAsObject(FIELD_SYND_ENTRY);
    }

    /**
     * Sets the syndentry associated with this entry
     * @param entry the syndentry
     */
    public void setSyndEntry(SyndEntry entry) {
        setFieldAsObject(FIELD_SYND_ENTRY, entry);
    }
}
