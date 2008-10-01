/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.music.wsitm.client.ui.widget;

import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.Widget;

/**
 *
 * @author mailletf
 */
public abstract class PopularitySelect extends Composite {

    private ListBox l;
    
    public PopularitySelect() {
        
        l = new ListBox(false);
        l.addItem("All", "ALL");
        l.addItem("Popular", "HEAD");
        l.addItem("Mainstream", "HEAD_MID");
        l.addItem("Hipster", "MID_TAIL");
        l.addItem("Rarities", "TAIL");
        l.setSelectedIndex(0);

        l.addChangeListener(new ChangeListener() {

            public void onChange(Widget sender) {
                onSelectionChange(getSelectedValue());
            }
        });

        initWidget(l);
        
    }

    public String getSelectedValue() {
        return l.getValue(l.getSelectedIndex());
    }

    /**
     * Called when the selection is changed
     * @param newPopularity new popularity value selected
     */
    public abstract void onSelectionChange(String newPopularity);
    
}
