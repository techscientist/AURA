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

package com.sun.labs.aura.music.wsitm.client.ui.swidget;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.sun.labs.aura.music.wsitm.client.ui.Popup;
import com.sun.labs.aura.music.wsitm.client.ui.TagDisplayLib;
import com.sun.labs.aura.music.wsitm.client.ui.MenuItem;
import com.sun.labs.aura.music.wsitm.client.event.CommonTagsAsyncCallback;
import com.sun.labs.aura.music.wsitm.client.ui.widget.StarRatingWidget;
import com.sun.labs.aura.music.wsitm.client.*;
import com.sun.labs.aura.music.wsitm.client.ui.widget.TagInputWidget;
import com.sun.labs.aura.music.wsitm.client.ui.Updatable;
import com.sun.labs.aura.music.wsitm.client.ui.widget.SteeringWheelWidget;
import com.sun.labs.aura.music.wsitm.client.ui.widget.ArtistListWidget;
import com.sun.labs.aura.music.wsitm.client.items.TagDetails;
import com.sun.labs.aura.music.wsitm.client.items.ArtistPhoto;
import com.sun.labs.aura.music.wsitm.client.items.AlbumDetails;
import com.sun.labs.aura.music.wsitm.client.items.ItemInfo;
import com.sun.labs.aura.music.wsitm.client.items.ArtistDetails;
import com.sun.labs.aura.music.wsitm.client.items.ArtistEvent;
import com.sun.labs.aura.music.wsitm.client.items.ArtistVideo;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.LoadListener;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.sun.labs.aura.music.wsitm.client.event.DDEClickHandler;
import com.sun.labs.aura.music.wsitm.client.event.DEAsyncCallback;
import com.sun.labs.aura.music.wsitm.client.event.DEClickHandler;
import com.sun.labs.aura.music.wsitm.client.event.HasListeners;
import com.sun.labs.aura.music.wsitm.client.ui.widget.AbstractSearchWidget.searchTypes;
import com.sun.labs.aura.music.wsitm.client.items.ArtistCompact;
import com.sun.labs.aura.music.wsitm.client.items.ScoredC;
import com.sun.labs.aura.music.wsitm.client.ui.PerformanceTimer;
import com.sun.labs.aura.music.wsitm.client.ui.TagDisplayLib.TagColorType;
import com.sun.labs.aura.music.wsitm.client.ui.bundles.ArtistRelatedBundle;
import com.sun.labs.aura.music.wsitm.client.ui.widget.ContextMenuSteeringWheelWidget;
import com.sun.labs.aura.music.wsitm.client.ui.widget.DualRoundedPanel;
import com.sun.labs.aura.music.wsitm.client.ui.widget.PlayButton;
import com.sun.labs.aura.music.wsitm.client.ui.widget.PopularitySelect;
import java.util.ArrayList;
import java.util.Map;
import org.adamtacy.client.ui.NEffectPanel;
import org.adamtacy.client.ui.effects.impl.Fade;

/**
 *
 * @author plamere
 */
public class SimpleSearchSwidget extends Swidget implements HasListeners {

    public static ArtistRelatedBundle playImgBundle =
            (ArtistRelatedBundle) GWT.create(ArtistRelatedBundle.class);

    private Widget curResult;
    private String curResultToken = "";
    private String curPageTitle = "";

    private DockPanel mainPanel;

    private PopularitySelect popSelect;
    
    // Widgets that contain listeners that need to be removed to prevent leaks
    private ArtistListWidget leftRecList;
    private ArtistListWidget leftSimList;
    private ArtistListWidget leftRelList;
    private StarRatingWidget artistStar;
    private TagInputWidget tagInputWidget;
    private PlayButton playButton;

    public SimpleSearchSwidget(ClientDataManager cdm) {
        super("Simple Search", cdm);
        try {
            initWidget(getWidget(), true);
            showResults(History.getToken());
        } catch (Exception e) {
            Popup.showErrorPopup(e, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                    "Server problem. Please try again later.",
                    Popup.ERROR_LVL.CRITICAL, null);
        }
    }

    /** Creates a new instance of SimpleSearchWidget */
    @Override
    public Widget getWidget() {

        //searchBoxContainerPanel = new FlowPanel();
        
        //search = new SearchWidget(musicServer, cdm, searchBoxContainerPanel);
        //search.updateSuggestBox(Oracles.ARTIST);

        mainPanel = new DockPanel();
        mainPanel.setWidth("100%");
        mainPanel.setHorizontalAlignment(DockPanel.ALIGN_CENTER);
        mainPanel.add(new Label(), DockPanel.NORTH);

        return mainPanel;
    }

    private void setResults(String historyName, Widget result) {
        if (curResult == result || curResultToken.equals(historyName)) {
            return;
        } 

        if (!History.getToken().equals(historyName)) {
            curResultToken = historyName;
            History.newItem(historyName, false);
        }
        
        if (curResult != null) {
            mainPanel.remove(curResult);
            curResult = null;
        }

        if (result != null) {
            cdm.setCurrSearchWidgetToken(historyName);
            mainPanel.add(result, DockPanel.CENTER);
            curResult = result;
            curResultToken = historyName;
        }// else {
        //    search.setText("");
        //}
    }

    private void clearResults() {
        setResults("artist:",new Label(""));
    }

    @Override
    public ArrayList<String> getTokenHeaders() {

        ArrayList<String> l = new ArrayList<String>();
        l.add("artist:");
        l.add("tag:");
        l.add("artistSearch:");
        l.add("artistSearchByTag:");
        l.add("tagSearch:");
        return l;
    }

    @Override
    protected void initMenuItem() {
        menuItem = new MenuItem("Exploration",new ClickHandler() {
                @Override
                public void onClick(ClickEvent event) {
                    History.newItem(cdm.getCurrSearchWidgetToken());
                }
            },false,0);
    }

    @Override
    public void doRemoveListeners() {

        if (leftRelList != null) {
            leftRelList.doRemoveListeners();
        }
        if (leftSimList != null) {
            leftSimList.doRemoveListeners();
        }
        if (leftRelList != null) {
            leftRelList.doRemoveListeners();
        }

        if (artistStar != null) {
            artistStar.onDelete();
        }

        if (tagInputWidget != null) {
            tagInputWidget.onDelete();
        }
        
        if (playButton != null) {
            playButton.onDelete();
        }
    }

