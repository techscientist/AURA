/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.music.wsitm.client;

import com.extjs.gxt.ui.client.util.Params;
import com.extjs.gxt.ui.client.widget.Info;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.HistoryListener;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.MouseListener;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SuggestBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.sun.labs.aura.music.wsitm.client.items.ArtistCompact;
import com.sun.labs.aura.music.wsitm.client.items.ItemInfo;
import com.sun.labs.aura.music.wsitm.client.items.ListenerDetails;
import com.sun.labs.aura.music.wsitm.client.AbstractSearchWidget.Oracles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author mailletf
 */
public class SteeringSwidget extends Swidget implements HistoryListener {

    private MainPanel mP;

    public SteeringSwidget(ClientDataManager cdm) {
        super("Steering", cdm);
        History.addHistoryListener(this);
        mP = new MainPanel();
        registerLoginListener(mP);
        initWidget(mP);
        cdm.setSteerableReset(true);
        onHistoryChanged(History.getToken());
    }

    public List<String> getTokenHeaders() {

        List<String> l = new ArrayList<String>();
        l.add("steering:");
        return l;
    }

    protected void initMenuItem() {
        menuItem = new MenuItem("Steering",MenuItem.getDefaultTokenClickListener("steering:"),false,1);
    }

    public void onHistoryChanged(String historyToken) {
        if (historyToken.startsWith("steering:")) {
            //
            // Only reset if artist id is in querystring and we aksed
            if (historyToken.length() > 9 && cdm.getSteerableReset()) {
                cdm.setSteerableReset(false);
                mP.loadArtistCloud(historyToken.substring(9));
            }
        }
    }

    private class MainPanel extends LoginListener {

        private DockPanel dP;
        private Grid mainTagPanel;
        private Grid mainArtistListPanel;
        private TagWidgetContainer tagLand;
        private VerticalPanel savePanel;
        private SearchWidget search;
        private FlowPanel searchBoxContainerPanel;

        private FlowPanel refreshingPanel;

        private ListBox listbox;
        private String currLoadedTagWidget = "";

        public MainPanel() {
            dP = new DockPanel();

            // Left
            mainArtistListPanel = new Grid(1, 1);
            mainArtistListPanel.setWidth("300px");
            mainArtistListPanel.setWidget(0, 0, new Label("Add tags to your tag cloud to get recommendations"));

            HorizontalPanel hP = new HorizontalPanel();
            hP.setStyleName("h2");
            hP.setWidth("300px");
            hP.add(new SpannedLabel("Recommendations"));

            refreshingPanel = new FlowPanel();
            refreshingPanel.add(new Image("ajax-loader-small.gif "));
            refreshingPanel.setVisible(false);

            hP.setHorizontalAlignment(HorizontalPanel.ALIGN_RIGHT);
            hP.add(refreshingPanel);

            dP.add(WebLib.createSection(hP, mainArtistListPanel), DockPanel.WEST);

            // Right (continued lower)
            mainTagPanel = new Grid(2, 1);
            dP.add(WebLib.createSection("Add tag", mainTagPanel), DockPanel.EAST);

            // Save panel
            savePanel = new VerticalPanel();
            HorizontalPanel saveNamePanel = new HorizontalPanel();
            saveNamePanel.add(new Label("Name:"));
            saveNamePanel.add(new TextBox());
            savePanel.add(saveNamePanel);
            savePanel.add(new TagInputWidget("tag cloud"));
            savePanel.setVisible(false);


            // North
            HorizontalPanel mainNorthMenuPanel = new HorizontalPanel();
            mainNorthMenuPanel.setSpacing(5);

            Button saveButton = new Button("Save this cloud");
            saveButton.addClickListener(new ClickListener() {

                public void onClick(Widget arg0) {
                    savePanel.setVisible(!savePanel.isVisible());
                }
            });
            mainNorthMenuPanel.add(saveButton);

            Button updateButton = new Button("Update recommendations");
            updateButton.addClickListener(new ClickListener() {

                public void onClick(Widget arg0) {
                    invokeFetchNewRecommendations();
                }
            });
            mainNorthMenuPanel.add(updateButton);


            Button resetButton = new Button("Erase all tags");
            resetButton.addClickListener(new ClickListener() {

                public void onClick(Widget arg0) {
                    tagLand.removeAllTags(true);
                }
            });
            mainNorthMenuPanel.add(resetButton);

            HorizontalPanel interfaceSelectPanel = new HorizontalPanel();
            Label interfaceLabel = new SpannedLabel("Interface: ");
            listbox = new ListBox(false);
            listbox.addItem("Cloud");
            listbox.addItem("Meter");
            listbox.addChangeListener(new DataEmbededChangeListener<ListBox>(listbox) {

                public void onChange(Widget arg0) {
                    swapTagWidget(data.getItemText(data.getSelectedIndex()));
                }
            });
            interfaceSelectPanel.add(interfaceLabel);
            interfaceSelectPanel.add(listbox);
            mainNorthMenuPanel.setHorizontalAlignment(HorizontalPanel.ALIGN_RIGHT);
            mainNorthMenuPanel.add(interfaceSelectPanel);


            dP.add(mainNorthMenuPanel, DockPanel.NORTH);
            dP.add(savePanel, DockPanel.NORTH);

            //
            // North 2
            tagLand = new TagWidgetContainer(new ResizableTagWidget(this), this);
            currLoadedTagWidget = "Cloud";
            dP.add(tagLand, DockPanel.NORTH);

            // Right again
            searchBoxContainerPanel = new FlowPanel();
            mainTagPanel.setWidth("185px");
            mainTagPanel.setWidget(1, 0, new Label("Search for tags to add using the above search box"));

            search = new SearchWidget(musicServer, cdm, searchBoxContainerPanel, mainTagPanel, tagLand);
            search.updateSuggestBox(Oracles.TAG);
            mainTagPanel.setWidget(0, 0, search);

            initWidget(dP);
        }

