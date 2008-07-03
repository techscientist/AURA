
package com.sun.labs.aura.music.wsitm.client;

import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.MouseListener;
import com.google.gwt.user.client.ui.MouseListenerCollection;
import com.google.gwt.user.client.ui.Panel;
import com.google.gwt.user.client.ui.SourcesClickEvents;
import com.google.gwt.user.client.ui.SourcesMouseEvents;
import com.google.gwt.user.client.ui.Widget;

/**
 *
 * @author mailletf
 */
public abstract class DeletableWidget <T extends Widget> extends Composite
        implements SourcesMouseEvents {

    
    protected Panel mainPanel; // panel containing the main widget and the close button
    protected T w; // main widget
    protected Widget xB; // close button
    protected boolean isHovering = false;

    private MouseListenerCollection mouseListeners;

    public DeletableWidget(T w) {
        super();
        doDefaultXButtonConstruct(w, new SpannedFlowPanel());
    }

    public DeletableWidget(T w, Panel mainPanel) {
        super();
        doDefaultXButtonConstruct(w, mainPanel);
    }

    private void doDefaultXButtonConstruct(T w, Panel mainPanel) {
        this.w = w;
        this.mainPanel = mainPanel;

        xB = new Image("green-x.jpg");
        xB.getElement().setAttribute("style", "vertical-align:top; display:none; margin-top: 3px;");

        addWidgetsToPanel();
        initWidget(this.mainPanel);
        addListenersToWidgets();
    }

    public DeletableWidget(T w, Panel mainPanel, Widget xButton) {
        super();

        this.w = w;
        this.xB = xButton;
        this.mainPanel = mainPanel;

        addWidgetsToPanel();
        initWidget(this.mainPanel);
        addListenersToWidgets();
    }
    
    /**
     * Adds widgets to mainPanel. Overwrite if the passed main panel does not support
     * the add method or you wish to add widgets in another order than widget and then
     * x button.
     */
    public void addWidgetsToPanel() {
        mainPanel.add(w);
        mainPanel.add(xB);
    }
    
    public void addListenersToWidgets() {
        this.addMouseListener(new MouseListener() {

            public void onMouseDown(Widget arg0, int arg1, int arg2) {
            }

            public void onMouseEnter(Widget arg0) {
                isHovering = true;
                xB.setVisible(true);
            }

            public void onMouseLeave(Widget arg0) {
                Timer t = new Timer() {

                    public void run() {
                        if (!isHovering) {
                            xB.setVisible(false);
                        }
                    }
                };
                t.schedule(100);
                isHovering = false;
            }

            public void onMouseMove(Widget arg0, int arg1, int arg2) {
            }

            public void onMouseUp(Widget arg0, int arg1, int arg2) {
            }
        });

        ((SourcesClickEvents)xB).addClickListener(new ClickListener() {

            public void onClick(Widget arg0) {
                onDelete();
            }
        });
    }

    /**
     * Returns the contained widget
     * @return
     */
    public T getWidget() {
        return w;
    }

    public void addMouseListener(MouseListener listener) {
        if (mouseListeners == null) {
            mouseListeners = new MouseListenerCollection();
            sinkEvents(Event.MOUSEEVENTS);
        }
        mouseListeners.add(listener);
    }

    public void removeMouseListener(MouseListener listener) {
        if (mouseListeners != null) {
            mouseListeners.remove(listener);
        }
    }

    @Override
    public void onBrowserEvent(Event event) {
        switch (DOM.eventGetType(event)) {

            case Event.ONMOUSEDOWN:
            case Event.ONMOUSEUP:
            case Event.ONMOUSEMOVE:
            case Event.ONMOUSEOVER:
            case Event.ONMOUSEOUT:
                if (mouseListeners != null) {
                    mouseListeners.fireMouseEvent(this, event);
                }
                break;
        }
    }

    /**
     * Called when the widget's delete button is clicked
     */
    public abstract void onDelete();

    public class SpannedFlowPanel extends FlowPanel {

        public SpannedFlowPanel() {
            setElement(DOM.createSpan());
        }
    }
}
