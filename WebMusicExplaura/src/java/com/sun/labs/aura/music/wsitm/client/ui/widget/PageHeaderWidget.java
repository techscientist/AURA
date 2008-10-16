/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.music.wsitm.client.ui.widget;

import com.sun.labs.aura.music.wsitm.client.ui.MenuItem;
import com.sun.labs.aura.music.wsitm.client.event.LoginListener;
import com.sun.labs.aura.music.wsitm.client.ui.swidget.Swidget;
import com.sun.labs.aura.music.wsitm.client.*;
import com.sun.labs.aura.music.wsitm.client.items.ListenerDetails;
import com.extjs.gxt.ui.client.util.Params;
import com.extjs.gxt.ui.client.widget.Info;
import com.extjs.gxt.ui.client.widget.toolbar.TextToolItem;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.DeferredCommand;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Random;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.KeyboardListener;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;
import com.sun.labs.aura.music.wsitm.client.items.ArtistCompact;
import com.sun.labs.aura.music.wsitm.client.event.HasListeners;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 *
 * @author mailletf
 */
public class PageHeaderWidget extends Swidget implements HasListeners {

    private Grid mainPanel;
    private TextBox txtbox;

    private ArrayList<MenuItem> menuItems;
    private MainMenu mm;
    private PlayButton playButton;

    private Widget instantRecPlayWidget;

    TextToolItem recTypeToolItem;

    private ListBox listbox;
    
    public PageHeaderWidget(ClientDataManager cdm) {
        super("pageHeader",cdm);
        this.cdm = cdm;
        menuItems = new ArrayList<MenuItem>();
        initWidget(getMainWidget());
    }
    
    public Widget getMainWidget() {
        
        mainPanel = new Grid(1,3);
        mainPanel.getColumnFormatter().setWidth(0, "33%");
        mainPanel.getColumnFormatter().setWidth(1, "33%");
        mainPanel.getCellFormatter().getElement(0, 1).setAttribute("align", "center");
        mainPanel.getColumnFormatter().setWidth(2, "33%");
        mainPanel.setStyleName("pageHeader");
        mainPanel.setWidth("100%");

        //
        // Set the recommendation type toolbar
        HorizontalPanel hP = new HorizontalPanel();
        Label lbl = new Label("Similarity type : ");
        lbl.setStyleName("headerMenuMed headerMenuMedC");
        hP.add(lbl);

        listbox = new ListBox(false);
        listbox.addItem("Loading...");
        hP.add(listbox);
        
        mainPanel.setWidget(0, 2, hP);
        mainPanel.getCellFormatter().getElement(0, 2).setAttribute("align", "right");

        invokeGetSimTypes();

        //
        // Set the section menu
        mm = new MainMenu();
        cdm.getLoginListenerManager().addListener(mm);
        mainPanel.setWidget(0, 1, mm);

        populateMainPanel();
     
        return mainPanel;
        
    }

    public void setMenuItems(ArrayList<MenuItem> mI) {
        this.menuItems = mI;
        mm.update();
    }

    public void updateSelectedMenuItem() {

    }

    private void populateMainPanel() {

        mainPanel.setWidget(0,0, new Label("Please wait while we fetch your session information..."));
        invokeGetUserSessionInfo();

    }

    private void invokeGetSimTypes() {
        AsyncCallback<HashMap<String, String>> callback =
                new AsyncCallback<HashMap<String, String>>() {

            public void onFailure(Throwable arg0) {
                Window.alert("Error fetching similarity types.");
            }

            public void onSuccess(HashMap<String, String> arg0) {
                cdm.setSimTypes(arg0);
                
                listbox.clear();
                String[] keyArray = cdm.getSimTypes().keySet().toArray(new String[0]);
                for (int i=keyArray.length-1; i>=0; i--) {
                    listbox.addItem(keyArray[i], keyArray[i]);
                }
                listbox.setSelectedIndex(0);
                cdm.setCurrSimTypeName(listbox.getItemText(0));
                listbox.addChangeListener(new ChangeListener() {

                    public void onChange(Widget arg0) {

                        String newSelectName = listbox.getItemText(listbox.getSelectedIndex());

                        // If the selection has changed
                        if (!cdm.getCurrSimTypeName().equals(newSelectName)) {
                            cdm.setCurrSimTypeName(newSelectName);

                            if (!cdm.getCurrArtistID().equals("")) {
                                cdm.displayWaitIconUpdatableWidgets();
                                invokeGetArtistInfo(cdm.getCurrArtistID());
                            }
                        }
                    }
                });
            }
        };

        try {
            musicServer.getSimTypes(callback);
        } catch (Exception ex) {
            Window.alert(ex.getMessage());
        }
    }