        public void swapTagWidget(String widgetName) {
            if (!currLoadedTagWidget.equals(widgetName)) {
                if (widgetName.equals("Cloud")) {
                    tagLand.swapTagWidget(new ResizableTagWidget(this));
                    currLoadedTagWidget = "Cloud";
                } else {
                    tagLand.swapTagWidget(new TagMeterWidget(this));
                    currLoadedTagWidget = "Meter";
                }
            }
        }

        public void invokeFetchNewRecommendations() {
            AsyncCallback callback = new AsyncCallback() {

                public void onSuccess(Object result) {

                    ArtistCompact[] aCArray = (ArtistCompact[]) result;
                    mainArtistListPanel.setWidget(0, 0,
                            new ArtistCloudArtistListWidget(musicServer, cdm, aCArray, tagLand));
                    refreshingPanel.setVisible(false);

                }

                public void onFailure(Throwable caught) {
                    Window.alert(caught.getMessage());
                }
            };

            refreshingPanel.setVisible(true);;

            try {
                musicServer.getSteerableRecommendations(tagLand.getTapMap(), callback);
            } catch (Exception ex) {
                Window.alert(ex.getMessage());
            }
        }

        public void onLogin(ListenerDetails lD) {
        }

        public void onLogout() {
        }

        public void loadArtistCloud(String artistId) {
            tagLand.removeAllTags(false);
            if (artistId.startsWith("steering:")) {
                artistId = artistId.substring(artistId.indexOf(":")+1);
            }
            invokeGetDistincitveTagsService(artistId);
            invokeGetArtistCompactService(artistId);
        }

        private void invokeGetDistincitveTagsService(String artistID) {

            AsyncCallback callback = new AsyncCallback() {

                public void onSuccess(Object result) {
                    ItemInfo[] results = (ItemInfo[]) result;
                    if (results != null) {
                        if (results.length == 0) {
                            Window.alert("No tags found for artist");
                        } else {
                            tagLand.addTags(results, TagWidget.NBR_TOP_TAGS_TO_ADD);
                        }
                    } else {
                        Window.alert("An unknown error occured while loading the artist's tag cloud");
                    }
                }

                public void onFailure(Throwable caught) {
                    Window.alert(caught.getMessage());
                }
            };

            try {
                musicServer.getDistinctiveTags(artistID, 30, callback);
            } catch (Exception ex) {
                Window.alert(ex.getMessage());

            }
        }

        private void invokeGetArtistCompactService(String artistId) {

            AsyncCallback callback = new AsyncCallback() {

                public void onSuccess(Object result) {
                    ArtistCompact aC = (ArtistCompact) result;
                    if (aC != null) {
                        search.displayArtist(new ItemInfo(aC.getId(), aC.getName(), aC.getNormPopularity(), aC.getNormPopularity()));
                    }
                }

                public void onFailure(Throwable caught) {
                    Window.alert(caught.getMessage());
                }
            };

            try {
                musicServer.getArtistCompact(artistId, callback);
            } catch (Exception ex) {
                Window.alert(ex.getMessage());

            }
        }
    }

    public abstract class TagWidget extends Composite {

        public static final int NBR_TOP_TAGS_TO_ADD = 10;

        protected MainPanel mainPanel;

        public TagWidget(MainPanel mainPanel) {
            this.mainPanel = mainPanel;
        }

        public void updateRecommendations() {
            mainPanel.invokeFetchNewRecommendations();
        }

        public void addTags(Map<String, Double> tagMap, int limit) {
            if (tagMap!=null && !tagMap.isEmpty()) {
                int max = tagMap.size();
                if (limit>0 && limit<max) {
                    max = limit;
                }

                ItemInfo[] tags = new ItemInfo[max];
                int index=0;
                for (String key : tagMap.keySet()) {
                    Double val = tagMap.get(key);
                    tags[index] = new ItemInfo(ClientDataManager.nameToKey(key), key, val, val);
                    index++;
                }
                addTags(tags, limit);
            }
        }

        public abstract Map<String, Double> getTapMap();
        public abstract void addTags(ItemInfo[] tag, int limit);
        public abstract void addTag(ItemInfo tag, boolean updateRecommendations) ;
        public abstract void addTag(ItemInfo tag, double tagSize, boolean updateRecommendations);
        public abstract void removeTag(String tagId);
        public abstract void removeAllTags(boolean updateRecommendations);
        public abstract void redrawTagCloud();

    }

    public class TagWidgetContainer extends TagWidget {

        private Grid g;
        private TagWidget activeTagWidget;

        public TagWidgetContainer(TagWidget tW, MainPanel mainPanel) {
            super(mainPanel);

            this.activeTagWidget = tW;

            g = new Grid(1,1);
            g.setWidget(0, 0, activeTagWidget);
            initWidget(g);
        }

        public void swapTagWidget(TagWidget newTagWidget) {
            newTagWidget.addTags(activeTagWidget.getTapMap(), 0);
            activeTagWidget = newTagWidget;
            g.setWidget(0, 0, activeTagWidget);
        }