    private void showResults(String resultName) {

        // Reset current artistID. Will be updated in invokeGetArtistInfo
        cdm.setCurrArtistInfo("", "");

        // Clear all listeners
        doRemoveListeners();

        // Reset the search
        cdm.getSearchAttentionManager().resetSearch();

        if (resultName.startsWith("artist:")) {
            invokeGetArtistInfo(resultName, false, null);
        } else if (resultName.startsWith("tag:")) {
            invokeGetTagInfo(resultName, false);
        } else if (resultName.startsWith("artistSearch:")) {
            String query = resultName.replaceAll("artistSearch:", "");
            invokeArtistSearchService(query, searchTypes.SEARCH_FOR_ARTIST_BY_ARTIST, 0);
        } else if (resultName.startsWith("artistSearchByTag:")) {
            String query = resultName.replaceAll("artistSearchByTag:", "");
            invokeArtistSearchService(query, searchTypes.SEARCH_FOR_ARTIST_BY_TAG, 0);
        } else if (resultName.startsWith("tagSearch:")) {
            String query = resultName.replaceAll("tagSearch:", "");
            invokeTagSearchService(query, 0);
        } else if (resultName.startsWith("searchHome:")) {
            cdm.setCurrSearchWidgetToken("searchHome:");
            History.newItem("searchHome:");
        }
    }

    @Override
    public void update(String historyToken) {
        // Only update results if the history token is different than the
        // currently loaded page
        if (!curResultToken.equals(historyToken)) {
            updateWindowTitleLocal("");
            showResults(historyToken);
        } else {
            updateWindowTitleLocal(curPageTitle);
        }
    }

    private void updateWindowTitleLocal(String title) {
        curPageTitle = title;
        updateWindowTitle(title);
    }

    private void invokeTagSearchService(String searchText, int page) {

        AsyncCallback callback = new AsyncCallback() {

            public void onSuccess(Object result) {
                // do some UI stuff to show success
                SearchResults sr = (SearchResults) result;
                if (sr != null && sr.isOK()) {
                    ItemInfo[] results = sr.getItemResults(cdm);
                    if (results.length == 0) {
                        showTopMessage("No Match for " + sr.getQuery());
                        clearResults();
                    } else if (results.length == 1) {
                        WebLib.trackPageLoad("#tagSearch:" + sr.getQuery());
                        ItemInfo ar = results[0];
                        invokeGetTagInfo(ar.getId(), false);
                    } else {
                        showTopMessage("Found " + sr.getItemResults(cdm).length + " matches");
                        setResults(sr.toString(), getItemInfoList("Pick one: ", sr.getItemResults(cdm), null, false, true, cdm.getTagOracle()));
                    }
                } else {
                    if (sr == null) {
                        showTopMessage("Error. Resultset is null. There were probably no tags found.");
                        clearResults();
                    } else {
                        showTopMessage("Whoops " + sr.getStatus());
                        clearResults();
                    }
                }
            }

            public void onFailure(Throwable caught) {
                Popup.showErrorPopup(caught, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                        "perform tag search.", Popup.ERROR_LVL.NORMAL, null);
            }
        };

        updateWindowTitleLocal("Tag search");
        showLoader();

        try {
            musicServer.tagSearch(searchText, 100, callback);
        } catch (Exception ex) {
            Popup.showErrorPopup(ex, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                "perform tag search.", Popup.ERROR_LVL.NORMAL, null);
        }
    }

    private void invokeArtistSearchService(final String searchText, searchTypes sT, int page) {
        AsyncCallback callback = new AsyncCallback() {

            public void onSuccess(Object result) {

                SearchResults sr = (SearchResults) result;
                if (sr != null && sr.isOK()) {
                    ItemInfo[] results = sr.getItemResults(cdm);
                    if (results.length == 0) {
                        showTopMessage("No Match for " + sr.getQuery());
                        clearResults();
                    } else if (results.length == 1) {
                        WebLib.trackPageLoad("#artistSearch:" + sr.getQuery());
                        cdm.getSearchAttentionManager().processUserClick(results[0].getId());
                        invokeGetArtistInfo(results[0].getId(), false, null);
                    } else {
                        // Did we get back an exact match of our query? If so
                        // foward to user to that artist and offer him the choice
                        // to see results
                        String lowerSearch = searchText.toLowerCase();
                        for (ItemInfo iI : sr.getItemResults(null)) {
                            if (lowerSearch.equals(iI.getItemName().toLowerCase())) {
                                WebLib.trackPageLoad("#artistSearch:" + sr.getQuery());
                                cdm.getSearchAttentionManager().processUserClick(iI.getId());
                                invokeGetArtistInfo(iI.getId(), false, sr);
                                return;
                            }
                        }
                        searchResultsToArtistList(sr);
                    }
                } else {
                    if (sr == null) {
                        showTopMessage("Error. Can't find artist specified.");
                        clearResults();
                    } else {
                        showTopMessage("Very Whooops " + sr.getStatus());
                        clearResults();
                    }
                }
            }

            public void onFailure(Throwable caught) {
                Popup.showErrorPopup(caught, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                    "perform artist search.", Popup.ERROR_LVL.NORMAL, null);
            }
        };

        updateWindowTitleLocal("Artist search");
        showLoader();
        try {
            if (sT == searchTypes.SEARCH_FOR_ARTIST_BY_TAG) {
                musicServer.artistSearchByTag(searchText, 100, callback);
            } else if (sT == searchTypes.SEARCH_FOR_ARTIST_BY_ARTIST) {
                musicServer.artistSearch(searchText, 100, callback);
            } else {
                Popup.showInformationPopup("Error. Invalid search type.");
            }
        } catch (Exception ex) {
            Popup.showErrorPopup(ex, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                    "perform search.", Popup.ERROR_LVL.NORMAL, null);
        }
    }

    /**
     * Child method of invokeArtistSearchService
     * @param sr
     */
    private void searchResultsToArtistList(SearchResults sr) {
        showTopMessage("Found " + sr.getItemResults(cdm).length + " matches");
        Widget searchResults = getItemInfoList("Pick one: ", sr.getItemResults(cdm), null, true, true, cdm.getArtistOracle());
        searchResults.setStyleName("searchResults");
        searchResults.setWidth("300px");
        setResults(sr.toString(), searchResults);
    }




