/*
 * GWTMainEntryPoint.java
 *
 * Created on February 27, 2008, 1:55 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.labs.aura.dbbrowser.client;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.rpc.ServiceDefTarget;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * The entry point that sets up the initial page
 */
public class GWTMainEntryPoint implements EntryPoint {
    
    /** Creates a new instance of GWTMainEntryPoint */
    public GWTMainEntryPoint() {
    }
    
    /**
     * The entry point method, called automatically by loading a module
     * that declares an implementing class as an entry-point
     */
    public void onModuleLoad() {
        MainSelector sel = new MainSelector();
        sel.addTool("Query DB", new TabbedQueryUI());
        sel.addTool("Viz UI", new VizUI());
        RootPanel.get().add(sel);
        //RootPanel.get().add(button);
        //RootPanel.get().add(label);
    }
    
    public static DBServiceAsync getDBService(){
        // Create the client proxy. Note that although you are creating the
        // service interface proper, you cast the result to the asynchronous
        // version of
        // the interface. The cast is always safe because the generated proxy
        // implements the asynchronous interface automatically.
        DBServiceAsync service = (DBServiceAsync) GWT.create(DBService.class);
        // Specify the URL at which our service implementation is running.
        // Note that the target URL must reside on the same domain and port from
        // which the host page was served.
        //
        ServiceDefTarget endpoint = (ServiceDefTarget) service;
        String moduleRelativeURL = GWT.getModuleBaseURL() + "dbservice";
        endpoint.setServiceEntryPoint(moduleRelativeURL);
        return service;
    }

    public static VizServiceAsync getVizService(){
        // Create the client proxy. Note that although you are creating the
        // service interface proper, you cast the result to the asynchronous
        // version of
        // the interface. The cast is always safe because the generated proxy
        // implements the asynchronous interface automatically.
        VizServiceAsync service = (VizServiceAsync) GWT.create(VizService.class);
        // Specify the URL at which our service implementation is running.
        // Note that the target URL must reside on the same domain and port from
        // which the host page was served.
        //
        ServiceDefTarget endpoint = (ServiceDefTarget) service;
        String moduleRelativeURL = GWT.getModuleBaseURL() + "vizservice";
        endpoint.setServiceEntryPoint(moduleRelativeURL);
        return service;
    }

}