        public Map<String, Double> getTapMap() {
            return activeTagWidget.getTapMap();
        }

        public void addTags(ItemInfo[] tag, int limit) {
            activeTagWidget.addTags(tag, limit);
        }

        public void addTags(Map<String, Double> tagMap, int limit) {
            activeTagWidget.addTags(tagMap, limit);
        }

        public void addTag(ItemInfo tag, boolean updateRecommendations) {
            activeTagWidget.addTag(tag, updateRecommendations);
        }

        public void addTag(ItemInfo tag, double tagSize, boolean updateRecommendations) {
            activeTagWidget.addTag(tag, tagSize, updateRecommendations);
        }

        public void removeTag(String tagId) {
            activeTagWidget.removeTag(tagId);
        }

        public void removeAllTags(boolean updateRecommendations) {
            activeTagWidget.removeAllTags(updateRecommendations);
        }

        public void redrawTagCloud() {
            activeTagWidget.redrawTagCloud();
        }

    }

    public class TagMeterWidget extends TagWidget {

        private final static int MAX_TAG_VALUE = 60;
        private final static int DEFAULT_TAG_VALUE = MAX_TAG_VALUE/2;

        private VerticalPanel mainTagPanel;

        private Map<String, TagMeter> tagCloud;
        private double maxSize = 1;

        public TagMeterWidget(MainPanel mainPanel) {
            super(mainPanel);

            int panelWidth = 480;
            if (Window.getClientWidth()>1024) {
                panelWidth = (int)(Window.getClientWidth() * 480.0 / 1024.0);
            }

            mainTagPanel = new VerticalPanel();
            mainTagPanel.setWidth(panelWidth+"px");
            tagCloud = new HashMap<String, TagMeter>();
            initWidget(mainTagPanel);
        }

        public Map<String, Double> getTapMap() {
            Map<String, Double> tagMap = new HashMap<String, Double>();

            // Find the average score of positive items
            double maxScore=-100;
            double sumScorePos=0;
            int nbrPos=0;
            for (String key : tagCloud.keySet()) {
                int rating = tagCloud.get(key).getRating();
                if (rating>maxScore) {
                    maxScore = rating;
                }
                if (rating>0) {
                    sumScorePos += rating;
                    nbrPos++;
                }
            }

            for (String key : tagCloud.keySet()) {
                double rating = tagCloud.get(key).getRating();
                if (rating==0) {
                    rating = -(sumScorePos/nbrPos);
                }
                // @todo remove lowercase when funny business in engine is fixed
                tagMap.put(tagCloud.get(key).getName().toLowerCase(), rating/maxScore);
            }
            return tagMap;
        }

        /**
         * Add all supplied tags
         * @param tag
         */
        public void addTags(ItemInfo[] tag, int limit) {
            if (tag!=null && tag.length>0) {
                if (limit==0) {
                    limit = tag.length;
                }

                List<ItemInfo> iIList = ItemInfo.arrayToList(tag);
                List<ItemInfo> cutList = new ArrayList<ItemInfo>();

                // Use only the top ten tags with the biggest score
                Collections.sort(iIList, ItemInfo.getScoreSorter());
                maxSize = iIList.get(0).getScore();
                int nbr = 0;
                for (ItemInfo i : iIList) {
                    cutList.add(i);
                    if (nbr++ >= limit) {
                        break;
                    }
                }

                // Add the tags to the cloud
                for (ItemInfo i : cutList) {
                    addTag(i, i.getScore()/maxSize * MAX_TAG_VALUE, false);
                }

                DeferredCommand.addCommand(new Command() {

                    public void execute() {
                        updateRecommendations();
                    }
                });
            }
        }

        public void addTag(ItemInfo tag, boolean updateRecommendations) {
            addTag(tag, DEFAULT_TAG_VALUE, updateRecommendations);
        }

        public void addTag(ItemInfo tag, double tagSize, boolean updateRecommendations) {
            if (!tagCloud.containsKey(tag.getId())) {
                if (tagSize<0) {
                    tagSize = 0;
                }
                TagMeter tM = new TagMeter(tag, (int)tagSize, MAX_TAG_VALUE);
                mainTagPanel.add(tM);
                tagCloud.put(tag.getId(), tM);

                if (updateRecommendations) {
                    updateRecommendations();
                }
            }
        }

        public void removeTag(String tagId) {
            if (tagCloud.containsKey(tagId)) {
                mainTagPanel.remove(tagCloud.get(tagId));
                tagCloud.remove(tagId);
                updateRecommendations();
            } else {
                Window.alert(tagId+" is not in tagcloud");
            }
        }

        public void removeAllTags(boolean updateRecommendations) {
            mainTagPanel.clear();
            tagCloud.clear();
            updateRecommendations();
        }

        public void redrawTagCloud() {
            // Not applicable to this implementation
        }

        public class TagMeter extends Composite {

            private ItemInfo tag;
            private int rating;

            private Grid mainPanel;

            private NonDraggableImage[] leds;