    private void invokeGetArtistInfo(String artistID, boolean refresh, SearchResults listResults) {
        PerformanceTimer.start("invokeGetArtistInfo");
        //
        // If we are currently fetching the similarity type, we can't fetch the
        // artist's info yet so let's try again in 250ms
        if (cdm.getCurrSimTypeName() == null || cdm.getCurrSimTypeName().equals("")) {
            Timer t = new TimerWithArtist(artistID, refresh);
            t.schedule(250);
        } else {
            if (artistID.startsWith("artist:")) {
                artistID = artistID.replaceAll("artist:", "");
            }

            AsyncCallback callback = new DEAsyncCallback<SearchResults, ArtistDetails>(listResults) {

                public void onSuccess(ArtistDetails artistDetails) {
                    PerformanceTimer.stop("getArtistDetails");
                    // do some UI stuff to show success
                    if (artistDetails != null && artistDetails.isOK()) {
                        PerformanceTimer.start("createArtistPanel");
                        cdm.setCurrArtistInfo(artistDetails.getId(), artistDetails.getName());
                        Widget artistPanel = createArtistPanel(artistDetails);
                        //search.setText(artistDetails.getName(), searchTypes.SEARCH_FOR_ARTIST_BY_ARTIST);
                        //search.updateSuggestBox(Oracles.ARTIST);
                        setResults("artist:" + artistDetails.getId(), artistPanel);
                        hideLoader();

                        showTopMessage(new Label(
                        "Artist bio is displayed below.  Click any tag for details. " +
                        "Similar artists are shown on the left.  See where they overlap " +
                        "with \"why?\".  The big steering wheel on the right lets you "+
                        "customize the recommended artists."));

                        // If we embeded a search result, a serch was made and although aura returned
                        // multiple results, one of them matched exacly. Offer to display the full list
                        if (data!=null) {
                            int length = data.getItemResults(null).length;
                            Label l = new Label("Not the artist you were looking for? Show "+
                                    String.valueOf(length-1)+" similar result"+(length>2 ? "s" : ""));
                            l.setStyleName("pointer topMsgIndicatorSmall");
                            l.addClickHandler(new ClickHandler() {
                                @Override
                                public void onClick(ClickEvent event) {
                                    searchResultsToArtistList(data);
                                }
                            });
                            showTopMessage(l);
                        }

                        PerformanceTimer.stop("createArtistPanel");
                    } else {
                        if (artistDetails == null) {
                            showTopMessage("Sorry. The details for the artist don't seem to be in our database.");
                            clearResults();
                        } else {
                            showTopMessage("Whooops " + artistDetails.getStatus());
                            clearResults();
                        }
                    }
                    PerformanceTimer.stop("invokeGetArtistInfo");
                }

                public void onFailure(Throwable caught) {
                    PerformanceTimer.stop("getArtistDetails");
                    Popup.showErrorPopup(caught, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                            "retrieve artist details.", Popup.ERROR_LVL.NORMAL, null);
                    PerformanceTimer.stop("invokeGetArtistInfo");
                }
            };

            showLoader();

            try {
                PerformanceTimer.start("getArtistDetails");
                musicServer.getArtistDetails(artistID, refresh, cdm.getCurrSimTypeName(), 
                        cdm.getCurrPopularity(), callback);
            } catch (Exception ex) {
                Popup.showErrorPopup(ex, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                    "retrieve artist details.", Popup.ERROR_LVL.NORMAL, null);
            }
        }
    }

    private void invokeGetTagInfo(String tagID, boolean refresh) {
        AsyncCallback callback = new AsyncCallback() {

            public void onSuccess(Object result) {
                TagDetails tagDetails = (TagDetails) result;
                if (tagDetails != null && tagDetails.isOK()) {
                    Widget tagPanel = createTagPanel(tagDetails);
                    //search.setText(tagDetails.getName(), searchTypes.SEARCH_FOR_TAG_BY_TAG);
                    //search.updateSuggestBox(Oracles.TAG);
                    setResults("tag:"+tagDetails.getId(), tagPanel);
                    hideLoader();
                } else {
                    if (tagDetails == null) {
                        showTopMessage("Sorry. The details for the tag don't seem to be in our database.");
                        clearResults();
                    } else {
                        showTopMessage("Whooops " + tagDetails.getStatus());
                        clearResults();
                    }
                }
            }

            public void onFailure(Throwable caught) {
                Popup.showErrorPopup(caught, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                    "retrieve tag details.", Popup.ERROR_LVL.NORMAL, null);
            }
        };

        showLoader();

        try {
            musicServer.getTagDetails(tagID.substring(tagID.indexOf(":")+1), refresh, cdm.getCurrSimTypeName(), callback);
        } catch (Exception ex) {
            Popup.showErrorPopup(ex, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                "retrieve tag details.", Popup.ERROR_LVL.NORMAL, null);
        }
    }

    private final void addCompactArtistToOracle(ArrayList<ScoredC<ArtistCompact>> aCList) {
        for (ScoredC<ArtistCompact> aC : aCList) {
            cdm.getArtistOracle().add(aC.getItem().getName(), aC.getItem().getPopularity());
        }
    }
    
    private final void addCompactArtistToOracle(ArtistCompact[] aCArray) {
        for (ArtistCompact aC : aCArray) {
            cdm.getArtistOracle().add(aC.getName(), aC.getPopularity());
        }
    }

