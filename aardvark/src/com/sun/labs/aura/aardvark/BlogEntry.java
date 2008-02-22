/*
 *  Copyright 2007 Sun Microsystems, Inc. 
 *  All Rights Reserved. Use is subject to license terms.
 * 
 *  See the file "license.terms" for information on usage and
 *  redistribution of this file, and for a DISCLAIMER OF ALL
 *  WARRANTIES..
 */

package com.sun.labs.aura.aardvark;

import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.StoreFactory;
import com.sun.labs.aura.util.AuraException;
import com.sun.syndication.feed.synd.SyndEntry;
import java.util.List;

/**
 * An BlogEntry representation of a Item
 * @author plamere
 */
public class BlogEntry extends ItemAdapter {
    private final static String FIELD_FEED_KEY = "fieldKey";
    private final static String FIELD_TITLE = "title";
    private final static String FIELD_CONTENT = "content";
    private final static String FIELD_SYND_ENTRY = "syndEntry";
    private final static String FIELD_TAG = "tag";
    private final static String FIELD_AUTHOR = "author";
    private final static String FIELD_AUTHORITY = "authority";

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

    /**
     * Gets the author of the entry
     * @return the author
     */
    public String getAuthor() {
        return getFieldAsString(FIELD_AUTHOR);
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
        return getFieldAsString(FIELD_CONTENT);
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
        return getFieldAsString(FIELD_TITLE);
    }


    /**
     * Sets the title of the entry
     * @param title the title of the entry
     */
    public void setTitle(String title) {
        setField(FIELD_TITLE, title);
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