            private TagMeter(ItemInfo tag, int initialRating, int maxRating) {
                this.tag = tag;
                this.rating = initialRating;

                mainPanel = new Grid(1,2);
                mainPanel.setWidth("100%");

                mainPanel.setWidget(0, 0, new DeletableTag(new SpannedLabel(tag.getItemName())));
                mainPanel.getCellFormatter().setHorizontalAlignment(0, 0, HorizontalPanel.ALIGN_LEFT);
                mainPanel.getCellFormatter().setHorizontalAlignment(0, 1, HorizontalPanel.ALIGN_RIGHT);
                
                mainPanel.getCellFormatter().setWidth(0, 1, "150px");

                leds = new NonDraggableImage[maxRating+1];
                HorizontalPanel meter = new HorizontalPanel();
                for (int i = 0; i < leds.length; i++) {
                    String color;
                    if (i==0) {
                        color = "red";
                    } else {
                        color = "green";
                    }
                    if ((i==0 && rating!=0) || rating<i) {
                        leds[i] = new NonDraggableImage("meter-"+color+"-off.jpg");
                    } else {
                        leds[i] = new NonDraggableImage("meter-"+color+"-on.jpg");
                    }
                    leds[i].setStyleName("noDrag");
                    leds[i].addStyleName("image");
                    leds[i].setHeight("15px");
                    leds[i].addClickListener(new DataEmbededClickListener<Integer>(i) {

                        public void onClick(Widget arg0) {
                            rating = data;
                            redrawMeter();
                            updateRecommendations();
                        }
                    });
                    leds[i].addMouseListener(new DataEmbededMouseListener<Integer>(i) {

                        public void onMouseEnter(Widget arg0) {
                            for (int i = 0; i < leds.length; i++) {
                                String color;
                                if (i == 0) {
                                    color = "red";
                                } else {
                                    color = "green";
                                }
                                if ((i == 0 && data != 0) || data < i) {
                                    if ((i == 0 && rating != 0) || rating < i) {
                                        leds[i].setUrl("meter-" + color + "-off.jpg");
                                    } else {
                                        leds[i].setUrl("meter-" + color + "-on.jpg");
                                    }
                                } else {
                                    leds[i].setUrl("meter-" + color + "-hover.jpg");
                                }
                            }
                        }

                        public void onMouseLeave(Widget arg0) {
                            redrawMeter();
                        }
                        public void onMouseDown(Widget arg0, int arg1, int arg2) {}
                        public void onMouseMove(Widget arg0, int arg1, int arg2) {}
                        public void onMouseUp(Widget arg0, int arg1, int arg2) {}
                    });

                    meter.add(leds[i]);
                }
                meter.getElement().setAttribute("style", "margin-right: 40px");
                mainPanel.setWidget(0, 1, meter);

                initWidget(mainPanel);
            }

            private void redrawMeter() {
                for (int i=0; i<leds.length; i++) {
                    String color;
                    if (i==0) {
                        color = "red";
                    } else {
                        color = "green";
                    }
                    if ((i==0 && rating!=0) || rating<i) {
                        leds[i].setUrl("meter-"+color+"-off.jpg");
                    } else {
                        leds[i].setUrl("meter-"+color+"-on.jpg");
                    }
                }
            }

            public int getRating() {
                return rating;
            }

            public String getName() {
                return tag.getItemName();
            }
        }

        public class DeletableTag extends DeletableWidget<Label> {

            private String tag;

            public DeletableTag(Label w) {
                super(w);
                this.tag = w.getText();
            }

            public void onDelete() {
                removeTag(ClientDataManager.nameToKey(tag));
            }
        }

        private class NonDraggableImage extends Image {

            public NonDraggableImage(String url) {
                super(url);
            }

            protected void onAttach() {
                WebLib.disableTextSelectInternal(this.getElement(), true);
                super.onAttach();
            }

            protected void onDetach() {
                super.onDetach();
                WebLib.disableTextSelectInternal(this.getElement(), false);
            }
        }
    }

    public class ResizableTagWidget extends TagWidget {

        private Map<String, DeletableResizableTag> tagCloud;
        private boolean hasChanged = false; // did the tagCloud change and recommendations need to be updated

        private double maxSize = 0.1;

        private Grid g;
        private FocusPanel fP;
        private FlowPanel flowP;

        private int lastX;
        private int lastY;
        
        private int colorIndex = 1;
        
        private ColorConfig[] color;