    private Widget createArtistPanel(ArtistDetails artistDetails) {

        ArtistCompact aC = artistDetails.toArtistCompact();
        updateWindowTitleLocal(aC.getName());

        VerticalPanel main = new VerticalPanel();
        main.setHorizontalAlignment(HorizontalPanel.ALIGN_CENTER);
        main.add(getBioWidget(artistDetails));
        if (artistDetails.getVideos().length > 0) {
            main.add(new DualRoundedPanel("Videos", new VideoScrollWidget(artistDetails.getVideos())));
        }
        if (artistDetails.getPhotos().length > 0) {
            main.add(new DualRoundedPanel("Photos", new ImageScrollWidget(artistDetails.getPhotos())));
        }
        if (artistDetails.getAlbums().length > 0) {
            main.add(new DualRoundedPanel("Albums", new AlbumScrollWidget(artistDetails.getAlbums())));
        }
        main.setHorizontalAlignment(HorizontalPanel.ALIGN_LEFT);
        if (artistDetails.getEvents().length > 0) {
            main.add(getEventsWidget(artistDetails));
        }
        main.setStyleName("center");

        VerticalPanel left = new VerticalPanel();
        left.setSpacing(4);
        left.setWidth("300px");
        
        // Add similar artists
        ArtistCompact[] aCArray;
        aCArray = artistDetails.getSimilarArtistsAsArray();
        addCompactArtistToOracle(aCArray);

        if (leftSimList != null) {
            leftSimList.doRemoveListeners();
        }
        leftSimList = new ArtistCloudArtistListWidget(musicServer, cdm, artistDetails.getSimilarArtists(), aC);

        HorizontalPanel hP = new HorizontalPanel();
        hP.add(new Label("Similar artists"));
        hP.setHorizontalAlignment(HorizontalPanel.ALIGN_RIGHT);
        popSelect = new PopularitySelectAD(artistDetails);
        hP.add(popSelect);
        hP.setWidth("300px");
        hP.setStyleName("h2");

        left.add(
                new Updatable<ArtistDetails>(hP, leftSimList, cdm, artistDetails) {

                    public void update(ArrayList<ScoredC<ArtistCompact>> aCList) {
                        addCompactArtistToOracle(aCList);
                        leftSimList.doRemoveListeners();
                        leftSimList = new ArtistCloudArtistListWidget(musicServer, cdm, aCList, data.toArtistCompact());
                        setNewContent(leftSimList);
                    }
                });

        // Add recommended artists
        if (artistDetails.getRecommendedArtists().length > 0) {
            aCArray = artistDetails.getRecommendedArtists();
            addCompactArtistToOracle(aCArray);
            
            if (leftRecList != null) {
                leftRecList.doRemoveListeners();
            }
            leftRecList = new ArtistCloudArtistListWidget(musicServer, cdm, aCArray, aC);
            left.add(WebLib.createSection("Recommendations", leftRecList));
        }

        // Add related artists
        if (artistDetails.getCollaborations().length > 0) {
            aCArray = artistDetails.getCollaborations();
            addCompactArtistToOracle(aCArray);

            if (leftRelList != null) {
                leftRelList.doRemoveListeners();
            }
            leftRelList = new ArtistCloudArtistListWidget(musicServer, cdm, aCArray, aC);
            left.add(WebLib.createSection("Related", leftRelList));
        }
        left.add(getMoreInfoWidget(artistDetails));
        left.setStyleName("left");

        DockPanel artistPanel = new DockPanel();
        artistPanel.add(main, DockPanel.CENTER);
        artistPanel.add(left, DockPanel.WEST);
        artistPanel.setWidth("100%");
        artistPanel.setStyleName("resultpanel");
        return artistPanel;
    }

    private Widget createTagPanel(TagDetails tagDetails) {

        updateWindowTitleLocal(tagDetails.getName()+" tag");

        VerticalPanel main = new VerticalPanel();
        main.setHorizontalAlignment(HorizontalPanel.ALIGN_CENTER);
        main.add(getTagHeaderWidget(tagDetails));
        main.add(new DualRoundedPanel("Videos", new VideoScrollWidget(tagDetails.getVideos())));
        main.add(new DualRoundedPanel("Photos", new ImageScrollWidget(tagDetails.getPhotos())));
        main.setStyleName("center");

        VerticalPanel left = new VerticalPanel();
        left.setSpacing(4);
        left.setWidth("300px");
        
        // Add similar artists
        ArtistCompact[] aCArray;
        aCArray = tagDetails.getRepresentativeArtists();
        addCompactArtistToOracle(aCArray);

        if (leftSimList != null) {
            leftSimList.doRemoveListeners();
        }
        leftSimList = new TagCloudArtistListWidget(musicServer, cdm, aCArray);

        HorizontalPanel hP = new HorizontalPanel();
        hP.add(new Label("Representative artists"));
        hP.setHorizontalAlignment(HorizontalPanel.ALIGN_RIGHT);
        hP.setWidth("300px");
        hP.setStyleName("h2");

        left.add(hP);
        left.add(leftSimList);

        DockPanel artistPanel = new DockPanel();
        artistPanel.add(main, DockPanel.CENTER);
        artistPanel.add(left, DockPanel.WEST);
        artistPanel.setWidth("100%");
        artistPanel.setStyleName("resultpanel");
        return artistPanel;
    }

    private Widget getTagHeaderWidget(final TagDetails tagDetails) {
        
        HTML html = new HTML();
        html.setHTML(getBestTagImageAsHTML(tagDetails) + tagDetails.getDescription());
        html.setStyleName("bio");

        HorizontalPanel hP = new HorizontalPanel();
        hP.setSpacing(3);
        hP.add(WebLib.getListenWidget(tagDetails));
        SteeringWheelWidget sww = new SteeringWheelWidget(SteeringWheelWidget.wheelSize.BIG, new ClickHandler() {
            @Override
            public void onClick(ClickEvent event) {
                cdm.getSteerableTagCloudExternalController().clear(false);
                cdm.getSteerableTagCloudExternalController().addTag(tagDetails);
                History.newItem("steering:", true);
            }
        });
        sww.setTitle("Steerable recommendations starting with the "+tagDetails.getName()+" tag");
        hP.add(sww);
        
        return createMainSection(tagDetails.getName(), html,
                hP, tagDetails.getSimilarTags(), null, false);
    }
    
    private Widget getBioWidget(ArtistDetails artistDetails) {
        HTML html = new HTML();
        html.setHTML(artistDetails.getBestArtistImageAsHTML() + artistDetails.getBiographySummary());
        html.setStyleName("bio");

        artistStar = new StarRatingWidget(musicServer, cdm, artistDetails.getId(), StarRatingWidget.InitialRating.FETCH, StarRatingWidget.Size.MEDIUM);
        cdm.getLoginListenerManager().addListener(artistStar);

        HorizontalPanel hP = new HorizontalPanel();
        hP.setVerticalAlignment(VerticalPanel.ALIGN_MIDDLE);
        playButton = new PlayButton(cdm, artistDetails.toArtistCompact(),
            PlayButton.PLAY_ICON_SIZE.MEDIUM, musicServer);
        if (playButton!=null) {
            cdm.getMusicProviderSwitchListenerManager().addListener(playButton);
            playButton.addStyleName("pointer");
            hP.add(playButton);
        }
        
        ArtistCompact aC = artistDetails.toArtistCompact();
        SteeringWheelWidget steerButton = new SteeringWheelWidget(SteeringWheelWidget.wheelSize.BIG, 
                new DDEClickHandler<ClientDataManager, ArtistCompact>(cdm, aC) {

            @Override
            public void onClick(ClickEvent ce) {
                data.setSteerableReset(true);
                History.newItem("steering:" + sndData.getId());
            }
        });
        steerButton.setTitle("Steerable recommendations starting with "+aC.getName()+"'s tag cloud");
        hP.add(new ContextMenuSteeringWheelWidget(cdm, steerButton, aC));

        //return createMainSection(artistDetails.getName(), html,
        //        hP, artistDetails.getDistinctiveTags(), artistStar, true);
        return createMainSection(artistDetails.getName(), html,
                hP, artistDetails.getDistinctiveTags(), null, true);
    }

