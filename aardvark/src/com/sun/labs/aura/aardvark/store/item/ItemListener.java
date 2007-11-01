/*
 * ItemListener.java
 * 
 * Created on Oct 25, 2007, 4:11:16 PM
 * 
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.aardvark.store.item;


/**
 * A Listener for events related to Items.
 * 
 * @author ja151348
 */
public interface ItemListener {
    /**
     * Called when an Item or set of Items have been created.
     * 
     * @param items the set of Items that were created
     */
    public void itemCreated(ItemEvent e);
    
    /**
     * Called when an Item or set of Items have been changed.
     * 
     * @param items the set of Items that changed
     */
    public void itemChanged(ItemEvent e);

    /**
     * Called when an Item or set of Items have been deleted.
     * 
     * @param items the set of Items that were deleted
     */
    public void itemDeleted(ItemEvent e);
}
