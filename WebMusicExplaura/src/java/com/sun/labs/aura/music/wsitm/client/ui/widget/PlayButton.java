/*
 * Copyright 2008-2009 Sun Microsystems, Inc. All Rights Reserved.
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

package com.sun.labs.aura.music.wsitm.client.ui.widget;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.ClickListenerCollection;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.SourcesClickEvents;
import com.google.gwt.user.client.ui.Widget;
import com.sun.labs.aura.music.wsitm.agentspecific.impl.CssDefsImpl;
import com.sun.labs.aura.music.wsitm.client.ClientDataManager;
import com.sun.labs.aura.music.wsitm.client.event.MusicProviderSwitchListener;
import com.sun.labs.aura.music.wsitm.client.event.SourcesRightClickEvents;
import com.sun.labs.aura.music.wsitm.client.items.ArtistCompact;
import com.sun.labs.aura.music.wsitm.client.ui.ContextMenu;
import com.sun.labs.aura.music.wsitm.client.ui.ContextMenu.HasContextMenu;
import com.sun.labs.aura.music.wsitm.client.MusicSearchInterfaceAsync;
import com.sun.labs.aura.music.wsitm.client.WebLib;
import com.sun.labs.aura.music.wsitm.client.event.DDEClickHandler;
import com.sun.labs.aura.music.wsitm.client.items.ItemInfo;
import com.sun.labs.aura.music.wsitm.client.ui.Popup;
import com.sun.labs.aura.music.wsitm.client.ui.SharedPlayButtonMenu;
import com.sun.labs.aura.music.wsitm.client.ui.bundles.ArtistRelatedBundle;

/**
 *
 * @author mailletf
 */
