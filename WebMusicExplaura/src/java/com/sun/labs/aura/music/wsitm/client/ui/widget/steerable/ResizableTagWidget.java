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

package com.sun.labs.aura.music.wsitm.client.ui.widget.steerable;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.dom.client.MouseDownHandler;
import com.google.gwt.event.dom.client.MouseUpEvent;
import com.google.gwt.event.dom.client.MouseUpHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.MouseListener;
import com.google.gwt.user.client.ui.Widget;
import com.sun.labs.aura.music.wsitm.client.WebLib;
import com.sun.labs.aura.music.wsitm.client.ClientDataManager;
import com.sun.labs.aura.music.wsitm.client.items.steerable.CloudArtist;
import com.sun.labs.aura.music.wsitm.client.items.steerable.CloudItem;
import com.sun.labs.aura.music.wsitm.client.items.steerable.WrapsCloudItem;
import com.sun.labs.aura.music.wsitm.client.ui.ContextMenu;
import com.sun.labs.aura.music.wsitm.client.ui.ContextMenu.HasContextMenu;
import com.sun.labs.aura.music.wsitm.client.ui.Popup;
import com.sun.labs.aura.music.wsitm.client.ui.SharedSteeringCIMenu;
import com.sun.labs.aura.music.wsitm.client.ui.SpannedLabel;
import com.sun.labs.aura.music.wsitm.client.ui.TagDisplayLib;
import com.sun.labs.aura.music.wsitm.client.ui.swidget.SteeringSwidget.MainPanel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;
import org.adamtacy.client.ui.NEffectPanel;
import org.adamtacy.client.ui.effects.core.NMorphStyle;
import org.adamtacy.client.ui.effects.events.EffectCompletedEvent;
import org.adamtacy.client.ui.effects.events.EffectCompletedHandler;
import org.adamtacy.client.ui.effects.impl.Fade;
import org.adamtacy.client.ui.effects.impl.css.Property;

/**
 * Resizable tag cloud manipulation UI
 * @author mailletf
 */
public class ResizableTagWidget extends TagWidget {

    private static int AVG_SIZE_OF_ADDED_CLOUD = 40;
    
    private ClientDataManager cdm;
    private SharedSteeringCIMenu sharedArtistMenu;
    private SharedSteeringCIMenu sharedTagMenu;
    private HashMap<String, SpannedNEffectPanel> tagCloud;
    private boolean hasChanged = false; // did the tagCloud change and recommendations need to be updated
    private double maxSize = 0.1;
    private Grid g;
    private FocusPanel fP;
    private FlowPanel flowP;
    private int lastY;
    private int lastX;
    private int colorIndex = 1;