    private void fetchUserInfo() {
        Image.prefetch("ajax-ball.gif");
        mainPanel.clearCell(0, 0);
        HorizontalPanel h = new HorizontalPanel();
        h.setWidth("300px");
        h.add(new Image("ajax-ball.gif"));
        Label lbl = new Label("Connecting...");
        lbl.setStyleName("headerMenuMed headerMenuMedC");
        h.add(lbl);
        mainPanel.setWidget(0, 0, h);

        //
        // Login with local db if user is not using an openid
        if (txtbox.getText().startsWith("test-") || txtbox.getText().endsWith(".com") ||
                txtbox.getText().endsWith(".net") || txtbox.getText().endsWith(".org")) {
            // Run in deffered command to let the progress image load
            DeferredCommand.addCommand(new Command(){ public void execute() {
                Window.Location.assign("./Login?app-openid-auth=true&app-openid-name=" + txtbox.getText());
            }});
        } else {
            invokeGetUserSessionInfo(txtbox.getText());
        }
    }

    private void invokeTerminateSession() {
        AsyncCallback callback = new AsyncCallback() {

            public void onSuccess(Object result) {
                // do some UI stuff to show success
                Info.display("Information", "You are now logged out. Have a nice and productive day.", new Params());
                populateLoginBox();
            }

            public void onFailure(Throwable caught) {
                //failureAction(caught);
                Window.alert(caught.toString());
            }
        };

        try {
            cdm.resetUser();
            musicServer.terminateSession(callback);
        } catch (Exception ex) {
            Window.alert(ex.getMessage());
        }
    }

    /**
     * Called after a successful login by the invoke methods that just received
     * the new ListenerDetails containing the login information. Updates page header UI
     * @param l
     */
    private void updatePanelAfterLogin(ListenerDetails l) {

        if (l!=null && l.isLoggedIn()) {

            cdm.setListenerDetails(l);

            String name;
            if (l.getNickName() != null) {
                name = l.getNickName();
            } else if (l.getRealName() != null) {
                name = l.getRealName();
            } else {
                name = l.getOpenId();
            }

            HorizontalPanel hP = new HorizontalPanel();
            hP.setSpacing(4);
            Label loggedLbl = new Label(name);
            loggedLbl.addClickListener(new ClickListener() {

                public void onClick(Widget arg0) {
                    History.newItem("userpref:");
                }
            });
            loggedLbl.addStyleName("headerMenuMedItem headerMenuMedItemC");
            hP.add(loggedLbl);

            VerticalPanel vP = new VerticalPanel();

            Label lnk = new Label("Logout");
            lnk.addClickListener(new ClickListener() {

                public void onClick(Widget arg0) {
                    cdm.resetUser();
                    invokeTerminateSession();
                }
            });
            lnk.setStyleName("headerMenuTinyItem headerMenuTinyItemC");
            vP.add(lnk);

            hP.add(vP);

            Image steerable = new SteeringWheelWidget(SteeringWheelWidget.wheelSize.SMALL, new ClickListener() {

                public void onClick(Widget arg0) {
                    cdm.setSteerableReset(true);
                    History.newItem("steering:userCloud");
                }
            });
            steerable.setTitle("Steerable recommendations starting with your personal tag cloud");
            hP.add(steerable);

            // Plays a random recommendation
            instantRecPlayWidget = getInstantRecPlayWidget();
            if (instantRecPlayWidget != null) {
                hP.add(instantRecPlayWidget);
            }


            mainPanel.setWidget(0, 0, hP);
        } else {
            populateLoginBox();
        }
    }

    private Widget getInstantRecPlayWidget() {
        ArtistCompact[] aC = cdm.getListenerDetails().getRecommendations();
        if (aC.length > 0) {
            int itemIndex = Random.nextInt(aC.length);
            int iterations = 0;
            while (iterations++ < 2 * aC.length) {
                if (aC[itemIndex].getSpotifyId() != null && aC[itemIndex].getSpotifyId().length() > 0) {

                    ClickListener cL = new ClickListener() {
                        public void onClick(Widget arg0) {
                            instantRecPlayWidget = getInstantRecPlayWidget();
                            Window.alert("Implement recent played trigger");
                        }
                    };

                    if (playButton != null) {
                        playButton.onDelete();
                    }
                    playButton = new PlayButton(cdm, aC[itemIndex], PlayButton.PLAY_ICON_SIZE.SMALL, musicServer);
                    cdm.getMusicProviderSwitchListenerManager().addListener(playButton);
                    return playButton;
                }
            }
        }
        return null;
    }

