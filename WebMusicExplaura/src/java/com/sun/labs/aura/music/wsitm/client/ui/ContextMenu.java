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

package com.sun.labs.aura.music.wsitm.client.ui;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.sun.labs.aura.music.wsitm.client.event.DEMouseOutHandler;
import com.sun.labs.aura.music.wsitm.client.event.DEMouseOverHandler;
import com.sun.labs.aura.music.wsitm.client.event.DataEmbededMouseListener;
import com.sun.labs.aura.music.wsitm.client.items.ArtistCompact;
import com.sun.labs.aura.music.wsitm.client.items.ItemInfo;
import com.sun.labs.aura.music.wsitm.client.items.steerable.CloudItem;

/**
 *
 * @author mailletf
 */
public class ContextMenu {

    private PopupPanel pp;
    protected VerticalPanel vP;

    private ClickHandler hideOnClickHandler;

    private boolean newPopup = true;
  
    public ContextMenu() {
        pp = Popup.getPopupPanel();
        vP = new VerticalPanel();

        hideOnClickHandler = new ClickHandler() {
            @Override
            public void onClick(ClickEvent ce) {
                hideMenu();
            }
        };
    }

    public boolean isVisible() {
        return pp.isVisible();
    }

    public void hideMenu() {
        if (pp != null) {
            pp.hide();
        }
    }

    public void addElement(String s, ClickHandler cH) {
        addElement(new Label(s), cH);
    }

    public void addElement(Label l, ClickHandler cH) {
        l.addClickHandler(cH);
        l.addClickHandler(hideOnClickHandler);
        l.addStyleName("contextMenuItem");

        l.addMouseOutHandler(new DEMouseOutHandler<Label>(l) {
            @Override
            public void onMouseOut(MouseOutEvent event) {
                data.removeStyleName("contextMenuItemHover");
            }
        });
        l.addMouseOverHandler(new DEMouseOverHandler<Label>(l) {
            @Override
            public void onMouseOver(MouseOverEvent event) {
                data.addStyleName("contextMenuItemHover");
            }
        });
        vP.add(l);
    }

    /**
     * @deprecated
     * @param l
     * @param cL
     */
    public void addElement(Label l, ClickListener cL) {
        l.addClickListener(cL);
        l.addClickHandler(hideOnClickHandler);
        l.addStyleName("contextMenuItem");
        l.addMouseListener(new DataEmbededMouseListener<Label>(l) {

            public void onMouseEnter(Widget sender) {
                data.addStyleName("contextMenuItemHover");
            }

            public void onMouseLeave(Widget sender) {
                data.removeStyleName("contextMenuItemHover");
            }
            public void onMouseDown(Widget sender, int x, int y) {}
            public void onMouseMove(Widget sender, int x, int y) {}
            public void onMouseUp(Widget sender, int x, int y) {}
        });
        vP.add(l);
    }

    /**
     * @deprecated
     * @param label
     * @param cL
     */
    public void addElement(String label, ClickListener cL) {
        Label l = new Label(label);
        addElement(l, cL);
    }

    public void addSeperator() {
        vP.add(new HTML("<hr>"));
    }

    public void showAt(Event e) {
        showAt(e, 250);
    }

    public void showAt(Event e, int width) {
        if (vP != null) {
            int x = e.getClientX() + Window.getScrollLeft();
            int y = e.getClientY() + Window.getScrollTop();
            if (newPopup) {
                Popup.showRoundedPopup(vP, "", pp, x, y, width, false);
                newPopup = false;
            } else {
                pp.setPopupPosition(x, y);
                pp.show();
            }
        } else {
            Popup.showErrorPopup("", Popup.ERROR_MSG_PREFIX.NONE,
                    "Error. Contextmenu is empty.", Popup.ERROR_LVL.NORMAL, null);
        }
    }
    
    public interface HasContextMenu {

        public ContextMenu getContextMenu();
    }
    
    public interface TagDependentSharedMenu {
        
        public void showAt(Event e, ItemInfo currTag);
    }
    
    public interface ArtistDependentSharedMenu {
        
        public void showAt(Event e, ArtistCompact currTag);
    }

    public interface CloudItemDependentSharedMenu {

        public void showAt(Event e, CloudItem cI);
    }
}
