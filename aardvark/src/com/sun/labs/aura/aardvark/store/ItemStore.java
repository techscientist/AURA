/*
 * ItemStore.java
 * 
 * Created on Oct 25, 2007, 3:53:59 PM
 * 
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.aardvark.store;

import com.sun.labs.aura.aardvark.store.item.Entry;
import com.sun.labs.aura.aardvark.store.item.Feed;
import com.sun.labs.aura.aardvark.store.item.ItemListener;
import com.sun.labs.aura.aardvark.store.item.Item;
import com.sun.labs.aura.aardvark.store.item.User;
import com.sun.labs.aura.aardvark.util.AuraException;
import com.sun.labs.util.props.Component;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * The ItemStore is responsible for storing items and their associated
 * data in some fashion that will allow quick retrieval for recommendation
 * algorithms and relatively quick retrieval for the rest of Aardvark's
 * functions.
 * 
 */
public interface ItemStore extends Component, Remote {
    
    /**
     * Creates an Item of the specified type with the given string as its key.
     * This method automatically assigns a new ID to the Item and is the only
     * supported way to create a new item that is to be added to the store.
     * 
     * @param itemType the class of the specific type of item to create
     * @param key a globally unique key that identifies this item.  May be a
     *            URI that points to the source of the item.
     * @return a new Item that is empty except for an ID and the provided key
     * @throws com.sun.labs.aura.aardvark.util.AuraException if the key already
     *         exists in the ItemStore
     */
    public <T extends Item> T newItem(Class<T> itemType, String key)
            throws AuraException, RemoteException;
    
    /**
     * Looks up the Aura ID of an item in the ItemStore.  This operation is
     * likely to be quicker than the {@link #get(String)} method since it
     * does not have to instantiate the entire Item.  If you need to get the
     * entire Item, call that method directly.
     * 
     * @param key the globally unique key that was used to create the Item
     * @return the Aura ID of the item, or -1 if the item does not exist
     */
    //public long getID(String key);


    /**
     * Gets all of the items in the store that are of the given type.
     * 
     * @param itemType the type of items that are of interest
     * @return a list containing all items of the given type
     */
    public <T extends Item> Set<T> getAll(Class<T> itemType)
            throws AuraException, RemoteException;
    
    
    /**
     * Gets an Item from the ItemStore.  The Item returned is specified by the
     * Aura ID that is passed in.
     * 
     * @param id the Aura ID of the Item to fetch
     * @return the requested Item
     */
    public Item get(long id) throws AuraException, RemoteException;
    
    /**
     * Gets an Item from the ItemStore that is associated with the given
     * globally unique key.  This method will instantiate the item once it
     * is found.
     * 
     * @param key the globally unique key that was used to create the Item
     * @return the requested Item
     */
    public Item get(String key) throws AuraException, RemoteException;
    
    /**
     * Puts an item into the ItemStore.  The Item may be either a new Item
     * created by the {@link #newItem} method or a modification of an
     * existing Item that was retrieved using one of the get methods.  If the
     * Item has the same Aura ID and key as an existing item, the existing
     * item will be updated.  If the Aura ID and key are new, the item will
     * be stored.  In any other case, an AuraException will be thrown.
     * 
     * @param item the Item that should be placed into the ItemStore
     * @return the item that was put into the database.  Because putting an
     * item may change the state of an item and because an item store may be
     * running remotely, it is recommended that further operations on the item
     * that it put into the item store be performed on the return value.
     * @throws com.sun.labs.aura.aardvark.util.AuraException if the Item is
     *         not valid for adding/updating
     */
    public Item put(Item item) throws AuraException, RemoteException;

    /**
     * Gets all the items of a particular type that have been added since a
     * particular time.  Returns an iterator over those items that must be
     * closed when reading is done.
     * 
     * @param itemType the type of item to retrieve
     * @param timeStamp the time from which to search (to the present time
     * @return an iterator over the added items
     * @throws com.sun.labs.aura.aardvark.util.AuraException 
     */
    public <T extends Item> DBIterator<T> getItemsAddedSince(Class<T> itemType,
            Date timeStamp) throws AuraException, RemoteException;