    /**
     * Get user info for a non openid user
     * @param userKey user key
     */
    private void invokeGetUserSessionInfo(String userKey) {
        AsyncCallback callback = new AsyncCallback() {

            public void onSuccess(Object result) {

                ListenerDetails l = (ListenerDetails) result;
                updatePanelAfterLogin(l);
            }

            public void onFailure(Throwable caught) {
                Window.alert(caught.toString());
                populateLoginBox();
            }
        };

        try {
            musicServer.getNonOpenIdLogInDetails(userKey, callback);
        } catch (Exception ex) {
            populateLoginBox();
            Window.alert(ex.getMessage());
        }
    }

    /**
     * Get user info for a potentially logged in user. This will log in a user
     * who has just entered his openid info after being redirected here from the
     * openid servlet
     */
    private void invokeGetUserSessionInfo() {
        AsyncCallback callback = new AsyncCallback() {

            public void onSuccess(Object result) {

                ListenerDetails l = (ListenerDetails) result;
                updatePanelAfterLogin(l);
            }

            public void onFailure(Throwable caught) {
                Window.alert(caught.toString());
            }
        };

        try {
            musicServer.getLogInDetails(callback);
        } catch (Exception ex) {
            Window.alert(ex.getMessage());
        }
    }

    private void populateLoginBox() {

        if (playButton != null) {
            playButton.onDelete();
        }

        txtbox = new TextBox();
        txtbox.setText(Cookies.getCookie("app-openid-uniqueid"));
        txtbox.addKeyboardListener(new KeyboardListener() {

            public void onKeyPress(Widget arg0, char keyCode, int arg2) {
                if (keyCode == KEY_ENTER) {
                    fetchUserInfo();
                }
            }

            public void onKeyDown(Widget arg0, char arg1, int arg2) {
            }

            public void onKeyUp(Widget arg0, char arg1, int arg2) {
            }
        });

        Button b = new Button();
        b.setText("Login with your openID");
        b.addClickListener(new ClickListener() {

            public void onClick(Widget arg0) {
                fetchUserInfo();
            }
        });

        HorizontalPanel h = new HorizontalPanel();
        h.add(txtbox);
        h.add(b);
        mainPanel.setWidget(0, 0, h);

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

        AsyncCallback<HashMap<ArtistCompact, Double>> callback = new AsyncCallback<HashMap<ArtistCompact, Double>>() {

            public void onSuccess(HashMap<ArtistCompact, Double> aC) {
                // do some UI stuff to show success
                if (aC != null) {
                    cdm.updateUpdatableWidgets(aC);
                } else {
                    Window.alert("An error occured while fetching the new recommendations.");
                }
            }

            public void onFailure(Throwable caught) {
                Window.alert("An error occured while fetching the new recommendations.");
            }
        };

        try {
            musicServer.getSimilarArtists(artistID, cdm.getCurrSimTypeName(), cdm.getCurrPopularity(), callback);
        } catch (Exception ex) {
            Window.alert(ex.getMessage());
        }
    }

    public ArrayList<String> getTokenHeaders() {
        return new ArrayList<String>();
    }

    protected void initMenuItem() {
        // this does not have a menu
        menuItem = new MenuItem();
    }

    public void doRemoveListeners() {
        mm.onDelete();
        if (playButton != null) {
            cdm.getMusicProviderSwitchListenerManager().removeListener(playButton);
        }
    }

    public class MainMenu extends Composite implements LoginListener {

        private Grid p;
        private boolean loggedIn = false;

        public MainMenu() {
            p = new Grid(1,1);
            update();
            initWidget(p);
        }

        public Widget getWidget() {
            return p;
        }

        private void update() {
            HorizontalPanel hP = new HorizontalPanel();
            hP.setSpacing(8);

            if (menuItems !=null && menuItems.size()>0) {
                Collections.sort(menuItems, MenuItem.getOrderComparator());
                for (MenuItem mI : menuItems) {
                    if (!mI.mustBeLoggedIn() || (mI.mustBeLoggedIn() && loggedIn)) {
                        Label sLabel = new Label(mI.getName());
                        sLabel.addClickListener(mI.getClickListener());
                        sLabel.setStyleName("headerMenuMedItem headerMenuMedItemC");
                        mI.setLabel(sLabel);
                        hP.add(sLabel);
                    }
                }
            }
            p.setWidget(0, 0, hP);
        }

        public void onLogin(ListenerDetails lD) {
            loggedIn = true;
            update();
        }

        public void onLogout() {
            loggedIn = false;
            update();
        }

        public void onDelete() {
            cdm.getLoginListenerManager().removeListener(this);
        }
    }

}