/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.music.wsitm.client.ui;

import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.DialogBox;
import com.google.gwt.user.client.ui.DockPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 *
 * @author mailletf
 */
public abstract class Popup {

    public static DialogBox getDialogBox() {
        final DialogBox popup = new DialogBox(true);
        return popup;
    }

    public static void showPopup(Widget w, String title) {
        showPopup(w, title, getDialogBox());
    }

    public static void showPopup(Widget w, String title, final DialogBox popup) {

        DockPanel docPanel = new DockPanel();

        Label closeButton = new Label("Close");
        closeButton.setStyleName("clickableLabel");
        closeButton.addStyleName("whiteTxt");
        closeButton.addClickListener(new ClickListener() {

            public void onClick(Widget sender) {
                popup.hide();
            }
        });

        FlowPanel container = new FlowPanel();
        container.setStyleName("outerpopup");
        container.add(w);

        docPanel.add(container, DockPanel.CENTER);
        docPanel.add(closeButton, DockPanel.SOUTH);
        docPanel.setCellHorizontalAlignment(closeButton, DockPanel.ALIGN_RIGHT);
        popup.add(docPanel);
        popup.setText(title);
        popup.setAnimationEnabled(true);
        popup.center();
    }

    public static PopupPanel getPopupPanel() {
        final PopupPanel popup = new PopupPanel(true);
        return popup;
    }

    public static void showRoundedPopup(Widget w, String title) {
        showRoundedPopup(w, title, getPopupPanel());
    }
    
    public static void showRoundedPopup(Widget w, String title, final PopupPanel popup) {

        VerticalPanel vP = new VerticalPanel();
        if (title != null && title.length() > 0) {
            Label titleLabel = new Label(title);
            titleLabel.setStyleName("popupColors");
            titleLabel.addStyleName("popupTitle");
            vP.add(titleLabel);
        }
        w.getElement().getStyle().setPropertyPx("padding", 5);
        w.addStyleName("popupColors");
        vP.add(w);
        
        Grid fP = new Grid(1,1);
        fP.setStyleName("popupColors");
        fP.setHeight("100%");
        fP.setWidget(0, 0, vP);
        
        RoundedPanel rp = new RoundedPanel(fP, RoundedPanel.ALL, 5);
        rp.setCornerStyleName("popupColors");
        popup.add(rp);
        popup.setAnimationEnabled(true);
        popup.center();
        popup.center();
    }
}
