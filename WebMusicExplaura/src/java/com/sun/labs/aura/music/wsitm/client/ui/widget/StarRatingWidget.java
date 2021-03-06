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

package com.sun.labs.aura.music.wsitm.client.ui.widget;


import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.sun.labs.aura.music.wsitm.client.event.RatingListener;
import com.sun.labs.aura.music.wsitm.client.event.LoginListener;
import com.sun.labs.aura.music.wsitm.client.*;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.AbstractImagePrototype;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Image;
import com.sun.labs.aura.music.wsitm.client.event.DEClickHandler;
import com.sun.labs.aura.music.wsitm.client.event.DEMouseOverHandler;
import com.sun.labs.aura.music.wsitm.client.items.ListenerDetails;
import com.sun.labs.aura.music.wsitm.client.ui.Popup;
import com.sun.labs.aura.music.wsitm.client.ui.bundles.StarsBundle;
import com.sun.labs.aura.music.wsitm.client.ui.bundles.VariaBundle;

/**
 *
 * @author mailletf
 */
public class StarRatingWidget extends Composite implements RatingListener, LoginListener {

    private MusicSearchInterfaceAsync musicServer;
    private ClientDataManager cdm;
    private String artistID;

    private final int NBR_STARS = 5;
    private int nbrSelectedStars = 0;
    private int oldNbrSelectedStars = nbrSelectedStars; // save to revert in case RPC call fails

    /**
     * If false, we are either displaying a loading image or nothing at all so we need to construct the whole widget
     */
    private boolean isInit = false;

    private static StarsBundle starsBundle = (StarsBundle) GWT.create(StarsBundle.class);
    private AbstractImagePrototype STAR_LID;
    private AbstractImagePrototype STAR_NOTLID;
    private AbstractImagePrototype STAR_WHITE;

    private Image[] images;

    private Grid g;

    public enum Size {
        SMALL,
        MEDIUM
    }

    public enum InitialRating {
        R0,R1,R2,R3,R4,R5,FETCH,DISPLAY_LOAD
    }

    public StarRatingWidget(MusicSearchInterfaceAsync musicServer, ClientDataManager cdm,
            String artistID, InitialRating rating, Size size) {

        g = new Grid(1, 1);
        initWidget(g);

        initializeRatingWidget(musicServer, cdm, artistID, -1, size);
        if (rating == InitialRating.FETCH && cdm.isLoggedIn()) {
            drawWaitWidget();
            invokeFetchRating();
        } else {
            if (rating == InitialRating.DISPLAY_LOAD) {
                drawWaitWidget();
            } else {
                nbrSelectedStars = ratingEnumToInt(rating);
                drawRatingWidget();
            }
        }
    }

    private void initializeRatingWidget(MusicSearchInterfaceAsync musicServer, ClientDataManager cdm,
            String artistID, int initialSelection, Size size) {

        this.nbrSelectedStars = initialSelection;
        this.cdm = cdm;
        this.artistID = artistID;
        this.musicServer = musicServer;

        cdm.getRatingListenerManager().addListener(artistID, this);

        if (size == Size.SMALL) {
            STAR_LID = starsBundle.starLidS();
            STAR_NOTLID = starsBundle.starNotLidS();
            STAR_WHITE = starsBundle.starWhiteS();
        } else {
            STAR_LID = starsBundle.starLid();
            STAR_NOTLID = starsBundle.starNotLid();
            STAR_WHITE = starsBundle.starWhite();
        }
    }

    @Override
    public void onDelete() {
        cdm.getRatingListenerManager().removeListener(artistID, this);
        cdm.getLoginListenerManager().removeListener(this);
    }

    private void drawWaitWidget() {
        isInit = false;
        g.setWidget(0, 0, new Image("ajax-loader-small.gif"));
    }

    private void drawRatingWidget() {

        isInit = true;
        FlowPanel p = new FlowPanel();

        images = new Image[NBR_STARS];
        for (int i=0; i<NBR_STARS; i++) {
            if (i<=nbrSelectedStars-1) {
                images[i] = STAR_NOTLID.createImage();
            } else {
                images[i] = STAR_WHITE.createImage();
            }
            images[i].addClickHandler(new DEClickHandler<Integer>(i) {
                @Override
                public void onClick(ClickEvent event) {
                    invokeSaveRating(data);
                }
            });
            images[i].addMouseOutHandler(new MouseOutHandler() {
                @Override
                public void onMouseOut(MouseOutEvent event) {
                    redrawStars();
                }
            });
            images[i].addMouseOverHandler(new DEMouseOverHandler<Integer>(i) {
                @Override
                public void onMouseOver(MouseOverEvent event) {
                    for (int i = 0; i <= data; i++) {
                        STAR_LID.applyTo(images[i]);
                    }
                }
            });
            p.add(images[i]);
        }
        g.setWidget(0, 0, p);
    }

