/*
 * User.java
 * 
 * Created on Oct 25, 2007, 4:53:12 PM
 * 
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.aardvark.store.item;

import java.net.URL;

/**
 *
 * @author ja151348
 */
public interface User extends Item {
    
    /**
     * Gets the unique hard-to-discover key that was generated to be used as
     * part of the URL for any of this User's content
     * 
     * @return the RFeedKey
     */
    public String getRecommenderFeedKey();
    
    /**
     * Gets the URL that the User initially provided as the location of their
     * Starred Items feed at Google.
     * 
     * @return the URL of the starred items feed
     */
    public URL getStarredItemFeedURL();

    /**
     *  Gets the last time that the feeds for this user was fetched.
     * @return the time the attention was applied, in milliseconds since the
     *         Java epoch (Jan 1, 1970 
     */
    public long getLastFetchTime();

    /**
     * Sets the last time that the feeds for this user was fetched.
     * @param lastFetchTime  the time the attention was applied, in milliseconds since the
     *         Java epoch (Jan 1, 1970 
     */
    public void setLastFetchTime(long lastFetchTime);

}