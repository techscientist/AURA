/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.music.wsitm.client.ui.swidget;

import com.sun.labs.aura.music.wsitm.client.ui.TagDisplayLib;
import com.sun.labs.aura.music.wsitm.client.ui.MenuItem;
import com.sun.labs.aura.music.wsitm.client.event.TaggingListener;
import com.sun.labs.aura.music.wsitm.client.event.HasListeners;
import com.sun.labs.aura.music.wsitm.client.event.RatingListener;
import com.sun.labs.aura.music.wsitm.client.event.LoginListener;
import com.sun.labs.aura.music.wsitm.client.event.CommonTagsAsyncCallback;
import com.sun.labs.aura.music.wsitm.client.*;
import com.sun.labs.aura.music.wsitm.client.ui.widget.ArtistListWidget;
import com.sun.labs.aura.music.wsitm.client.ui.widget.CompactArtistWidget;
import com.extjs.gxt.ui.client.util.Params;
import com.extjs.gxt.ui.client.widget.Info;
import com.google.gwt.user.client.Random;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.sun.labs.aura.music.wsitm.client.event.DataEmbededChangeListener;
import com.sun.labs.aura.music.wsitm.client.items.ArtistCompact;
import com.sun.labs.aura.music.wsitm.client.items.ArtistDetails;
import com.sun.labs.aura.music.wsitm.client.items.ArtistRecommendation;
import com.sun.labs.aura.music.wsitm.client.items.AttentionItem;
import com.sun.labs.aura.music.wsitm.client.items.ItemInfo;
import com.sun.labs.aura.music.wsitm.client.items.ListenerDetails;
import com.sun.labs.aura.music.wsitm.client.ui.SpannedLabel;
import com.sun.labs.aura.music.wsitm.client.ui.UpdatablePanel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author mailletf
 */
public class DashboardSwidget extends Swidget {

    private MainPanel mP;
    
    private Grid recPanel;
    private UpdatablePanel uP;

    public DashboardSwidget(ClientDataManager cdm) {
        super("Dashboard", cdm);
        mP = new MainPanel();
        cdm.getRatingListenerManager().addListener(mP);
        cdm.getTaggingListenerManager().addListener(mP);
        cdm.getLoginListenerManager().addListener(mP);
        initWidget(mP);
    }

    public List<String> getTokenHeaders() {
        
        List<String> l = new ArrayList<String>();
        l.add("dashboard:");
        return l;
    }

    protected void initMenuItem() {
        menuItem = new MenuItem("Dashboard", MenuItem.getDefaultTokenClickListener("dashboard:"), true, 3);
    }

    public void doRemoveListeners() {
        mP.onDelete();
    }