    private Widget getMoreInfoWidget(ArtistDetails artistDetails) {
        Map<String, String> urls = artistDetails.getUrls();

        if (urls != null && urls.size() > 0) {
            Grid grid = new Grid(urls.size(), 1);
            grid.setWidth("300px");
            int index = 0;
            for (String key : urls.keySet()) {
                String url =  urls.get(key);
                HTML html = new HTML(WebLib.createAnchor(key, url));
                grid.setWidget(index++, 0, html);
            }
            return new DualRoundedPanel("More info", grid);
        } else {
            return new Label("");
        }
    }
/*
    Widget getTastAuraMeterPanel(ArtistDetails aD) {

        double currArtistScore = 1; //cdm.computeTastauraMeterScore(aD);
        double realMaxScore;    // max between currArtist and user's fav artists' max score

        if (currArtistScore>cdm.getMaxScore()) {
            realMaxScore = currArtistScore;
        } else {
            realMaxScore = cdm.getMaxScore();
        }

        VerticalPanel vPanel = new VerticalPanel();

        for (String key : cdm.getFavArtist().keySet()) {
            vPanel.add(WebLib.getPopularityWidget(key, cdm.getFavArtist().get(key)/realMaxScore, false, null));
        }

        vPanel.add(WebLib.getPopularityWidget(aD.getName(),
                currArtistScore/realMaxScore, false, "itemInfoHighlight"));

        return WebLib.createSection("Tast-aura-meter", vPanel);
    }
    */

    private Widget getPopularityPanel(ArtistDetails artistDetails) {

        VerticalPanel vPanel = new VerticalPanel();
        vPanel.add(WebLib.getPopularityWidget("The Beatles",1,true,null));
        vPanel.add(WebLib.getPopularityWidget(artistDetails.getName(),
                artistDetails.getNormPopularity(),true,null));

        return WebLib.createSection("Popularity", vPanel);
    }

    private Widget getTastAuraMeterWidget(String name, double normPopularity, boolean log) {

        /**
        Widget popWidget = getPopularityWidget(name, normPopularity, log);

        Label why = new Label("why?");
        why.setStyleName("tinyInfo");
        why.addClickListener(new CommonTagsClickListener(highlightID, itemInfo[i].getId()));
        **/
        return null;

    }

    private DualRoundedPanel getEventsWidget(ArtistDetails artistDetails) {
        ArtistEvent[] events = artistDetails.getEvents();
        VerticalPanel widget = new VerticalPanel();
        if (events.length == 0) {
            widget.setWidth("300px");
            widget.add(new Label("No events found"));
        } else {
            String introMessage;
            if (artistDetails.isActive()) {
                introMessage = "Some upcoming events related to " + artistDetails.getName();
            } else {
                introMessage = "Although we haven't heard from " + artistDetails.getName() + " since " + artistDetails.getEndYear() + ", you might find these related events to be of interest.";
            }
            widget.add(new HTML(introMessage + "<br/>"));
            Grid grid = new Grid(events.length, 3);
            for (int i = 0; i < events.length; i++) {
                ArtistEvent event = events[i];
                grid.setWidget(i, 0, new Label(events[i].getDate()));
                grid.setWidget(i, 1, new HTML(WebLib.createAnchor(event.getName(), event.getEventURL())));
                String venue = event.getVenue();
                grid.setWidget(i, 2, new HTML(venue));
            }
            widget.add(grid);
        }
        return new DualRoundedPanel("Upcoming Events", widget);
    }

    private Widget createMainSection(String title, Widget widget, Widget adornment, 
            ItemInfo[] tagCloud, StarRatingWidget starWidget, boolean addTagInputWidget) {
        Panel panel = new VerticalPanel();
        DockPanel h = new DockPanel();
        h.add(new Label(title), DockPanel.WEST);
        if (adornment != null) {
            h.add(adornment, DockPanel.EAST);
            h.setCellHorizontalAlignment(adornment, HorizontalPanel.ALIGN_RIGHT);
        }
        if (starWidget != null) {
            h.add(starWidget, DockPanel.NORTH);
        }

        h.setWidth("100%");
        h.setStyleName("h1");
        panel.add(h);
        if (tagCloud != null) {
            if (tagInputWidget != null) {
                tagInputWidget.onDelete();
            }
            if (addTagInputWidget) {
                tagInputWidget = new TagInputWidget(musicServer, cdm, "artist", cdm.getCurrArtistID());
                cdm.getLoginListenerManager().addListener(tagInputWidget);
                //panel.add(tagInputWidget);
            }
            
            Panel p = TagDisplayLib.getTagsInPanel(tagCloud, TagDisplayLib.ORDER.SHUFFLE, cdm, TagColorType.TAG);
            // If there are not tags, this will be null
            if (p != null) {
                p.addStyleName("tagCloudMargin");
                panel.add(p);
            } else {
                panel.add(new HTML("<br /<br />"));
            }
        }
        panel.add(widget);
        return panel;
    }

    private String getBestTagImageAsHTML(TagDetails td) {
        String imgHtml = "";
        ArtistPhoto[] photos = td.getPhotos();
        if (photos.length > 0) {
            imgHtml = photos[0].getHtmlWrapper();
        }
        return imgHtml;
    }