        public ResizableTagWidget(MainPanel mainPanel) {

            super(mainPanel);

            int panelWidth = 480;
            if (Window.getClientWidth()>1024) {
                panelWidth = (int)(Window.getClientWidth() * 480.0 / 1024.0);
            }

            fP = new FocusPanel();
            fP.setWidth(panelWidth+"px");
            fP.setHeight("450px");
            flowP = new FlowPanel();
            flowP.setWidth("500px");
            flowP.getElement().setAttribute("style", "margin-top: 15px");
            fP.add(flowP);
            initWidget(fP);

            color = new ColorConfig[2];
            color[0] = new ColorConfig("#D4C790", "#D49090");
            color[1] = new ColorConfig("#ADA376", "#AD7676");

            tagCloud = new HashMap<String, DeletableResizableTag>();

            fP.addMouseListener(new MouseListener() {

                public void onMouseDown(Widget arg0, int arg1, int arg2) {
                    lastX = arg1;
                    lastY = arg2;
                }

                public void onMouseEnter(Widget arg0) {
                }

                public void onMouseLeave(Widget arg0) {
                    boolean wasTrue = false;
                    for (DeletableWidget<ResizableTag> dW : tagCloud.values()) {
                        if (dW.getWidget().hasClicked()) {
                            wasTrue = true;
                        }
                        dW.getWidget().setClickFalse();
                    }
                    if (wasTrue) {
                        updateRecommendations();
                    }
                }

                public void onMouseMove(Widget arg0, int arg1, int arg2) {

                    int increment = lastY - arg2;

                    // Don't refresh everytime to let the browser take its breath
                    if (Math.abs(increment) > 3) {

                        double diff = 0;
                        maxSize = 0; // reset maxsize to deal with when the top tag is scaled down
                        for (DeletableResizableTag dW : tagCloud.values()) {
                            double oldSize = dW.getWidget().getCurrentSize();
                            double tempDiff = dW.getWidget().updateSize(increment, true);

                            if (oldSize != dW.getWidget().getCurrentSize()) {
                                hasChanged = true;

                                dW.setXButtonPosition();
                            }

                            if (tempDiff != 0) {
                                diff = tempDiff;
                            }

                            if (Math.abs(dW.getWidget().getCurrentSize())>maxSize) {
                                maxSize = Math.abs(dW.getWidget().getCurrentSize());
                            }
                        }

                        //
                        // Do a second pass to modify the tags that aren't being resized
                        // if the one that is resized has reached its max/min size
                        if (diff != 0) {
                            diff = diff / (tagCloud.size()-1);
                            for (DeletableResizableTag dW : tagCloud.values()) {
                                double oldSize = dW.getWidget().getCurrentSize();
                                dW.getWidget().updateSize(diff, false);

                                if (oldSize != dW.getWidget().getCurrentSize()) {
                                    hasChanged = true;

                                    dW.setXButtonPosition();
                                }

                                if (Math.abs(dW.getWidget().getCurrentSize())>maxSize) {
                                    maxSize = Math.abs(dW.getWidget().getCurrentSize());
                                }
                            }
                        }

                        lastX = arg1;
                        lastY = arg2;
                    }
                }

                public void onMouseUp(Widget arg0, int arg1, int arg2) {
                    for (DeletableWidget<ResizableTag> dW : tagCloud.values()) {
                        dW.getWidget().setClickFalse();
                    }
                    updateRecommendations();
                }
            });

        }

        public void updateRecommendations() {
            if (hasChanged) {
                hasChanged = false;
                super.updateRecommendations();
            }
        }

        protected void onAttach() {
            WebLib.disableTextSelectInternal(this.getElement(), true);
            super.onAttach();
        }

        protected void onDetach() {
            super.onDetach();
            WebLib.disableTextSelectInternal(this.getElement(), false);
        }

        public Map<String, Double> getTapMap() {
            Map<String, Double> tagMap = new HashMap<String, Double>();
            // Add the tags normalised by the size of the biggest one
            for (String tag : tagCloud.keySet()) {
                // @todo remove lowercase when engine is fixed
                tagMap.put(tagCloud.get(tag).getWidget().getText().toLowerCase(), tagCloud.get(tag).getWidget().getCurrentSize()/maxSize);
            }
            return tagMap;
        }

        /**
         * Add all supplied tags
         * @param tag
         */
        public void addTags(ItemInfo[] tag, int limit) {
            if (tag!=null && tag.length>0) {
                if (limit==0) {
                    limit = tag.length;
                }

                int avgSizeOfAddedCloud = 40;

                List<ItemInfo> iIList = ItemInfo.arrayToList(tag);
                List<ItemInfo> cutList = new ArrayList<ItemInfo>();

                // Use only the top ten tags with the biggest score
                Collections.sort(iIList, ItemInfo.getScoreSorter());
                maxSize = iIList.get(0).getScore();
                int nbr = 0;
                for (ItemInfo i : iIList) {
                    cutList.add(i);
                    if (nbr++ >= limit) {
                        break;
                    }
                }

                double sumScore = 0;
                for (ItemInfo i : cutList) {
                    sumScore += i.getScore();
                }

                // Find the size of the biggest tag so that the average size of the
                // added tags match avgSizeOfAddedCloud
                double maxTagSize = avgSizeOfAddedCloud * cutList.size() / sumScore;

                // Add the tags to the cloud
                Collections.sort(cutList, ItemInfo.getRandomSorter());
                for (ItemInfo i : cutList) {
                    double score;
                    if (i.getScore() < 0) {
                        score = -0.6;
                    } else {
                        score = i.getScore();
                    }
                    addTag(i, score * maxTagSize, false);
                }

                hasChanged = true;
                DeferredCommand.addCommand(new Command() {
                    public void execute() {
                        updateRecommendations();
                    }
                });
            }
        }

        public void addTag(ItemInfo tag, boolean updateRecommendations) {
            addTag(tag, 0, updateRecommendations);
        }

        public void addTag(ItemInfo tag, double tagSize, boolean updateRecommendations) {
            if (!tagCloud.containsKey(tag.getId())) {
                ResizableTag rT;
                if (tagSize != 0) {
                    rT = new ResizableTag(tag.getItemName(), color[(colorIndex++)%2], tagSize);
                } else {
                    rT = new ResizableTag(tag.getItemName(), color[(colorIndex++)%2]);
                }
                DeletableResizableTag dW = new DeletableResizableTag(rT);

                tagCloud.put(tag.getId(), dW);
                flowP.add(dW);
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
            }
        }

        public void removeTag(String tagId) {
            if (tagCloud.containsKey(tagId)) {
                flowP.remove(tagCloud.get(tagId));
                tagCloud.remove(tagId);
                redrawTagCloud();

                hasChanged = true;
                updateRecommendations();
            }
        }

        public void removeAllTags(boolean updateRecommendations) {
            tagCloud.clear();
            flowP.clear();
            colorIndex=1;

            hasChanged = true;
            if (updateRecommendations) {
                updateRecommendations();
            }
        }