    private void invokeSaveRating(int index) {

        if (!cdm.isLoggedIn()) {
            //Window.alert("Message from the happy tag : you must be logged in to access this feature. I should redirect you to another page so you can create an account, but I'd rather keep you here so we can be friends.");
            Popup.showLoginPopup();
            return;
        }

        AsyncCallback callback = new AsyncCallback() {

            public void onSuccess(Object result) {
                cdm.getRatingListenerManager().triggerOnRate(artistID, nbrSelectedStars);
            }

            public void onFailure(Throwable caught) {
                Popup.showErrorPopup(caught, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                        "save your rating for artist " + artistID + ".", Popup.ERROR_LVL.NORMAL, null);
                nbrSelectedStars = oldNbrSelectedStars;
                redrawStars();
            }
        };

        oldNbrSelectedStars = nbrSelectedStars;
        nbrSelectedStars = index + 1;
        redrawStars();

        try {
            musicServer.updateUserSongRating(index + 1, artistID, callback);
        } catch (Exception ex) {
            Popup.showErrorPopup(ex, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                    "save your rating for artist " + artistID + ".", Popup.ERROR_LVL.NORMAL, null);
        }

    }

    public void setNbrSelectedStarsWithNoDbUpdate(int nbrSelected) {
        if (nbrSelected > NBR_STARS) {
            nbrSelected = NBR_STARS;
        }

        oldNbrSelectedStars = nbrSelectedStars;
        nbrSelectedStars = nbrSelected;
        if (isInit) {
            redrawStars();
        } else {
            drawRatingWidget();
        }
    }

    private void redrawStars() {
        for (int i = 0; i < NBR_STARS; i++) {
            if (i <= nbrSelectedStars - 1) {
                STAR_NOTLID.applyTo(images[i]);
            } else {
                STAR_WHITE.applyTo(images[i]);
            }
        }
    }

    private void invokeFetchRating() {

        AsyncCallback callback = new AsyncCallback() {

            public void onSuccess(Object result) {
                nbrSelectedStars = (Integer) result;
                cdm.setRatingInCache(artistID, nbrSelectedStars);
                drawRatingWidget();
            }

            public void onFailure(Throwable caught) {
                Popup.showErrorPopup(caught, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                        "retrieve your rating.", Popup.ERROR_LVL.NORMAL, null);
            }
        };

        int rating = cdm.getRatingFromCache(artistID);
        if (rating == -1) {
            try {
                musicServer.fetchUserSongRating(artistID, callback);
            } catch (WebException ex) {
                Popup.showErrorPopup(ex, Popup.ERROR_MSG_PREFIX.ERROR_OCC_WHILE,
                        "retrieve your rating.", Popup.ERROR_LVL.NORMAL, null);
            }
        } else {
            callback.onSuccess(rating);
        }
    }

    public void onLogin(ListenerDetails lD) {
        invokeFetchRating();
    }

    public void onLogout() {
        nbrSelectedStars = 0;
        redrawStars();
    }

    public void onRate(String itemId, int rating) {
        nbrSelectedStars = rating;
        drawRatingWidget();
    }

    public static int ratingEnumToInt(InitialRating iR) {
        if (iR == InitialRating.R0) {
            return 0;
        } else if (iR == InitialRating.R1) {
            return 1;
        } else if (iR == InitialRating.R2) {
            return 2;
        } else if (iR == InitialRating.R3) {
            return 3;
        } else if (iR == InitialRating.R4) {
            return 4;
        } else if (iR == InitialRating.R5) {
            return 5;
        } else {
            return -1;
        }
    }

    public static InitialRating intToRatingEnum(int rating) {
        if (rating==0) {
            return InitialRating.R0;
        } else if (rating==1) {
            return InitialRating.R1;
        } else if (rating==2) {
            return InitialRating.R2;
        } else if (rating==3) {
            return InitialRating.R3;
        } else if (rating==4) {
            return InitialRating.R4;
        } else if (rating==5) {
            return InitialRating.R4;
        } else {
            return InitialRating.FETCH;
        }
    }
}
