/*
 * Item.java
 * 
 * Created on Oct 25, 2007, 4:07:27 PM
 * 
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.aardvark.store.item;

import com.sun.labs.aura.aardvark.store.*;
import java.util.List;

/**
 * A generic Item that is stored in the ItemStore.  Aura will support many
 * configurable types of items.  An Item's fields will be defined from the
 * base types that are provided as part of Aura.
 * 
 * @author ja151348
 */
public interface Item {
    
    /**
     * Gets the Aura ID assigned to this Item
     * 
     * @return the ID
     */
    public long getID();
    
    /**
     * Gets the globally unique key that was assigned to this item when it was
     * entered into the ItemStore
     * 
     * @return the Item's key
     */
    public String getKey();
    
    /**
     * Gets the well defined specific type of this Item.
     * 
     * @return a string uniquely identifying the Item's type
     */
    public String getType();

    /**
     * Gets the value of the specified field from the Item.
     * 
     * @param name the name of the field to fetch
     * @return the value of the named field
     */
    public String getField(String name);
    
    /**
     * Sets the value of the specified field from the Item.
     * 
     * @param name the name of the field to set
     * @param value the value to assign to the named field
     */
    public void setField(String name, String value);
    
    /**
     * Gets a list of all the attention data that is stored about this item.
     * 
     * @return a list of attention data
     */
    public List<Attention> getAttentionData();

}