    public ResizableTagWidget(MainPanel mainPanel, ClientDataManager cdm, 
            SharedSteeringCIMenu sharedArtistMenu, SharedSteeringCIMenu sharedTagMenu) {

        super(mainPanel);

        this.cdm = cdm;
        this.sharedArtistMenu = sharedArtistMenu;
        this.sharedTagMenu = sharedTagMenu;

        int panelWidth = 480;
        if (Window.getClientWidth() > 1024) {
            panelWidth = (int) (Window.getClientWidth() * 480.0 / 1024.0);
        }

        fP = new FocusPanel();
        fP.setWidth(panelWidth + "px");
        fP.setHeight("450px");
        flowP = new FlowPanel();
        flowP.setWidth("500px");
        flowP.getElement().setAttribute("style", "margin-top: 15px");
        fP.add(flowP);
        initWidget(fP);

        tagCloud = new HashMap<String, SpannedNEffectPanel>();
        fP.addMouseListener(new MouseListener() {

            @Override
            public void onMouseDown(Widget arg0, int newX, int newY) {
                lastY = newY;
                lastX = newX;

                ((FocusPanel) arg0).setFocus(false);
            }

            @Override
            public void onMouseEnter(Widget arg0) {}

            @Override
            public void onMouseLeave(Widget arg0) {
                boolean wasTrue = false;
                for (SpannedNEffectPanel sep : tagCloud.values()) {
                    ResizableTag dW = sep.getTag();
                    if (dW.hasClicked()) {
                        wasTrue = true;
                    }
                    dW.setClickFalse();
                }
                if (wasTrue) {
                    updateRecommendations();
                }

                ((FocusPanel) arg0).setFocus(false);
            }

            @Override
            public void onMouseMove(Widget arg0, int newX, int newY) {

                // Take either increment from Y or X movement. Taking both
                // modifies the size too quickly.
                // -- Going up or right grows the tags.
                int diffY = lastY - newY;
                int diffX = newX - lastX;
                int increment = 0;
                if (Math.abs(diffY) > Math.abs(diffX)) {
                    increment = diffY;
                } else {
                    increment = diffX;
                }

                // Don't refresh everytime to let the browser take its breath
                if (Math.abs(increment) > 3) {

                    double diff = 0;
                    maxSize = 0; // reset maxsize to deal with when the top tag is scaled down
                    for (SpannedNEffectPanel sep : tagCloud.values()) {
                        ResizableTag dW = sep.getTag();
                        double oldSize = dW.getCurrentSize();
                        double tempDiff = dW.updateSize(increment, true);

                        if (oldSize != dW.getCurrentSize()) {
                            hasChanged = true;
                        }

                        if (tempDiff != 0) {
                            diff = tempDiff;
                        }

                        if (Math.abs(dW.getCurrentSize()) > maxSize) {
                            maxSize = Math.abs(dW.getCurrentSize());
                        }
                    }

                    //
                    // Do a second pass to modify the tags that aren't being resized
                    // if the one that is resized has reached its max/min size
                    if (diff != 0) {
                        diff = diff / (tagCloud.size() - 1);
                        for (SpannedNEffectPanel sep : tagCloud.values()) {
                            ResizableTag dW = sep.getTag();
                            double oldSize = dW.getCurrentSize();
                            dW.updateSize(diff, false);

                            if (oldSize != dW.getCurrentSize()) {
                                hasChanged = true;
                            }

                            if (Math.abs(dW.getCurrentSize()) > maxSize) {
                                maxSize = Math.abs(dW.getCurrentSize());
                            }
                        }
                    }

                    lastY = newY;
                    lastX = newX;
                }

                ((FocusPanel) arg0).setFocus(false);
            }

            @Override
            public void onMouseUp(Widget arg0, int arg1, int arg2) {
                for (SpannedNEffectPanel sep : tagCloud.values()) {
                    ResizableTag dW = sep.getTag();
                    dW.setClickFalse();
                }
                ((FocusPanel) arg0).setFocus(false);
                updateRecommendations();
            }
        });
    }

    @Override
    public void updateRecommendations() {
        if (hasChanged) {
            hasChanged = false;
            super.updateRecommendations();
        }
    }

    @Override
    protected void onAttach() {
        WebLib.disableTextSelectInternal(this.getElement(), true);
        super.onAttach();
    }

    @Override
    protected void onDetach() {
        super.onDetach();
        WebLib.disableTextSelectInternal(this.getElement(), false);
    }
    
    @Override
    public double getMaxWeight() {
        double maxVal = 0;
        double tempVal = 0;
        for (SpannedNEffectPanel sep : tagCloud.values()) {
            ResizableTag dT = sep.getTag();
            tempVal = dT.getCloudItem().getWeight();
            if (tempVal > maxVal) {
                maxVal = tempVal;
            }
        }
        return maxVal;
    }