    private String getEmbeddedVideo(ArtistVideo video, boolean autoplay) {
        String url = video.getUrl();
        String autostring = autoplay ? "&autoplay=1" : "";
        url = url.replaceAll("\\?v=", "/v/");
        //String title = "<span style=\"text-align:center\">" + video.getTitle() + "</span><br/>";
        String obj = "<center><object width=\"425\" height=\"350\"><param name=\"movie\" value=\"" + url + "\"></param><param name=\"wmode\" value=\"transparent\"></param>" + "<embed src=\"" + url + autostring + "\" type=\"application/x-shockwave-flash\"" + " wmode=\"transparent\" width=\"425\" height=\"350\"></embed></object></center>";
        //return title + obj;
        return obj;
    }

    private VerticalPanel getItemInfoList(final String title, final ItemInfo[] itemInfo, 
            String highlightID, boolean getArtistOnClick, boolean displayPopularity, 
            PopSortedMultiWordSuggestOracle oracle) {

        Grid artistGrid;
        if (displayPopularity) {
            artistGrid = new Grid(itemInfo.length + 1, 2);
            artistGrid.setCellSpacing(5);
            artistGrid.setWidget(0, 0, new HTML("<b>Name</b>"));
            artistGrid.setWidget(0, 1, new HTML("<b>Popularity</b>"));
        } else {
            artistGrid = new Grid(itemInfo.length, 1);
        }

        // Find the maximum values for score and popularity
        double maxPopularity = 0;
        for (ItemInfo iI : itemInfo) {
            if (iI.getPopularity() > maxPopularity) {
                maxPopularity = iI.getPopularity();
            }
        }

        for (int i = 0; i < itemInfo.length; i++) {

            if (oracle != null) {
                oracle.add(itemInfo[i].getItemName(), itemInfo[i].getPopularity());
            }

            Label label = new Label(itemInfo[i].getItemName());
            label.addClickHandler(new DEClickHandler<String>(itemInfo[i].getId()) {
                @Override
                public void onClick(ClickEvent event) {
                    // Add search attention if necessary
                    cdm.getSearchAttentionManager().processUserClick(data);
                }
            });
            label.addClickHandler(new ItemInfoClickHandler(itemInfo[i], getArtistOnClick));
            label.setTitle("Score: " + itemInfo[i].getScore() + " Popularity:" + itemInfo[i].getPopularity());
            if (highlightID != null && highlightID.equals(itemInfo[i].getId())) {
                label.setStyleName("itemInfoHighlight");
            } else {
                label.setStyleName("itemInfo");
            }
            
            if (displayPopularity) {
                artistGrid.setWidget(i + 1, 0, label);
                artistGrid.setWidget(i + 1, 1, WebLib.getPopularityHisto( itemInfo[i].getPopularity() / maxPopularity, false, 10, 100));
            } else {
                artistGrid.setWidget(i, 0, label);
            }
        }

        VerticalPanel w;
        if (!getArtistOnClick) {
            Grid titleWidget = new Grid(1, 2);
            titleWidget.setWidget(0, 0, new HTML("<h2>" + title + "</h2>"));
            Label l = new Label(" Cloud");
            l.setStyleName("tinyInfo");
            l.addClickHandler(new ClickHandler() {

                public void onClick(ClickEvent ce) {
                    TagDisplayLib.showTagCloud(title, itemInfo, TagDisplayLib.ORDER.SHUFFLE, cdm);
                }
            });
            titleWidget.setWidget(0, 1, l);
            w = WebLib.createSection(titleWidget, artistGrid);
        } else {
            w = WebLib.createSection(title, artistGrid);
        }
        w.setStyleName("infoList");
        if (displayPopularity) {
            w.setWidth("325px");
        } else {
            w.setWidth("200px");
        }
        return w;
    }

    private class TimerWithArtist extends Timer {

        private String artistID;
        private boolean refresh;

        public TimerWithArtist(String artistID, boolean refresh) {
            super();
            this.artistID=artistID;
            this.refresh=refresh;
        }

        @Override
        public void run() {
            History.newItem("artist:"+artistID);
        }
    }

    private class ItemInfoClickHandler implements ClickHandler {

        private ItemInfo info;
        private boolean getArtistOnClick;

        ItemInfoClickHandler(ItemInfo info, boolean getArtistOnClick) {
            this.info = info;
            this.getArtistOnClick = getArtistOnClick;
        }

        @Override
        public void onClick(ClickEvent ce) {
            if (getArtistOnClick) {
                History.newItem("artist:"+info.getId());
            } else {
                History.newItem("tag:"+info.getId());
            }
        }
    }

    private class ArtistCloudArtistListWidget extends ArtistListWidget {

        private ArtistCompact currArtist;

        public ArtistCloudArtistListWidget(MusicSearchInterfaceAsync musicServer,
            ClientDataManager cdm, ArrayList<ScoredC<ArtistCompact>> aC, ArtistCompact currArtist) {

            super(musicServer, cdm, aC, cdm.isLoggedIn(), true);
            this.currArtist = currArtist;
        }
        
        public ArtistCloudArtistListWidget(MusicSearchInterfaceAsync musicServer,
            ClientDataManager cdm, ArtistCompact[] aC, ArtistCompact currArtist) {

            super(musicServer, cdm, aC, cdm.isLoggedIn(), true);
            this.currArtist = currArtist;
        }

        @Override
        public void openWhyPopup(SwapableTxtButton why) {
            why.showLoad();
            TagDisplayLib.invokeGetCommonTags(currArtist.getId(), why.getId(),
                    musicServer, cdm, new CommonTagsAsyncCallback(why, "Common tags between "+currArtist.getName()+" and "+why.getName(), cdm) {});
        }

        @Override
        public void openDiffPopup(DiffButton diff) {
            if (diff.getId().equals(currArtist.getId())) {
                diff.displayIdenticalArtistMsg();
            } else {
                TagDisplayLib.showDifferenceCloud("Difference cloud between "+currArtist.getName()+" and "+diff.getName(),
                    currArtist.getDistinctiveTags(), diff.getDistinctiveTags(), cdm);
            }
        }
    }
    
    private class TagCloudArtistListWidget extends ArtistListWidget {

        private ArtistCompact[] aC;

        public TagCloudArtistListWidget(MusicSearchInterfaceAsync musicServer,
            ClientDataManager cdm, ArtistCompact[] aDArray) {
            
            super(musicServer, cdm, aDArray, cdm.isLoggedIn(), false);
            this.aC = aDArray;
        }