    private class MainPanel extends Composite implements LoginListener, 
            RatingListener, TaggingListener, HasListeners {

        private Grid g;
        private static final int IMG_SIZE = 150;

        private Grid featArtist;
        private Grid recentRating;
        private List<HasListeners> recentRatingListeners;
        private Grid recentTagged;
        private List<HasListeners> recentTaggingListeners;

        public MainPanel() {

            recentRatingListeners = new LinkedList<HasListeners>();
            recentTaggingListeners = new LinkedList<HasListeners>();

            g = new Grid(1,1);
            initWidget(g);
            update();
        }

        public void onLogin(ListenerDetails lD) {
            update();
        }

        public void onLogout() {
            update();
        }

        public void update() {
            if (cdm.isLoggedIn()) {
                g.setWidget(0, 0, getDashboard());
            } else {
                g.setWidget(0, 0, getMustBeLoggedInWidget());
            }
        }

        private Widget getDashboard() {

            DockPanel dP = new DockPanel();
            
            recPanel = new Grid(1,1);
            recPanel.setWidget(0, 0, new Label("Loding..."));
            if (cdm.getRecTypes() == null || cdm.getRecTypes().isEmpty()) {
                invokeFetchRecType();
            } else {
                createRecPanel();
            }           
            dP.add(recPanel, DockPanel.WEST);

            Label titleLbl = new Label("Dashboard");
            titleLbl.setStyleName("h1");
            //dP.add(titleLbl, DockPanel.NORTH);

            //
            // Featured artist
            featArtist = new Grid(2,1);
            featArtist.setWidget(0, 0, new HTML("<h2>Featured Artist</h2>"));
            featArtist.setWidget(1, 0, new Image("ajax-bar.gif"));
            invokeFetchFeaturedArtist();

            recentRating = new Grid(2,1);
            recentRating.setWidget(0, 0, new HTML("<h2>Recently rated artists</h2>"));
            recentRating.setWidget(1, 0, new Image("ajax-bar.gif"));
            invokeFetchRecentRatedArtist();

            recentTagged = new Grid(2,1);
            recentTagged.setWidget(0, 0, new HTML("<h2>Recently tagged artists</h2>"));
            recentTagged.setWidget(1, 0, new Image("ajax-bar.gif"));
            invokeFetchRecentTagArtist();

            //dP.add(featArtist, DockPanel.NORTH);
            //dP.add(recentRating, DockPanel.NORTH);
            //dP.add(recentTagged, DockPanel.NORTH);

            ItemInfo[] trimTags = null;
            if (cdm.getListenerDetails().userTagCloud != null) {
                int max = cdm.getListenerDetails().userTagCloud.length;
                if (max > 20) {
                    max = 20;
                }
                List<ItemInfo> liI = ItemInfo.arrayToList(cdm.getListenerDetails().userTagCloud);
                Collections.sort(liI,ItemInfo.getScoreSorter());
                trimTags = new ItemInfo[max];
                for (int i=0; i<max; i++) {
                    trimTags[i] = liI.get(i);//cdm.getListenerDetails().userTagCloud[i];
                }
            }

            VerticalPanel centerPanel = new VerticalPanel();
            centerPanel.add(titleLbl);
            if (trimTags != null) {
                centerPanel.add(TagDisplayLib.getTagsInPanel(trimTags));
            }
            centerPanel.add(featArtist);
            centerPanel.add(recentRating);
            centerPanel.add(recentTagged);
            dP.add(centerPanel, DockPanel.NORTH);
            return dP;
        }

        /**
         * Stores the n first distinctive tags for an artist in a comma seperated string
         * @param aD artist's details
         * @param n number of tags
         * @return comma seperated string
         */
        private String getNDistinctiveTags(ArtistCompact aD, int n) {
            String tags = "";
            for (int i = 0; i < aD.getDistinctiveTags().length; i++) {
                tags += aD.getDistinctiveTags()[i].getItemName() + ", ";
                if (i == n) {
                    break;
                }
            }
            return tags.substring(0, tags.length()-2);
        }

        private void setFeaturedArtist(ArtistDetails aD) {
            if (aD == null) {
                featArtist.setWidget(1, 0, new Label("Unable to load artist."));
            } else {

                Grid featArtTitle = new Grid(1,3);
                featArtTitle.setStyleName("h2");
                featArtTitle.setWidth("100%");
                featArtTitle.setWidget(0, 0, new Label("Featured artist : "+aD.getName()));
                //featArtTitle.setWidget(0, 1, new StarRatingWidget(0,StarRatingWidget.Size.MEDIUM));
                featArtTitle.setWidget(0, 2, WebLib.getSpotifyListenWidget(aD, 30, null));

                featArtist.setWidget(0, 0, featArtTitle);

                HorizontalPanel featHp = new HorizontalPanel();
                featHp.setSpacing(5);
                featHp.add(aD.getBestArtistImage(false));

                VerticalPanel featVp = new VerticalPanel();
                featVp.setSpacing(4);
                featVp.add(new HTML(aD.getBiographySummary().substring(0, 300) + " [...]"));
                featVp.add(new HTML("<b>Tags</b> : "+getNDistinctiveTags(aD, 10)));

                featHp.add(featVp);


                featArtist.setWidget(1, 0,  featHp);

            }
        }

        private void invokeFetchRecentTagArtist() {

            AsyncCallback<List<AttentionItem>> callback = new AsyncCallback<List<AttentionItem>>() {

                public void onFailure(Throwable arg0) {
                    Window.alert(arg0.toString());
                }

                public void onSuccess(List<AttentionItem> arg0) {

                    int numLines = (int)Math.ceil(arg0.size() / 2.0);
                    Grid artists = new Grid(numLines, 2);

                    int lineIndex = 0;
                    int colIndex = 0;


                    for (AttentionItem aI : arg0) {

                        CompactArtistWidget caw = new CompactArtistWidget((ArtistCompact)aI.getItem(), cdm,
                                musicServer, null, aI.getRating(), aI.getTags());
                        recentTaggingListeners.add(caw);
                        artists.setWidget(lineIndex, (colIndex++)%2, caw);

                        if (colIndex%2 == 0) {
                            lineIndex++;
                        }
                    }
                    recentTagged.setWidget(1, 0, artists);
                }
            };

            try {
                musicServer.getLastTaggedArtists(6, callback);
            } catch (WebException ex) {
                Window.alert(ex.getMessage());
            }
        }

        /**
         * Create the left recommendation pannel
         */
        private void createRecPanel() {
            HorizontalPanel hP = new HorizontalPanel();
            SpannedLabel title = new SpannedLabel("Recommendations");
            title.setStyleName("h2");
            hP.add(title);

            ListBox listbox = new ListBox();
            String[] keyArray = cdm.getRecTypes().keySet().toArray(new String[0]);
            for (int i = keyArray.length - 1; i >= 0; i--) {
                listbox.addItem(keyArray[i], keyArray[i]);
            }
            listbox.setSelectedIndex(0);
            cdm.setCurrRecTypeName(listbox.getItemText(0));
            listbox.addChangeListener(new DataEmbededChangeListener<ListBox>(listbox) {

                public void onChange(Widget arg0) {

                    String newSelectName = data.getItemText(data.getSelectedIndex());

                    // If the selection has changed
                    if (!cdm.getCurrRecTypeName().equals(newSelectName)) {
                        cdm.setCurrRecTypeName(newSelectName);
                        invokeFetchRecommendations();
                    }
                }
            });
            hP.add(listbox);
            uP = new UpdatablePanel(hP, new Label("loading"), cdm);
            recPanel.setWidget(0, 0, uP);
            invokeFetchRecommendations();
        }

        private void invokeFetchRecType() {

            AsyncCallback<Map<String, String>> callback = new AsyncCallback<Map<String, String>>() {

                public void onFailure(Throwable arg0) {
                    Window.alert(arg0.toString());
                }

                public void onSuccess(Map<String, String> recTypes) {
                    if (recTypes != null) {
                        cdm.setRecTypes(recTypes);
                        createRecPanel();
                    } else {
                        Window.alert("Recommendation types is null");
                    }
                }
            };

            musicServer.getArtistRecommendationTypes(callback);
        }
        
        private void invokeFetchRecommendations() {

            AsyncCallback<List<ArtistRecommendation>> callback = new AsyncCallback<List<ArtistRecommendation>>() {

                public void onFailure(Throwable arg0) {
                    Window.alert(arg0.toString());
                }

                public void onSuccess(List<ArtistRecommendation> rec) {
                    uP.setNewContent(new UserCloudArtistListWidget(musicServer, cdm, ListArToAc(rec),rec));
                    uP.setWaitIconVicible(false);
                }
            };

            uP.setWaitIconVicible(true);
            
            try {
                musicServer.getRecommendations(cdm.getCurrRecTypeName(), 20, callback);
            } catch (WebException ex) {
                Window.alert(ex.getMessage());
            }
        }
        
        private void invokeFetchRecentRatedArtist() {

            AsyncCallback<List<AttentionItem>> callback = new AsyncCallback<List<AttentionItem>>() {

                public void onFailure(Throwable arg0) {
                    Window.alert(arg0.toString());
                }

                public void onSuccess(List<AttentionItem> arg0) {

                    int numLines = (int)Math.ceil(arg0.size() / 2.0);
                    Grid artists = new Grid(numLines, 2);
                    
                    int lineIndex = 0;
                    int colIndex = 0;
                    
                    
                    for (AttentionItem aI : arg0) {

                        CompactArtistWidget caw = new CompactArtistWidget((ArtistCompact)aI.getItem(), cdm,
                                musicServer, null, aI.getRating(), null);
                        recentRatingListeners.add(caw);
                        artists.setWidget(lineIndex, (colIndex++)%2, caw);

                        if (colIndex%2 == 0) {
                            lineIndex++;
                        }
                    }
                    recentRating.setWidget(1, 0, artists);
                }
            };

            try {
                musicServer.getLastRatedArtists(6, callback);
            } catch (WebException ex) {
                Window.alert(ex.getMessage());
            }
        }

        private void invokeFetchFeaturedArtist() {

            AsyncCallback callback = new AsyncCallback() {

                public void onSuccess(Object result) {
                    setFeaturedArtist((ArtistDetails) result);
                }

                public void onFailure(Throwable caught) {
                    Window.alert(caught.getMessage());
                }
            };

            try {

                ArtistCompact[] aC = cdm.getListenerDetails().recommendations;
                if (aC.length > 0) {
                    int itemIndex = Random.nextInt(aC.length);
                    musicServer.getArtistDetails(aC[itemIndex].getId(), false, cdm.getCurrSimTypeName(), callback);
                }
            } catch (WebException ex) {
                Window.alert(ex.getMessage());
            }
        }

        public void onDelete() {
            cdm.getLoginListenerManager().removeListener(this);
        }
        
        public void doRemoveListeners() {
            onDelete();
            clearListeners(recentRatingListeners);
            clearListeners(recentTaggingListeners);
        }

        public void onRate(String itemId, int rating) {
            clearListeners(recentRatingListeners);
            recentRating.setWidget(1, 0, new Image("ajax-bar.gif"));
            invokeFetchRecentRatedArtist();
        }

        public void onTag(String itemId, Set<String> tags) {
            clearListeners(recentTaggingListeners);
            recentTagged.setWidget(1, 0, new Image("ajax-bar.gif"));
            invokeFetchRecentTagArtist();
        }

        private void clearListeners(List<HasListeners> hLL) {
            for (HasListeners hL : hLL) {
                hL.doRemoveListeners();
            }
            hLL.clear();
        }
    }

    /**
     * Extract the ArtistCompacts from a list of artist recommenation
     * @param aR
     * @return
     */
    public ArtistCompact[] ListArToAc(List<ArtistRecommendation> aR) {
        ArtistCompact[] aC = new ArtistCompact[aR.size()];
        for (int i = 0; i < aR.size(); i++) {
            aC[i] = aR.get(i).getArtist();
        }
        return aC;
    }
    
    private class UserCloudArtistListWidget extends ArtistListWidget {

        private Map<String, ArtistRecommendation> mapAR;

        public UserCloudArtistListWidget(MusicSearchInterfaceAsync musicServer,
                ClientDataManager cdm, ArtistCompact[] aDArray, List<ArtistRecommendation> aR) {

            super(musicServer, cdm, aDArray, false);
            mapAR = new HashMap<String, ArtistRecommendation>();
            for (ArtistRecommendation a : aR) {
                mapAR.put(a.getArtist().getId(), a);
            }
        }

        public void openWhyPopup(WhyButton why) {
            TagDisplayLib.showTagCloud("Comon tags", mapAR.get(why.getId()).getExplanation());
        }
    }
}