        public void redrawTagCloud() {
            colorIndex = 1;
            for (DeletableWidget<ResizableTag> dW : tagCloud.values()) {
                dW.getWidget().updateColor(color[(colorIndex++)%2], true);
            }
        }

        public class DeletableResizableTag extends DeletableWidget<ResizableTag> {

            public DeletableResizableTag(ResizableTag t) {
                super(t);

                xB.getElement().setAttribute("style", "margin-bottom: "+getXButtonMargin()+"px;");
            }

            private final double getXButtonMargin() {
                return Math.abs(getWidget().getCurrentSize())*0.5;
            }

            public void setXButtonPosition() {
                String displayAttrib = xB.getElement().getAttribute("style");
                String newStyle = "";
                for (String s : displayAttrib.split(";")) {
                    String[] sSplit = s.split(":");
                    if (sSplit[0].trim().equals("margin-bottom")) {
                        newStyle += "margin-bottom:"+getXButtonMargin()+"px;";
                    } else {
                        newStyle += s+";";
                    }
                }
                xB.getElement().setAttribute("style", newStyle);
            }

            public void onDelete() {
                removeTag(ClientDataManager.nameToKey(getWidget().getText()));
            }
        }

        public class ResizableTag extends SpannedLabel {

            private boolean hasClicked = false;
            private FocusPanel fP;
            private double currentSize = 40;
            private ColorConfig color;

            private static final int MIN_SIZE = 4;
            private static final int MAX_SIZE = 175;

            public ResizableTag(String txt, ColorConfig color) {
                super(txt);
                initialize(color);
            }

            public ResizableTag(String txt, ColorConfig color, double initialSize) {
                super(txt);
                currentSize = initialSize;
                initialize(color);
            }

            private void initialize(ColorConfig color) {
                addStyleName("marginRight");
                addStyleName("hand");
                this.color = color;
                resetAttributes();

                addMouseListener(new MouseListener() {

                    public void onMouseDown(Widget arg0, int arg1, int arg2) {
                        hasClicked = true;
                    }

                    public void onMouseUp(Widget arg0, int arg1, int arg2) {
                        hasClicked = false;
                    }

                    public void onMouseEnter(Widget arg0) {}
                    public void onMouseLeave(Widget arg0) {}
                    public void onMouseMove(Widget arg0, int arg1, int arg2) {}
                });
            }

            private final void resetAttributes() {
                getElement().setAttribute("style", "-moz-user-select: none; -khtml-user-select: none; user-select: none; font-size:"+Math.abs(currentSize)+"px; color:"+color.getColor(currentSize)+";");
            }

            public void updateColor(ColorConfig color, boolean allowSignFlip) {
                this.color = color;
                updateSize(0, true);
            }

            public void updateSize(int increment, ColorConfig color) {
                this.color = color;
                updateSize(increment, true);
            }

            /**
             * Update size of tag
             * @param increment increment by which to increase or decrease the tag's size
             * @param modifyHasClicked modify the tag that is being dragged or all the others
             * @return the increment by which the other tags need to be resized if this tag has reached it's maximum or minimum size
             */
            public double updateSize(double increment, boolean modifyHasClicked) {
                double oldSize = currentSize;
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

                    resetAttributes();

                    return diff;
                }
                return 0;
            }

            public double getCurrentSize() {
                return currentSize;
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
        }

        public class ColorConfig {

            private String positive;
            private String negative;

            public ColorConfig(String positive, String negative) {
                this.positive = positive;
                this.negative = negative;
            }

            /**
             * Return the right color based on the current size of the item.
             * @param size
             * @return
             */
            public final String getColor(double size) {
                if (size<0) {
                    return negative;
                } else {
                    return positive;
                }
            }
        }
    }

    public class ItemInfoHierarchyWidget extends Composite {

        private Grid mainGrid;
        private ItemInfo[] mainItems;
        private ItemInfo[] subItems = null;

        private TagWidget tagLand;

        public ItemInfoHierarchyWidget(ItemInfo[] iI, TagWidget tagLand) {
            mainItems = iI;
            this.tagLand = tagLand;

            mainGrid = new Grid(2,1);
            if (mainItems.length>1) {
                displayMainItems();
            } else {
                displayDetails(mainItems[0], false);
            }

            initWidget(mainGrid);
        }

        /**
         * Called when user clicks on one of the main items. Will display the
         * details for this item along with a header allowing the user to go back
         * to the main item list
         * @param iI
         */
        public void displayDetails(ItemInfo iI, boolean showBackButton) {
            mainGrid.setWidget(1, 0, WebLib.getLoadingBarWidget());
            invokeGetDistincitveTagsService(iI.getId());

            VerticalPanel hP = new VerticalPanel();
            hP.setStyleName("pageHeader");
            hP.setWidth("185px");

            HorizontalPanel smallMenuPanel = new HorizontalPanel();
            smallMenuPanel.setSpacing(4);
            if (showBackButton) {
                Label backButton = new Label("Back");
                backButton.setStyleName("headerMenuTinyItem");
                backButton.addClickListener(new ClickListener() {

                    public void onClick(Widget arg0) {
                        displayMainItems();
                    }
                });
                smallMenuPanel.add(backButton);
                smallMenuPanel.add(new SpannedLabel("   "));
            }
            Label addAllButton = new Label("Add top tags");
            addAllButton.setStyleName("headerMenuTinyItem");
            addAllButton.addClickListener(new ClickListener() {

                public void onClick(Widget arg0) {
                    if (subItems != null) {
                        tagLand.addTags(subItems, TagWidget.NBR_TOP_TAGS_TO_ADD);
                    } else {
                        Info.display("Add all tags", "subitems is null", new Params());
                    }
                }
            });
            smallMenuPanel.add(addAllButton);
            hP.add(smallMenuPanel);

            Label title = new Label("Tags for "+iI.getItemName());
            hP.add(title);
            mainGrid.setWidget(0, 0, hP);
        }

