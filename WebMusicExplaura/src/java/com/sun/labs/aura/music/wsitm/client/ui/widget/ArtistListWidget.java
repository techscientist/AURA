/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.music.wsitm.client.ui.widget;

import com.sun.labs.aura.music.wsitm.client.ui.SpannedLabel;
import com.sun.labs.aura.music.wsitm.client.event.DataEmbededClickListener;
import com.sun.labs.aura.music.wsitm.client.event.HasListeners;
import com.sun.labs.aura.music.wsitm.client.*;
import com.extjs.gxt.ui.client.Style.Direction;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.sun.labs.aura.music.wsitm.client.items.ArtistCompact;
import com.sun.labs.aura.music.wsitm.client.items.ItemInfo;
import com.sun.labs.aura.music.wsitm.client.ui.PerformanceTimer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author mailletf
 */
public abstract class ArtistListWidget extends Composite implements HasListeners {

    private Grid g;
    private MusicSearchInterfaceAsync musicServer;
    private ClientDataManager cdm;

    private ArtistCompact[] aDArray;
    private Double[] similarity;
    private Map<String,Integer> ratingMap;

    private List<CompactArtistWidget> artistWidgetList;
    
    private boolean displayDiff;

    public ArtistListWidget(MusicSearchInterfaceAsync musicServer,
            ClientDataManager cdm, ArtistCompact[] aDArray,
            boolean fetchRatings) {

        this(musicServer, cdm, aDArray, fetchRatings, true);
    }

    public ArtistListWidget(MusicSearchInterfaceAsync musicServer,
            ClientDataManager cdm, HashMap<ArtistCompact, Double> aC,
            boolean fetchRatings, boolean displayDiff) {
    
        processArtistSimValues(aC);
        doInit(musicServer, cdm, fetchRatings, true);
    }
    
    public ArtistListWidget(MusicSearchInterfaceAsync musicServer,
            ClientDataManager cdm, ArtistCompact[] aDArray, 
            boolean fetchRatings, boolean displayDiff) {
        
        this.aDArray = aDArray;
        doInit(musicServer, cdm, fetchRatings, displayDiff);
    }
    
    private void doInit(MusicSearchInterfaceAsync musicServer,
            ClientDataManager cdm, boolean fetchRatings, boolean displayDiff) {
        
        this.musicServer = musicServer;
        this.cdm = cdm;
        this.displayDiff = displayDiff;
        
        artistWidgetList = new LinkedList<CompactArtistWidget>();

        g = new Grid(1,1);
        initWidget(g);
        setWidth("300px");

        if (fetchRatings) {
            invokeFetchRatings();
        } else {
            ratingMap = new HashMap<String, Integer>();
            g.setWidget(0, 0, getUpdatedPanel());
        }
    }
    
    private void processArtistSimValues(HashMap<ArtistCompact, Double> aCMap) {
        similarity = new Double[aCMap.size()];
        aDArray = new ArtistCompact[aCMap.size()];
        int i = 0;
        String sims = "";
        for (ArtistCompact aC : aCMap.keySet()) {
            aDArray[i] = aC;
            similarity[i] = aCMap.get(aC);
            sims += simToColor(aCMap.get(aC))+";";
            i++;
        }
        
        
        Window.alert("simList:"+sims);
    }

    public abstract void openWhyPopup(SwapableTxtButton why);
    public abstract void openDiffPopup(DiffButton diff);

    /**
     * Called on tag click in the CompactArtistWidget. Overwrite to change default
     * behavior
     * @param tag
     */
    public void onTagClick(ItemInfo tag) {
        History.newItem("tag:"+tag.getId());
    }

    public void updateWidget(ArtistCompact[] aDArray) {
        if (this.aDArray!=aDArray) {
            this.aDArray = aDArray;
        }
        invokeFetchRatings();
    }

    public void doRemoveListeners() {
        for (CompactArtistWidget caw : artistWidgetList) {
            caw.doRemoveListeners();
        }
        artistWidgetList.clear();
    }

    private Panel getUpdatedPanel() {

        doRemoveListeners();

        VerticalPanel vP = new VerticalPanel();

        PerformanceTimer.start("  alw - GUP");
        if (aDArray != null && aDArray.length > 0) {
            int aCIndex = 0;
            for (ArtistCompact aC : aDArray) {

                // Add artist to oracle
                cdm.getArtistOracle().add(aC.getName());

                Image img = new Image("not-interested-vert.jpg");
                img.getElement().getStyle().setProperty("vertical-align", "top");
                img.getElement().getStyle().setProperty("display", "none");

                int rating;
                if (ratingMap.containsKey(aC.getId())) {
                    rating = ratingMap.get(aC.getId());
                } else {
                    rating = 0;
                }

                PerformanceTimer.start("  alw - single artist widget");
                
                DiffButton dB = null;
                if (this.displayDiff) {
                    dB = new DiffButton(aC);
                }

                // MUST DETERMINE BACKGROUND COLOR AND PASS IT
                String backColor = null;
                if (similarity != null && similarity.length >= aCIndex) {
                    backColor = simToColor(similarity[aCIndex++]);
                }
                
                CompactArtistWidget caw = new OverWroteOnClickCompactArtistWidget(aC, cdm,
                        musicServer, new WhyButton(aC.getId(), aC.getName()),
                        dB, rating, null, this, backColor);
                PerformanceTimer.stop("  alw - single artist widget");

                artistWidgetList.add(caw);

                DeletableWidget dW = new DeletableWidget<CompactArtistWidget>(caw, new HorizontalPanel()) {

                    public void onDelete() {
                        invokeAddNotInterested(getWidget().getArtistId());
                        this.getWidget().doRemoveListeners();
                        this.slideOut(Direction.UP,
                                new DualDataEmbededCommand<VerticalPanel, DeletableWidget>(((VerticalPanel) g.getWidget(0, 0)), this) {

                            public void execute() {
                                data.remove(sndData);
                            }
                        });
                    }
                };
                if (cdm.isLoggedIn()) {
                    dW.addRemoveButton();
                }
                vP.add(dW);
            }
        } else {
            vP.add(new Label("No artists found."));
        }

        PerformanceTimer.stop("  alw - GUP");
        return vP;
    }