    /**
     * Add all supplied items
     * @param tag
     */
    @Override
    public void addItems(HashMap<String, CloudItem> items, ITEM_WEIGHT_TYPE weightType, int limit) {
        if (items != null && items.size() > 0) {
            if (limit == 0) {
                limit = items.size();
            }

            ArrayList<CloudItem> itemsList = new ArrayList<CloudItem>(items.values());
            ArrayList<CloudItem> cutList = new ArrayList<CloudItem>();
            Collections.sort(itemsList, new CloudItemWeightSorter());

            maxSize = itemsList.get(0).getWeight();
            double sumScore = 0;
            int nbr = 0;
            for (CloudItem cI : itemsList) {
                cutList.add(cI);
                sumScore += cI.getWeight();
                if (nbr++ >= limit) {
                    break;
                }
            }

            // Find the size of the biggest tag so that the average size of the
            // added tags match AVG_SIZE_OF_ADDED_CLOUD
            double maxTagSize = AVG_SIZE_OF_ADDED_CLOUD * cutList.size() / sumScore;

            // Add the tags to the cloud
            Collections.sort(cutList, new RandomSorter());
            for (CloudItem i : cutList) {
                if (weightType == TagWidget.ITEM_WEIGHT_TYPE.RELATIVE) {
                    if (i.getWeight() < 0) {
                        i.setWeight( -AVG_SIZE_OF_ADDED_CLOUD );
                    } else {
                        i.setWeight( i.getWeight() * maxTagSize );
                    }
                }
                this.addItem(i, false);
            }

            hasChanged = true;
            DeferredCommand.addCommand(new Command() {
                @Override
                public void execute() {
                    updateRecommendations();
                }
            });
        }
    }

    @Override
    public void addItem(CloudItem item, boolean updateRecommendations) {
        if (!tagCloud.containsKey(item.getId())) {
            if (item.getWeight() == 0) {
                item.setWeight( AVG_SIZE_OF_ADDED_CLOUD );
            }
            
            ResizableTag rT = getNewTagObject(item, (colorIndex++) % 2);
            rT.addStyleName("pointer");

            SpannedNEffectPanel sep = new SpannedNEffectPanel();
            sep.add(rT);

            tagCloud.put(item.getId(), sep);
            flowP.add(sep);
            flowP.add(new SpannedLabel(" "));

            if (tagCloud.size() == 1) {
                maxSize = rT.getCurrentSize();
            } else {
                if (rT.getCurrentSize() > maxSize) {
                    maxSize = rT.getCurrentSize();
                }
            }

            hasChanged = true;
            if (updateRecommendations) {
                updateRecommendations();
            }

            cdm.getTagCloudListenerManager().triggerOnTagAdd(item.getId());
        } else {
            highlightItem(item.getId());
        }
    }

    @Override
    public void removeItem(String itemId) {
        
        if (tagCloud.containsKey(itemId)) {

            final String iid = itemId;
            final Fade f = new Fade();

            EffectCompletedHandler eCH = new EffectCompletedHandler() {
                @Override
                public void onEffectCompleted(EffectCompletedEvent event) {
                    DeferredCommand.addCommand(new Command() {
                        @Override
                        public void execute() {
                            doRemoveItem(iid);
                        }
                    });
                }
            };

            f.setStartOpacity(100);
            f.setEndOpacity(1);
            f.addEffectCompletedHandler(eCH);
            tagCloud.get(iid).addEffect(f);
            f.setDuration(1.0);
            f.play();

        } else {
            Popup.showErrorPopup("", Popup.ERROR_MSG_PREFIX.NONE,
                "Error. '"+itemId+"' not in tag cloud.", Popup.ERROR_LVL.NORMAL, null);
        }
    }

    /**
     * Perform the actual removal of the tag. This is necessary because if a circular reference
     * seems to be created between the effectHandler and the EffectsPanel and if we delete
     * the panel in the Handler, it blows up
     * @param itemId
     */
    private void doRemoveItem(String itemId) {
        flowP.remove(tagCloud.get(itemId));

        tagCloud.remove(itemId);
        redrawTagCloud();
        cdm.getTagCloudListenerManager().triggerOnTagDelete(itemId);

        hasChanged = true;
        updateRecommendations();
    }

    @Override
    public void removeAllItems(boolean updateRecommendations) {
        tagCloud.clear();
        flowP.clear();
        colorIndex = 1;
        cdm.getTagCloudListenerManager().triggerOnTagDeleteAll();

        hasChanged = true;
        if (updateRecommendations) {
            updateRecommendations();
        }
    }