        @Override
        public void openWhyPopup(SwapableTxtButton why) {

            for (ArtistCompact t : aC) {
                if (t.getId().equals(why.getId())) {
                    TagDisplayLib.showTagCloud(t.getName()+"'s tag cloud",
                        t.getDistinctiveTags(), TagDisplayLib.ORDER.SHUFFLE, cdm);
                    break;
                }
            }
        }

        @Override
        public void openDiffPopup(DiffButton diff) {
            Popup.showErrorPopup("", Popup.ERROR_MSG_PREFIX.NONE,
                "This feature is not yet implemented.", Popup.ERROR_LVL.SILENT, null);
        }
        
    }

    private abstract class ScrollWidget extends Composite {

        protected ScrollItem[] items;

        private final int NBR_ITEM_ON_PREVIEW=12;
        private int NBR_ITEM_PER_LINE=3;

        protected int maxImgHeight = 0;
        protected int maxImgWidth = 0;

        protected Grid mainPanel = new Grid(2,1);
        protected Grid topPanel = new Grid(1,3);

        protected Panel currPreview;
        protected Panel nextPreview;

        protected int currPageNbr = 0;
        protected int totalPageNbr = -1;

        private Label headerTitle;

        abstract protected void triggerAction(int index);
        abstract protected String getSectionName();