        public void displayMainItems() {

            subItems = null;
            VerticalPanel vP = new VerticalPanel();

            Label explanation = new Label("Click on an artist's name to display its most distinctive tags");
            explanation.setStyleName("smallItalicExplanation");
            explanation.getElement().setAttribute("style", "margin-bottom: 5px");
            vP.add(explanation);
            
            for (ItemInfo item : mainItems) {
                Label itemName = new Label(item.getItemName());
                itemName.setStyleName("pointer");
                itemName.addClickListener(new DataEmbededClickListener<ItemInfo>(item) {

                    public void onClick(Widget arg0) {
                        displayDetails(data, true);
                    }
                });
                vP.add(itemName);
            }
            mainGrid.setWidget(0, 0, vP);
            mainGrid.setWidget(1, 0, new Label(""));
        }

        private void invokeGetDistincitveTagsService(String artistID) {

            AsyncCallback callback = new AsyncCallback() {

                public void onSuccess(Object result) {
                    ItemInfo[] results = (ItemInfo[]) result;
                    if (results != null) {
                        if (results.length == 0) {
                            mainGrid.setWidget(1, 0, new Label("No tags found"));
                        } else {
                            subItems = results;
                            mainGrid.setWidget(1, 0, new SortableItemInfoList(results) {

                                protected void onItemClick(ItemInfo i) {
                                    tagLand.addTag(i, true);
                                }
                            });
                        }
                    } else {
                        mainGrid.setWidget(1, 0, new Label("An unknown error occured."));
                    }
                }

                public void onFailure(Throwable caught) {
                    Window.alert(caught.getMessage());
                }
            };

            try {
                musicServer.getDistinctiveTags(artistID, 30, callback);
            } catch (Exception ex) {
                Window.alert(ex.getMessage());

            }
        }
    }

    public abstract class SortableItemInfoList extends Composite {

        private Grid mainPanel;
        private List<ItemInfo> iI;

        private double maxValue = 0;

        public SortableItemInfoList(ItemInfo[] iI) {
            this.iI = ItemInfo.arrayToList(iI);

            mainPanel = new Grid(iI.length + 1, 2);

            //
            // Add the title line
            Label nameLbl = new Label("Name");
            nameLbl.addClickListener(new ClickListener() {

                public void onClick(Widget arg0) {
                    ((Label)mainPanel.getWidget(0, 0)).setText("Name");
                    ((Label)mainPanel.getWidget(0, 1)).setText("Popularity*");
                    populateMainPanel(ItemInfo.getNameSorter());
                }
            });
            mainPanel.setWidget(0, 0, nameLbl);

            Label popLbl = new Label("Popularity*");
            popLbl.addClickListener(new ClickListener() {

                public void onClick(Widget arg0) {
                    ((Label)mainPanel.getWidget(0, 0)).setText("Name");
                    ((Label)mainPanel.getWidget(0, 1)).setText("Popularity*");
                    populateMainPanel(ItemInfo.getPopularitySorter());
                }
            });
            mainPanel.setWidget(0, 1, popLbl);

            populateMainPanel(ItemInfo.getPopularitySorter());
            initWidget(mainPanel);
        }

        private void populateMainPanel(Comparator<ItemInfo> c) {

            // Add all the items
            Collections.sort(iI, c);
            int lineIndex = 1;
            // Normalise by this result set's biggest value. The first time this function
            // is called, it will be with a popularity sorter giving us the maxValue
            if (maxValue == 0) {
                maxValue = iI.get(0).getPopularity();
            }
            for (ItemInfo i : iI) {
                Label tagLbl = new Label(i.getItemName());
                tagLbl.addClickListener(new DataEmbededClickListener<ItemInfo>(i) {

                    public void onClick(Widget arg0) {
                        onItemClick(data);
                    }
                });
                tagLbl.setStyleName("smallTagClick");
                mainPanel.setWidget(lineIndex, 0, tagLbl);
                mainPanel.setWidget(lineIndex, 1, WebLib.getSmallPopularityWidget(i.getPopularity()/maxValue, 75, true, false));
                lineIndex++;
            }
        }

        protected abstract void onItemClick(ItemInfo i);

    }

    public class SearchWidget extends AbstractSearchWidget {

        private Grid mainTagPanel;
        private TagWidget tagLand;

