/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.music.wsitm.client.ui.widget;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.MouseListener;
import com.google.gwt.user.client.ui.Widget;
import com.sun.labs.aura.music.wsitm.client.WebException;
import com.sun.labs.aura.music.wsitm.client.items.ArtistCompact;
import com.sun.labs.aura.music.wsitm.client.ui.ContextMenuImage;
import com.sun.labs.aura.music.wsitm.client.ui.SharedSteeringMenu;

/**
 *
 * @author mailletf
 */
public class SteeringWheelWidget extends ContextMenuImage {

    private static final String steerImageBig = "steering-30.gif";
    private static final String steerImageSmall = "steering-20.gif";

    private static final String steerImageHoverBig = "steering-hover-30.gif";
    private static final String steerImageHoverSmall = "steering-hover-20.gif";

    private String steerImage;
    private String steerImageHover;

    private ArtistCompact aC;
  
    public enum wheelSize {
        SMALL,
        BIG
    }

    public SteeringWheelWidget(wheelSize size, ArtistCompact aC, SharedSteeringMenu steeringMenu) {
        super("", steeringMenu);

        this.aC = aC;

        if (size == wheelSize.SMALL) {
            steerImage = steerImageSmall;
            steerImageHover = steerImageHoverSmall;
        } else {
            steerImage = steerImageBig;
            steerImageHover = steerImageHoverBig;
        }
        
        setUrl(steerImage);
        addStyleName("pointer");

        this.addMouseListener(new MouseListener() {

            public void onMouseEnter(Widget arg0) {
                setUrl(steerImageHover);
            }

            public void onMouseLeave(Widget arg0) {
                setUrl(steerImage);
            }

            public void onMouseDown(Widget arg0, int arg1, int arg2) {}
            public void onMouseMove(Widget arg0, int arg1, int arg2) {}
            public void onMouseUp(Widget arg0, int arg1, int arg2) {}
        });
    }

    @Override
    public void onBrowserEvent(Event event) {
        if (event.getTypeInt() == Event.ONCONTEXTMENU ||
                event.getTypeInt() == Event.ONCLICK) {
            try {
                DOM.eventPreventDefault(event);
                cm.showSharedMenu(event, aC);
            } catch (WebException ex) {
                Window.alert(ex.toString());
            }
        } else {
            super.onBrowserEvent(event);
        }
    }
}