        protected Widget init() {

            if (Window.getClientWidth()>1024) {
                NBR_ITEM_PER_LINE=4;
            }

            totalPageNbr = (int) Math.ceil(items.length / new Double(NBR_ITEM_ON_PREVIEW));

            topPanel.addStyleName("center");
            topPanel.setWidth("100%");
            topPanel.setCellPadding(4);

            Image prev = playImgBundle.scrollWidgetPrev().createImage();
            prev.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent ce) {
                    setPreviewPanel(getElementsOnPage(currPageNbr-1));
                }
            });

            Image next = playImgBundle.scrollWidgetNext().createImage();
            next.addClickHandler(new ClickHandler() {
                @Override
                public void onClick(ClickEvent ce) {
                    setPreviewPanel(getElementsOnPage(currPageNbr+1));
                }
            });

            if (items.length>NBR_ITEM_ON_PREVIEW) {
                topPanel.setWidget(0, 0, prev);
                topPanel.setWidget(0, 2, next);
            }
            headerTitle = new Label();
            topPanel.setWidget(0, 1, headerTitle);

            for (int j=0; j<3; j++) {
                topPanel.getCellFormatter().setAlignment(0, j,
                        HorizontalPanel.ALIGN_CENTER, VerticalPanel.ALIGN_MIDDLE);
            }

            if (items.length>0) {
                setPreviewPanel(getElementsOnPage(currPageNbr));
                mainPanel.setWidget(0, 0, topPanel);
            } else {
                mainPanel.setWidget(1, 0, new Label("No "+getSectionName()));
            }
            mainPanel.setWidth("100%");

            return mainPanel;
        }

        private void updateHeaderTitle() {
            if (totalPageNbr>1) {
                headerTitle.setText((currPageNbr+1)+"/"+totalPageNbr);
            }
        }

        /**
         * Returns elements on the given page
         * @param page number
         * @return n scrollitems
         */
        protected ArrayList<ScrollItem> getElementsOnPage(int newPageNbr) {
            // If we're going over or under, wrap us around
            if (newPageNbr<0) {
                newPageNbr = totalPageNbr - 1;
            } else if (newPageNbr>=totalPageNbr) {
                newPageNbr = 0;
            }

            ArrayList<ScrollItem> sI = new ArrayList<ScrollItem>();
            for (int i=0; i<NBR_ITEM_ON_PREVIEW; i++) {
                int idx = i + (newPageNbr * NBR_ITEM_ON_PREVIEW);
                if (idx>=items.length) {
                    break;
                }
                sI.add(new ScrollItem(items[idx].title,
                        items[idx].thumb, idx));
            }
            currPageNbr = newPageNbr;
            return sI;
        }

        private void setPreviewPanel(ArrayList<ScrollItem> sI) {
            nextPreview = new VerticalPanel();
            ArrayList<HorizontalPanel> topPreviewArray = new ArrayList<HorizontalPanel>();
            HorizontalPanel topPreview=null;

            int index=0;
            for (ScrollItem i : sI) {
                if (topPreview==null || ++index>=NBR_ITEM_PER_LINE) {
                    if (topPreview!=null) {
                        topPreviewArray.add(topPreview);
                        topPreview=null;
                    }

                    index = 0;
                    topPreview = new HorizontalPanel();
                    topPreview.setHorizontalAlignment(HorizontalPanel.ALIGN_CENTER);
                    topPreview.setVerticalAlignment(VerticalPanel.ALIGN_MIDDLE);
                    topPreview.setWidth("100%");
                    topPreview.setSpacing(8);
                }

                Grid g = new Grid(1, 1);
                g.setSize(maxImgWidth+"px", maxImgHeight+"px");
                g.getCellFormatter().getElement(0, 0).setAttribute("valign", "middle");
                g.getCellFormatter().getElement(0, 0).setAttribute("align", "center");
                g.setTitle(i.title);

                NEffectPanel theEffectPanel = new NEffectPanel();
                Fade f = new Fade();
                f.getProperties().setStartOpacity(0);
                f.getProperties().setEndOpacity(100);
                theEffectPanel.addEffect(f);

                Image img = new Image(i.thumb);
                //img.setTitle(i.title);
                theEffectPanel.add(img);
                img.setVisible(false);
                img.addLoadListener(new LoadListenerPanelContainer(theEffectPanel));
                img.addClickHandler(new IndexClickHandler(i.index));

                // Crop if necessary
                if (maxImgHeight>0 && maxImgWidth>0) {
                    img.setVisibleRect(0, 0, maxImgWidth, maxImgHeight);
                }

                g.setWidget(0, 0, theEffectPanel);
                topPreview.add(g);
            }

            if (topPreview!=null) {

                while (index<NBR_ITEM_PER_LINE) {
                    topPreview.add(new Label(""));
                    index++;
                }

                topPreviewArray.add(topPreview);
                topPreview=null;
            }

            nextPreview = new VerticalPanel();
            for (HorizontalPanel p : topPreviewArray) {
                nextPreview.add(p);
            }
            mainPanel.setWidget(1, 0, nextPreview);
            updateHeaderTitle();
        }

        protected class IndexClickHandler implements ClickHandler {
            
            protected int index;

            public IndexClickHandler(int index) {
                super();
                this.index=index;
            }

            @Override
            public void onClick(ClickEvent ce) {
                triggerAction(index);
            }
        }

        private class LoadListenerPanelContainer implements LoadListener {

            private NEffectPanel theEffectPanel;

            /**
             * @param w widget we want the effect applied to
             */
            public LoadListenerPanelContainer(NEffectPanel theEffectPanel) {
                super();
                this.theEffectPanel=theEffectPanel;
            }

            @Override
            public void onError(Widget arg0) {
            }

            @Override
            public void onLoad(Widget arg0) {
                theEffectPanel.playEffects();
            }
        }

        protected class ScrollItem {
            public String title;
            public String thumb;
            public int index;

            public ScrollItem(String title, String thumb, int index) {
                this.title=title;
                this.thumb=thumb;
                this.index=index;
            }
        }

    }

    private class ImageScrollWidget extends ScrollWidget {

        private ArtistPhoto[] aP;

        public ImageScrollWidget(ArtistPhoto[] aPArray) {
            this.aP = aPArray;
            ArrayList<ScrollItem> sIList = new ArrayList<ScrollItem>();
            int i = 0;
            for (ArtistPhoto a : aPArray) {
                if (a != null) {
                    sIList.add(new ScrollItem(a.getTitle(),
                        a.getSmallImageUrl(), i++));
                }
            }
            items = sIList.toArray(new ScrollItem[0]);

            maxImgHeight = 130;
            maxImgWidth = 130;

            initWidget(init());
        }

        @Override
        protected String getSectionName() {
            return "photos";
        }

        @Override
        protected void triggerAction(int index) {
            HTML html = new HTML(aP[index].getRichHtmlWrapper());
            //Popup.showPopup(html,"WebMusicExplaura :: Flickr Photo");
            //Popup.showRoundedPopup(html, "WebMusicExplaura :: Flickr Photo");
            Popup.showRoundedPopup(html, aP[index].getTitle(), 600);
        }

    }

    private class VideoScrollWidget extends ScrollWidget {

        private ArtistVideo[] aV;

        public VideoScrollWidget(ArtistVideo[] aVArray) {
            this.aV = aVArray;
            ArrayList<ScrollItem> sIList = new ArrayList<ScrollItem>();
            int i = 0;
            for (ArtistVideo a : aVArray) {
                if (a != null) {
                    sIList.add(new ScrollItem(a.getTitle(),
                        a.getThumbnail(), i++));
                }
            }
            items = sIList.toArray(new ScrollItem[0]);

            maxImgHeight = 97;
            maxImgWidth = 130;

            initWidget(init());
        }

        @Override
        protected String getSectionName() {
            return "videos";
        }

        @Override
        protected void triggerAction(int index) {
            HTML html = new HTML(getEmbeddedVideo(aV[index], true));
            //Popup.showPopup(html,"WebMusicExplaura :: YouTube Video");
            //Popup.showRoundedPopup(html, "WebMusicExplaura :: YouTube Video");
            Popup.showRoundedPopup(html, aV[index].getTitle(), 600);
        }

    }

    private class AlbumScrollWidget extends ScrollWidget {

        private AlbumDetails[] aD;

        public AlbumScrollWidget(AlbumDetails[] aDArray) {
            this.aD = aDArray;
            ArrayList<ScrollItem> sIList = new ArrayList<ScrollItem>();
            int i = 0;
            for (AlbumDetails a : aDArray) {
                if (a != null) {
                    sIList.add(new ScrollItem(a.getTitle(),
                        a.getAlbumArt(), i++));
                }
            }
            items = sIList.toArray(new ScrollItem[0]);
            
            maxImgHeight = 130;
            maxImgWidth = 130;
            
            initWidget(init());
        }

        @Override
        protected void triggerAction(int index) {
            Window.open(aD[index].getAmazonLink(), "Window1", "");
        }

        @Override
        protected String getSectionName() {
            return "albums";
        }

    }
    
    private class PopularitySelectAD extends PopularitySelect {

        private ArtistDetails aD;

        public PopularitySelectAD(ArtistDetails aD) {
            super(cdm.getCurrPopularity());
            this.aD = aD;
        }

        @Override
        public void onSelectionChange(String newPopularity) {
            cdm.setCurrPopularity(newPopularity);
            cdm.displayWaitIconUpdatableWidgets();
            invokeGetArtistInfo(aD.getId());
        }

        /**
         * Fetch new similar artists. Used when similarity type is updated
         * @param artistID
         * @param refresh
         */
        private void invokeGetArtistInfo(String artistID) {

            if (artistID.startsWith("artist:")) {
                artistID = artistID.replaceAll("artist:", "");
            }
            final String fArtistID = artistID;

            AsyncCallback<ArrayList<ScoredC<ArtistCompact>>> callback = new AsyncCallback<ArrayList<ScoredC<ArtistCompact>>>() {

                public void onSuccess(ArrayList<ScoredC<ArtistCompact>> aC) {
                    // do some UI stuff to show success
                    if (aC != null) {
                        cdm.updateUpdatableWidgets(aC);
                    } else {
                        Popup.showErrorPopup("Returned list was null.", Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                                "retrieve the new recommendations.", Popup.ERROR_LVL.NORMAL, new DECommand<String>(fArtistID) {
                            @Override
                            public void execute() {
                                invokeGetArtistInfo(data);
                            }
                        });
                    }
                }

                public void onFailure(Throwable caught) {
                    Popup.showErrorPopup(caught, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                            "retrieve the new recommendations.", Popup.ERROR_LVL.NORMAL, new DECommand<String>(fArtistID) {
                        @Override
                        public void execute() {
                            invokeGetArtistInfo(data);
                        }
                    });
                }
            };

            try {
                musicServer.getSimilarArtists(artistID, cdm.getCurrSimTypeName(), cdm.getCurrPopularity(), callback);
            } catch (Exception ex) {
                Popup.showErrorPopup(ex, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                        "retrieve the new recommendations.", Popup.ERROR_LVL.NORMAL, new DECommand<String>(fArtistID) {
                    @Override
                    public void execute() {
                        invokeGetArtistInfo(data);
                    }
                });
            }
        }
    };
}