    @Override
    public void redrawTagCloud() {
        colorIndex = 1;
        for (SpannedNEffectPanel sep : tagCloud.values()) {
            ResizableTag dW = sep.getTag();
            dW.updateColor((colorIndex++) % 2);
        }
    }

    @Override
    public boolean containsItem(String itemId) {
        return tagCloud.containsKey(itemId);
    }

    @Override
    public HashMap<String, CloudItem> getItemsMap() {
        HashMap<String, CloudItem> itemsMap = new HashMap<String, CloudItem>();
        for (SpannedNEffectPanel sep : tagCloud.values()) {
            ResizableTag tag = sep.getTag();
            itemsMap.put(tag.getCloudItem().getId(), tag.getCloudItem());
        }
        return itemsMap;
    }

    /**
     * Determines the type of CloudItem, creates the right type of ResizableTag
     * and returns it
     * @param item
     * @param color
     * @return
     */
    private ResizableTag getNewTagObject(CloudItem item, int startingColorIndex) {
        ResizableTag rT;
        if (item instanceof CloudArtist) {
            rT = new ResizableArtistTag(item, startingColorIndex);
        } else {
            rT = new ResizableTag(item, startingColorIndex);
        }
        return rT;
    }

    @Override
    public void highlightItem(String itemId) {
        if (tagCloud.containsKey(itemId)) {
            ResizableTag rT = tagCloud.get(itemId).getTag();
            rT.playHighLightEffect(tagCloud.get(itemId));
        }
    }

    /**
     * Temporary class to fix bug in gwt-fx. Remove when they fix constructor
     * TODO Remove this
     */
    private class NMorphStyleFixed extends NMorphStyle {

        public NMorphStyleFixed(Property start, Property end) {
            if (!start.getName().equals(end.getName())) {
                throw new RuntimeException(
                        "Start and End properties must be of the same type!  " +
                        "You used start=" + start.getName() + " and end=" + end.getName());
            }
            propertyPairs = new Vector<PropertyPair>();
            propertyPairs.add(new PropertyPair(start, end));
        }
    }

    /**
     * Resizable tag with the addition of the artist context menu
     */
    private class ResizableArtistTag extends ResizableTag {

        public ResizableArtistTag(CloudItem item, int startingColorIndex) {
            super(item, startingColorIndex);
            this.cm = sharedArtistMenu;
        }
        
    }

    private class ResizableTag extends SpannedLabel implements WrapsCloudItem, HasContextMenu {

        private NMorphStyle highlightEffect;

        protected SharedSteeringCIMenu cm;
        private final static double DEFAULT_SIZE = 40;

        private boolean hasClicked = false;
        protected CloudItem item;
        private static final int MIN_SIZE = 4;
        private static final int MAX_SIZE = 175;

        private String currCssColorName = "";
        private int lastUsedIndex = 1;

        public ResizableTag(CloudItem item, int startingColorIndex) {
            super(item.getDisplayName());
            this.item = item;
            this.cm = sharedTagMenu;
            sinkEvents(Event.ONCONTEXTMENU);
            
            if (this.item.getWeight() == 0) {
                this.item.setWeight(DEFAULT_SIZE);
            }

            lastUsedIndex = startingColorIndex;
            setTitle("Click and drag this tag to change its size. Right click for more options.");

            addStyleName("marginRight");
            addStyleName("hand");
            resetAttributes();
            this.addMouseUpHandler(new MouseUpHandler() {
                @Override
                public void onMouseUp(MouseUpEvent event) {
                    hasClicked = false;
                }
            });
            this.addMouseDownHandler(new MouseDownHandler() {
                @Override
                public void onMouseDown(MouseDownEvent event) {
                    hasClicked = true;
                }
            });
        }