    public static String simToColor(double sim) {
       
        String col = "";
        String tcol = "";
        
        Integer[] highColor = new Integer[3];
        Integer[] lowColor = new Integer[3];
        highColor[0] = 111;
        highColor[1] = 221;
        highColor[2] = 129;
        lowColor[0] = 240;
        lowColor[1] = 248;
        lowColor[2] = 198;
        
        for (int i=0; i<3; i++) {
            tcol = Integer.toHexString((int)(  (highColor[i]-lowColor[i])*sim + lowColor[i]  ));
            if (tcol.length() == 1) {
                tcol = "0" + tcol;
            } else if (tcol.length() == 0) {
                tcol = "00";
            }
            col += tcol;
        }
        
        return "#"+col;
    }

    private void invokeAddNotInterested(String artistId) {

        AsyncCallback callback = new AsyncCallback() {

            public void onSuccess(Object result) {}
            public void onFailure(Throwable caught) {
                Window.alert("Error adding not interested attention."+caught.toString());
            }
        };

        if (cdm.isLoggedIn()) {
            try {
                musicServer.addNotInterestedAttention(artistId, callback);
            } catch (WebException ex) {
                Window.alert(ex.getMessage());
            }
        }
    }

    private void invokeFetchRatings() {

        AsyncCallback callback = new AsyncCallback() {

            public void onSuccess(Object result) {
                Map<String,Integer> map = (Map<String,Integer>)result;
                if (map!=null) {
                   ratingMap = map;
                } else {
                   ratingMap = new HashMap<String, Integer>();
                }
                g.setWidget(0, 0, getUpdatedPanel());
            }

            public void onFailure(Throwable caught) {
                Window.alert(caught.toString());
                Window.alert("Error fetching ratings.");
            }
        };

        if (cdm.getListenerDetails().isLoggedIn()) {

            g.setWidget(0, 0, new Image("ajax-loader.gif"));

            Set<String> artistIDs = new HashSet<String>();
            for (ArtistCompact aC : aDArray) {
                artistIDs.add(aC.getId());
            }

            try {
                musicServer.fetchUserSongRating(artistIDs, callback);
            } catch (WebException ex) {
                Window.alert(ex.getMessage());
            }
        } else {
            ratingMap = new HashMap<String, Integer>();
            g.setWidget(0, 0, getUpdatedPanel());
        }
    }

    public class WhyButton extends SwapableTxtButton {

        public WhyButton(String id, String artistName) {
            super("why?", id, artistName);
        }
        
        @Override
        protected void addClickListener() {
            
            button.addClickListener(new DataEmbededClickListener<SwapableTxtButton>(this) {

                public void onClick(Widget arg0) {
                    openWhyPopup(data);
                }
            });
        }
    }

    public class DiffButton extends SwapableTxtButton {

        private ArtistCompact aC;

        public DiffButton(ArtistCompact aC) {
            super("diff", aC.getId(), aC.getName());
            this.aC = aC;
        }
        
        @Override
        protected void addClickListener() {
            
            button.addClickListener(new DataEmbededClickListener<DiffButton>(this) {

                public void onClick(Widget arg0) {
                    openDiffPopup(data);
                }
            });
        }

        public ItemInfo[] getDistinctiveTags() {
            return aC.getDistinctiveTags();
        }
        
        public void displayIdenticalArtistMsg() {
            Window.alert("Cannot display difference tag cloud between the same artist.");
        }

    }

    public abstract class SwapableTxtButton extends SwapableWidget<SpannedLabel, Image> {

        protected SpannedLabel button;

        private String artistName;
        private String id;

        public SwapableTxtButton(String linkName, String id, String artistName) {

            super(new SpannedLabel(linkName), new Image("ajax-loader-small.gif"));
            
            this.id = id;
            this.artistName = artistName;

            setWidth("30px");

            button = getWidget1();
            button.getElement().getStyle().setPropertyPx("font-size", 11);
            button.addStyleName("pointer");

            addClickListener();
        }
        
        protected abstract void addClickListener();

        public String getId() {
            return id;
        }

        public String getName() {
            return artistName;
        }

        public void showButton() {
            showWidget(LoadableWidget.W1);
        }

        public void showLoad() {
            showWidget(LoadableWidget.W2);
        }
    }

    public class OverWroteOnClickCompactArtistWidget extends CompactArtistWidget {

        private ArtistListWidget aLW;

        public OverWroteOnClickCompactArtistWidget(ArtistCompact aD, ClientDataManager cdm,
                MusicSearchInterfaceAsync musicServer, SwapableTxtButton whyB,
                SwapableTxtButton diffB, int currentRating, Set<String> userTags,
                ArtistListWidget aLW, String backColor) {
            super(aD, cdm, musicServer, whyB, diffB, currentRating, userTags, backColor);
            this.aLW = aLW;
        }

        @Override
        public void onTagClick(ItemInfo tag) {
            aLW.onTagClick(tag);
        }
    }
}