public class PlayButton extends Composite implements MusicProviderSwitchListener, 
        SourcesClickEvents, SourcesRightClickEvents, HasContextMenu {

    public static ArtistRelatedBundle playImgBundle = (ArtistRelatedBundle) GWT.create(ArtistRelatedBundle.class);

    private static SharedPlayButtonMenu cm;
    private MusicSearchInterfaceAsync musicServer;

    private ClickListenerCollection clickListeners;
    private ClickListenerCollection rightClickListeners;

    private ClientDataManager cdm;
    private ArtistCompact aC;
    private PLAY_ICON_SIZE size;

    private static ClickHandler triggerPlayClickHandler;
    private Grid mainbutton;
    
    public enum PLAY_ICON_SIZE {
        SMALL, MEDIUM, BIG
    }
    
    public enum MusicProviders {
        SPOTIFY,
        LASTFM,
        THEWEB
    }
    
    public PlayButton(ClientDataManager tcdm, ArtistCompact taC,
            PLAY_ICON_SIZE size, MusicSearchInterfaceAsync musicServer) {
        
        this.cdm = tcdm;
        this.aC = taC;
        this.size = size;
        this.musicServer = musicServer;
        
        if (cm == null) {
            cm = cdm.getSharedPlayButtonMenu();
        }
        
        if (triggerPlayClickHandler == null) {
            triggerPlayClickHandler = new ClickHandler() {
                @Override
                public void onClick(ClickEvent ce) {
                    cdm.getPlayedListenerManager().triggerOnPlay(aC.getId());
                    WebLib.trackPageLoad("play", aC.getId(), aC.getEncodedName());
                }
            };
        }

        mainbutton = new Grid(1,1);
        setNewButton(cdm.getCurrPreferedMusicProvider());
        initWidget(mainbutton);
        
        sinkEvents(Event.ONCLICK | Event.ONCONTEXTMENU);
        rightClickListeners = new ClickListenerCollection();

        addStyleName("pointer");
    }
    
    private void setNewButton(MusicProviders preferredMP) {
        
        Widget w = null;
        if (preferredMP != null) {
            w = tryProviders(preferredMP);
        }
        if (w == null) {
            w = tryProviders(null);
        }
        mainbutton.setWidget(0, 0, w);
        
    }

    /**
     * Try different music providers
     * @param preferredMP null to return the best one. not null to try to get a specific provider
     * @return
     */
    private Widget tryProviders(MusicProviders preferredMP) {
        
        Widget w = null;
        
        // Try to get widget for preferred provider
        if (preferredMP == null || preferredMP == MusicProviders.LASTFM) {
            w = getLastFMListenWidget(triggerPlayClickHandler);
            if (w != null) {
                return w;
            }
        }
        if (preferredMP == null || preferredMP == MusicProviders.SPOTIFY) {
            w = getSpotifyListenWidget(triggerPlayClickHandler);
            if (w != null) {
                return w;
            }
        }
        if (preferredMP == null || preferredMP == MusicProviders.THEWEB) {
            w = getTheWebListenWidget(triggerPlayClickHandler);
            if (w != null) {
                return w;
            }
        }
        return null;
    }
    
    @Override
    public void onSwitch(MusicProviders newMp) {
        setNewButton(newMp);
    }

    @Override
    public void onDelete() {
        cdm.getMusicProviderSwitchListenerManager().removeListener(this);
    }

    @Override
    public void onBrowserEvent(Event event) {
        if (event.getTypeInt() == Event.ONCONTEXTMENU) {
            DOM.eventPreventDefault(event);
            cm.showAt(event, aC);
            rightClickListeners.fireClick(this);
        } else {
            // Fire our own click listeners if we have any and then pass on event
            if (event.getTypeInt() == Event.ONCLICK) {
                if (clickListeners != null) {
                    clickListeners.fireClick(this);
                }
            }
            super.onBrowserEvent(event);
        }
    }

    public void addRightClickListener(ClickListener listener) {
        rightClickListeners.add(listener);
    }

    public void removeRightClickListener(ClickListener listener) {
        if (rightClickListeners != null) {
            rightClickListeners.remove(listener);
        }
    }

    public void addClickListener(ClickListener listener) {
        if (clickListeners == null) {
            clickListeners = new ClickListenerCollection();
        }
        clickListeners.add(listener);
    }

    public void removeClickListener(ClickListener listener) {
        if (clickListeners != null) {
            clickListeners.remove(listener);
        }
    }

    @Override
    public ContextMenu getContextMenu() {
        return cm;
    }

    private Widget getTheWebListenWidget(ClickHandler cH) {
        if (aC.getAudio() == null || aC.getAudio().isEmpty()) {
            return null;
        } else {
            String[] urls = aC.getAudio().toArray(new String[0]);
            HTML html = new HTML("<object type=\"application/x-shockwave-flash\" " +
                "data=\"musicplayer.swf?song_url="+urls[0]+"\" width=\"17\" height=\"17\">" +
                "<param name=\"movie\" value=\"musicplayer.swf?song_url="+urls[0]+"\" />" +
                "<a href=\"/search/play/eaa6699bdb078500e4b567\">play</a></object>");
            html.addClickHandler(cH);
            return html;
        }
    }

    private Widget getSpotifyListenWidget(ClickHandler cH) {
        String musicURL = aC.getSpotifyId();
        int intSize = playIconSizeToInt(size);
        if (musicURL != null && !musicURL.equals("")) {

            String spotHtml;
            if (intSize==20) {
                spotHtml = playImgBundle.playSpotify20().getHTML();
            } else if (intSize==30) {
                spotHtml = playImgBundle.playSpotify30().getHTML();
            } else {
                spotHtml = playImgBundle.playSpotify40().getHTML();
            }

            HTML html = new HTML("<a href=\"" + musicURL + "\" target=\"spotifyFrame\">"+spotHtml+"</a>");
            html.setTitle("Play " + aC.getName() + " with Spotify");
            if (cdm.isLoggedIn()) {
                html.addClickHandler(new ClickHandler() {
                    @Override
                    public void onClick(ClickEvent ce) {
                        AsyncCallback callback = new AsyncCallback() {
                            public void onSuccess(Object result) {}
                            public void onFailure(Throwable caught) {
                                Popup.showErrorPopup(caught, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                                    "add your play attention for artist " + aC + ".", Popup.ERROR_LVL.NORMAL, null);
                            }
                        };

                        try {
                            musicServer.addPlayAttention(aC.getId(), callback);
                        } catch (Exception ex) {
                                Popup.showErrorPopup(ex, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                                    "add your play attention for artist " + aC + ".", Popup.ERROR_LVL.NORMAL, null);
                        }
                    }
                });
            }
            if (cH != null) {
                html.addClickHandler(cH);
            }
            return html;
        } else {
            return null;
        }
    }

    public String getSimilarArtistRadioLink(boolean useTags) {
        if (useTags) {
            ItemInfo tag = WebLib.getBestTag(aC);
            if (tag != null) {
                return WebLib.getTagRadioLink(tag.getItemName());
            } else {
                return getSimilarArtistRadioLink(false);
            }
        } else {
            String link = "http://www.last.fm/webclient/popup/?radioURL=" + "lastfm://artist/ARTIST_REPLACE_ME/similarartists&resourceID=undefined" + "&resourceType=undefined&viral=true";
            return link.replaceAll("ARTIST_REPLACE_ME", aC.getEncodedName());
        }
    }

    public String getArtistRadioLink() {
        String link = CssDefsImpl.impl.getLastFmRadioPrefix() + "MusicPlayer?name=" +aC.getEncodedName();
        return link;
    }
    
    private Widget getSimilarArtistRadio() {
        String embeddedObject = "<object width=\"340\" height=\"123\">" + "<param name=\"movie\" value=\"http://panther1.last.fm/webclient/50/defaultEmbedPlayer.swf\" />" + "<param name=FlashVars value=\"viral=true&lfmMode=radio&amp;radioURL=lastfm://artist/ARTIST_NAME/similarartists&amp;" + "restTitle= ARTIST_NAME’s Similar Artists \" />" + "<param name=\"wmode\" value=\"transparent\" />" + "<embed src=\"http://panther1.last.fm/webclient/50/defaultEmbedPlayer.swf\" width=\"340\" " + "FlashVars=\"viral=true&lfmMode=radio&amp;radioURL=" + "lastfm://artist/ARTIST_NAME/similarartists&amp;restTitle= ARTIST_NAME’s Similar Artists \" height=\"123\" " + "type=\"application/x-shockwave-flash\" wmode=\"transparent\" />" + "</object>";
        embeddedObject = embeddedObject.replaceAll("ARTIST_NAME", aC.getEncodedName());
        return new HTML(embeddedObject);
    }


    private void popupSimilarArtistRadio(boolean useTags) {
        Window.open(getSimilarArtistRadioLink(useTags), "lastfm_popup", "width=400,height=170,menubar=no,toolbar=no,directories=no," + "location=no,resizable=no,scrollbars=no,status=no");
    }

    private void popupArtistRadio() {
        //Window.open(getArtistRadioLink(), "lastfm_popup", "width=300,height=266,menubar=no,toolbar=no,directories=no," 
        //        + "location=no,titlebar=no,dialog=no,resizable=yes,scrollbars=no,status=no");
        Window.open(getArtistRadioLink(), "lastfm_popup", "width=330,height=296,titlebar=no");
    }
    
    private Widget getLastFMListenWidget(ClickHandler cH) {
        int intSize = playIconSizeToInt(size);
        Image image;
        if (intSize == 20) {
            image = playImgBundle.playLastfm20().createImage();
        } else if (intSize == 30) {
            image = playImgBundle.playLastfm30().createImage();
        } else {
            image = playImgBundle.playLastfm40().createImage();
        }
        image.setTitle("Play music like " + aC.getName() + " at last.fm");
        image.addClickHandler(new ClickHandler() {
            @Override
            public void onClick(ClickEvent ce) {
                //popupSimilarArtistRadio(true);
                popupArtistRadio();
            }
        });
        if (cdm.isLoggedIn()) {
            image.addClickHandler(new DDEClickHandler<MusicSearchInterfaceAsync, String>(musicServer, aC.getId()) {
                @Override
                public void onClick(ClickEvent ce) {
                    AsyncCallback callback = new AsyncCallback() {

                        public void onSuccess(Object result) {}
                        public void onFailure(Throwable caught) {
                                Popup.showErrorPopup(caught, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                                    "add your play attention for artist " + aC + ".", Popup.ERROR_LVL.NORMAL, null);
                        }
                    };

                    try {
                        data.addPlayAttention(sndData, callback);
                    } catch (Exception ex) {
                        Popup.showErrorPopup(ex, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                                "add your play attention for artist " + aC + ".", Popup.ERROR_LVL.NORMAL, null);
                    }
                }
            });
        }
        if (cH != null) {
            image.addClickHandler(cH);
        }
        return image;
    }

    /**
     * Convert the play button size enum into the actual size in px
     * @param size PLAY_ICON_SIZE enum
     * @return actual size in px
     */
    private static int playIconSizeToInt(PLAY_ICON_SIZE size) {
        if (size == PLAY_ICON_SIZE.SMALL) {
            return 20;
        } else if (size == PLAY_ICON_SIZE.MEDIUM) {
            return 30;
        } else {
            return 40;
        }
    }
 
}