    /**
     * Gets the last <code>count</code> attentions for the given user.
     * @param user the user whose attention data we want
     * @param count the number of attention data points to return
     * @return a set of attention data points ordered by the date they were 
     * added
     * @throws java.rmi.RemoteException
     */
    public SortedSet<Attention> getLastAttention(User user, int count) throws RemoteException;
    
    /**
     * Gets the last <code>count</code> attentions for the given user.
     * @param user the user whose attention data we want
     * @param type the type of attention items that we want to fetch
     * @param count the number of attention data points to return
     * @return a set of attention data points ordered by the date they were 
     * added
     * @throws java.rmi.RemoteException
     */
    public SortedSet<Attention> getLastAttention(User user, Attention.Type type, int count) throws RemoteException;
    /**
     * Gets all the attention that has been added to the store since a
     * particular date.  Returns an iterator over the attention that must be
     * closed when reading is done.
     * 
     * @param timeStamp the time to search back to
     * @return the Attentions added since that time
     * @throws com.sun.labs.aura.aardvark.util.AuraException
     */
    public DBIterator<Attention> getAttentionAddedSince(Date timeStamp)
            throws AuraException, RemoteException;
    
    /**
     * Gets the attention data associated with an item.
     * @param item the item whose attention data we want
     * @return a list of attentions for this item
     * @throws java.rmi.RemoteException if there is any error fetching the
     * data.
     */
    public List<Attention> getAttentionData(Item item) throws AuraException, RemoteException;
    
    /**
     * Gets the feeds for a given user of the given type.
     * @param user the user whose feeds we want
     * @param type the type of attention that the user has for the field
     * @return a set of feeds for the user of the given type
     * @throws com.sun.labs.aura.aardvark.util.AuraException 
     * @throws java.rmi.RemoteException
     */
    public Set<Feed> getFeeds(User user, Attention.Type type) throws AuraException, RemoteException;

    /**
     * Gets all of the entries associated with a given feed.
     * @param feed the feed whose entries we want.
     * @return the entries from the given feed, sorted in order of addition
     * @throws java.rmi.RemoteException if there is an error communicating with
     * the item store.
     */
    public SortedSet<Entry> getEntries(Feed feed) throws RemoteException;
    
    /**
     * Adds attention to the the ItemStore.  The Attention should contain
     * the User, the Item, the type of attention, and optionally a value
     * associated with the type (TBD).
     * 
     * @param att the attention that was paid
     * 
     * @throws com.sun.labs.aura.aardvark.util.AuraException in the event that
     *         the attention is invalid
     */
    public void attend(Attention att) throws AuraException, RemoteException;
    
    /**
     * Adds an ItemListener to this ItemStore.  ItemListeners are sent
     * batches of Item-related events.  The policy for when to send events
     * is left to the ItemStore implementation.  The listener may request
     * events only for a specific type of Item using the type parameter.  If
     * null is provided for the type, then events for all types of Items are
     * delivered.  A single ItemListener may register itself multiple times
     * with different Item types.
     * 
     * @param type the type of Item for which events are delivered, or null
     *             for all events
     * @param listener the listener to which events are delivered
     */
    public <T extends Item> void addItemListener(Class<T> type,
                                                 ItemListener listener)
            throws AuraException, RemoteException;
    
   
    /**
     * Removes an ItemListener from this ItemStore.  Since the same
     * ItemListener may listen for multiple different ItemTypes, the type
     * of the Item may be provided to stop only a specific set of events
     * from being delivered to the listener.  If null is provided for the type,
     * then the ItemListener will be removed entirely, even if it was added
     * only for specific types.
     * 
     * @param type the type of Item for which events should cease, or null
     *             for all events
     * @param listener the listener to which events should no longer be sent
     */
    public <T extends Item> void removeItemListener(Class<T> type,
                                                    ItemListener listener)
            throws AuraException, RemoteException;

    /**
     * Get stats about the Item Store.  The ItemStoreStats is implementation
     * specfic to Aardvark and returns info about users, entries, and
     * attention.
     * 
     * @return the item store stats
     */
    public ItemStoreStats getStats() throws AuraException, RemoteException;
    
    /**
     * Closes the item store cleanly.  This should be called before the
     * application exits.
     */
    public void close() throws AuraException, RemoteException;
}