        /**
         * Creates the highlight effect if it doesn't exist and plays it
         */
        public void playHighLightEffect(NEffectPanel effectPanel) {
            if (effectPanel != null) {
                if (highlightEffect==null) {
                    highlightEffect = new NMorphStyleFixed(
                            new Property("background:#cacaca"),
                            new Property("background:#ffffff"));
                    effectPanel.addEffect(highlightEffect);
                }
                highlightEffect.play();
            }
        }

        @Override
        public void onBrowserEvent(Event event) {
            if (event.getTypeInt() == Event.ONCONTEXTMENU) {
                DOM.eventPreventDefault(event);
                cm.showAt(event, item);
            } else {
                super.onBrowserEvent(event);
            }
        }

        @Override
        public ContextMenu getContextMenu() {
            return cm;
        }

        private final void resetAttributes() {
            getElement().setAttribute("style", "-moz-user-select: none; -khtml-user-select: none; user-select: none;");
            getElement().getStyle().setPropertyPx("fontSize", (int)Math.abs(item.getWeight()));
            updateColor();
        }

        public void updateColor() {
            updateColor(lastUsedIndex);
        }

        public void updateColor(int newIndex) {
            lastUsedIndex = newIndex;
            currCssColorName = TagDisplayLib.setColorToElem(this, lastUsedIndex,
                    item.getWeight(), currCssColorName, item.getTagColorType());
        }

        public void updateSize(int increment) {
            updateSize(increment, true);
        }

        /**
         * Update size of tag
         * @param increment increment by which to increase or decrease the tag's size
         * @param modifyHasClicked modify the tag that is being dragged or all the others
         * @return the increment by which the other tags need to be resized if this tag has reached it's maximum or minimum size
         */
        public double updateSize(double increment, boolean modifyHasClicked) {
            double oldSize = item.getWeight();
            double currentSize = item.getWeight();
            if (hasClicked == modifyHasClicked) {

                currentSize += increment;
                double diff = 0;
                if (Math.abs(currentSize) > MAX_SIZE) {
                    double absCurrSize = Math.abs(currentSize);
                    diff = currentSize / absCurrSize * (MAX_SIZE - absCurrSize);
                    currentSize = currentSize / absCurrSize * MAX_SIZE;
                }
                // If we're crossing from positive to negative
                if (-MIN_SIZE < currentSize && currentSize < MIN_SIZE) {
                    currentSize = MIN_SIZE * increment / Math.abs(increment);
                    diff = 0;
                }

                // If sign flip is not allowed and it happened, restore value
                if (!this.hasClicked && (oldSize * currentSize < 0)) {
                    currentSize = MIN_SIZE * oldSize / Math.abs(oldSize);
                }
                item.setWeight(currentSize);
                resetAttributes();

                return diff;
            }
            return 0;
        }

        public double getCurrentSize() {
            return item.getWeight();
        }

        public void setClickFalse() {
            hasClicked = false;
        }

        public boolean hasClicked() {
            return hasClicked;
        }

        public boolean equals(ResizableTag rT) {
            return this.getText().equals(rT.getText());
        }

        @Override
        public CloudItem getCloudItem() {
            return item;
        }

        public String getCSSColorName() {
            return currCssColorName;
        }
    }

    private class SpannedNEffectPanel extends NEffectPanel {

        public SpannedNEffectPanel() {

            setElement(DOM.createSpan());

            // Set the style name up for it.
            this.setStyleName("effectPanel");
            // Quick fix here for IE (for such a small thing it is not worth trying to
            // used deferred binding....)
            DOM.setStyleAttribute(getElement(), "zoom", "1");
            getElement().setPropertyInt("zoom", 1);
            // Initially hide the panel, allowing for any set-up required to be applied;
            // see onAttach() method
            // for where the panel is finally set to be visible - if not one, then effet
            // such as Show could
            // result in a flickering effect as the DOM is added to the browser to then
            // have visibility set to 0.
            this.setVisible(false);
        }

        public ResizableTag getTag() {
            if (getWidget() != null) {
                return (ResizableTag) getWidget();
            } else {
                return null;
            }
        }
    }

}
