/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.aardvark.impl.store.item;

import com.sun.labs.aura.aardvark.store.item.Entry;

/**
 * Implements an Entry in a Feed
 * 
 * @author ja151348
 */
public class EntryImpl extends ItemImpl implements Entry {

    protected final static String CONTENT = "content";
    
    public EntryImpl(long itemID, String key) {
        super(itemID, key);
    }

    public static String getType() {
        return "AardvarkEntry";
    }
    
    public String getContent() {
        return getField(CONTENT);
    }

    public void setContent(String content) {
        setField(CONTENT, content);
    }

}