        public SearchWidget(MusicSearchInterfaceAsync musicServer,
            ClientDataManager cdm, Panel searchBoxContainerPanel, Grid mainTagPanel,
            TagWidget tagLand) {

            super(musicServer, cdm, searchBoxContainerPanel);

            searchBoxStyleName="";

            this.mainTagPanel = mainTagPanel;
            this.tagLand = tagLand;

            textBox = new SuggestBox();
            textBox.setTabIndex(1);
            setSuggestBoxWidth(50);

            searchBoxContainerPanel.add(WebLib.getLoadingBarWidget());

            HorizontalPanel searchType = new HorizontalPanel();
            searchType.setSpacing(5);
            searchButtons = new SearchTypeRadioButton[2];
            searchButtons[0] = new SearchTypeRadioButton("searchType", "For Tag", searchTypes.SEARCH_FOR_TAG_BY_TAG);
            searchButtons[1] = new SearchTypeRadioButton("searchType", "By Artist", searchTypes.SEARCH_FOR_ARTIST_BY_ARTIST);
            searchButtons[0].setChecked(true);

            searchButtons[1].addClickListener(new ClickListener() {
                public void onClick(Widget arg0) {
                    updateSuggestBox(Oracles.ARTIST);
                }
            });
            searchButtons[0].addClickListener(new ClickListener() {
                public void onClick(Widget arg0) {
                    updateSuggestBox(Oracles.TAG);
                }
            });

            setText("", searchTypes.SEARCH_FOR_TAG_BY_TAG);
            updateSuggestBox(Oracles.TAG);

            for (int i = 0; i < searchButtons.length; i++) {
                searchType.add(searchButtons[i]);
                searchButtons[i].getElement().setAttribute("style", "font-size: 12px");
            }
            searchType.setWidth("100%");
            searchType.setStyleName("searchPanel");

            VerticalPanel searchPanel = new VerticalPanel();
            searchPanel.setHorizontalAlignment(HorizontalPanel.ALIGN_CENTER);

            VerticalPanel leftP = new VerticalPanel();
            leftP.setHeight("100%");
            leftP.setWidth("100%");
            leftP.setHorizontalAlignment(HorizontalPanel.ALIGN_CENTER);
            leftP.setVerticalAlignment(VerticalPanel.ALIGN_MIDDLE);
            leftP.add(searchBoxContainerPanel);
            searchPanel.add(leftP);
            searchPanel.add(searchType);
            this.initWidget(searchPanel);
            this.setWidth("185px");
        }

        public void search() {
            mainTagPanel.setWidget(1, 0, WebLib.getLoadingBarWidget());
            if (getCurrLoadedOracle() == Oracles.TAG) {
                invokeTagSearchService(textBox.getText().toLowerCase());
            } else {
                invokeArtistSearchService(textBox.getText().toLowerCase());
            }
        }

        /**
         * Display the supplied artist's distincitve tags
         * @param a
         */
        public void displayArtist(ItemInfo a) {
            ItemInfo[] aA = new ItemInfo[1];
            aA[0] = a;
            mainTagPanel.setWidget(1, 0, new ItemInfoHierarchyWidget(aA, tagLand));
        }

        private void invokeArtistSearchService(String searchText) {

            AsyncCallback callback = new AsyncCallback()  {

                public void onSuccess(Object result) {
                    SearchResults sr = (SearchResults) result;
                    if (sr != null && sr.isOK()) {
                        ItemInfo[] results = sr.getItemResults();
                        if (results.length == 0) {
                            mainTagPanel.setWidget(1, 0, new Label("No Match for " + sr.getQuery()));
                        } else {
                            mainTagPanel.setWidget(1, 0, new ItemInfoHierarchyWidget(results, tagLand));
                        }
                    } else {
                        if (sr == null) {
                            Window.alert("Error. Resultset is null. There were probably no tags found.");
                        } else {
                            Window.alert("Whoops " + sr.getStatus());
                        }
                    }
                }

                public void onFailure(Throwable caught) {
                    Window.alert(caught.getMessage());
                }
            };

            try {
                musicServer.artistSearch(searchText, 10, callback);
            } catch (Exception ex) {
                Window.alert(ex.getMessage());

            }
        }

        private void invokeTagSearchService(String searchText) {

            AsyncCallback callback = new AsyncCallback()  {

                public void onSuccess(Object result) {
                    SearchResults sr = (SearchResults) result;
                    if (sr != null && sr.isOK()) {
                        ItemInfo[] results = sr.getItemResults();
                        if (results.length == 0) {
                            mainTagPanel.setWidget(1, 0, new Label("No Match for " + sr.getQuery()));
                        } else {
                            mainTagPanel.setWidget(1, 0, new SortableItemInfoList(results) {

                                protected void onItemClick(ItemInfo i) {
                                    tagLand.addTag(i, true);
                                }
                            });
                        }
                    } else {
                        if (sr == null) {
                            Window.alert("Error. Resultset is null. There were probably no tags found.");
                        } else {
                            Window.alert("Whoops " + sr.getStatus());
                        }
                    }
                }

                public void onFailure(Throwable caught) {
                    Window.alert(caught.getMessage());
                }
            };

            mainTagPanel.setWidget(1, 0, WebLib.getLoadingBarWidget());

            try {
                musicServer.tagSearch(searchText, 100, callback);
            } catch (Exception ex) {
                Window.alert(ex.getMessage());
            }
        }
    }

    public class ArtistCloudArtistListWidget extends ArtistListWidget {

        private TagWidget tagLand;

        public ArtistCloudArtistListWidget(MusicSearchInterfaceAsync musicServer,
                ClientDataManager cdm, ArtistCompact[] aDArray, TagWidget tagLand) {

            super(musicServer, cdm, aDArray);
            this.tagLand = tagLand;
        }

        public void onTagClick(ItemInfo tag) {
            tagLand.addTag(tag, true);
        }

        public void openWhyPopup(WhyButton why) {
            why.showLoad();
            TagDisplayLib.invokeGetCommonTags(tagLand.getTapMap(), why.getId(),
                    musicServer, cdm, new CommonTagsAsyncCallback(why) {});
        }
    }
}