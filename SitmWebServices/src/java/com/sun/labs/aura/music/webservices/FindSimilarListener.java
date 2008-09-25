/*
 *  Copyright (c) 2008, Sun Microsystems Inc.
 *  See license.txt for license.
 */

package com.sun.labs.aura.music.webservices;

import com.sun.labs.aura.music.Listener;
import com.sun.labs.aura.music.MusicDatabase;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.Scored;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author plamere
 */
public class FindSimilarListener extends HttpServlet {

    private final static String SERVLET_NAME = "FindSimilarListener";

    /** 
    * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
    * @param request servlet request
    * @param response servlet response
    */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
                ServletContext context = getServletContext();

        response.setContentType("text/xml;charset=UTF-8");
        PrintWriter out = response.getWriter();
        Timer timer = Util.getTimer();

        try {
            MusicDatabase mdb = (MusicDatabase) context.getAttribute("MusicDatabase");

            if (mdb == null) {
                Util.outputStatus(out, SERVLET_NAME, Util.ErrorCode.InternalError, "Can't connect to the music database");
            } else {
                int maxCount = 100;
                String maxCountString = request.getParameter("max");
                if (maxCountString != null) {
                    maxCount = Integer.parseInt(maxCountString);
                }

                String userID = request.getParameter("userID");
                if (userID != null) {
                    try {
                        Listener listener = mdb.getListener(userID);
                        if (listener != null) {
                            List<Scored<Listener>> similarListeners = mdb.listenerFindSimilar(userID, maxCount);
                            out.println("<FindSimilarListener userID=\"" + userID + "\" name=\"" 
                                    + Util.filter(listener.getName()) + "\">");
                            for (Scored<Listener> scoredListener : similarListeners) {

                                if (scoredListener.getItem().getKey().equals(userID)) {
                                    continue;
                                }

                                Listener simListener = scoredListener.getItem();
                                out.println("    <listener userID=\"" +
                                        simListener.getKey() + "\" " +
                                        "score=\"" + scoredListener.getScore() + "\" " +
                                        "name=\"" + Util.filter(simListener.getName()) + "\"" +
                                        "/>");
                            }
                            Util.outputOKStatus(out);
                            Util.tagClose(out, SERVLET_NAME);
                        } else {
                            Util.outputStatus(out, SERVLET_NAME, Util.ErrorCode.BadArgument, "Can't find user with id " + userID);
                        }
                    } catch (AuraException ex) {
                        Util.outputStatus(out, SERVLET_NAME, Util.ErrorCode.InternalError, "Problem accessing data " + ex);
                    }
                } else {
                    Util.outputStatus(out, SERVLET_NAME, Util.ErrorCode.MissingArgument, "Missing userID");
                }
            }
        } finally {
            timer.report(out);
            out.close();
        }
    } 

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** 
    * Handles the HTTP <code>GET</code> method.
    * @param request servlet request
    * @param response servlet response
    */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        processRequest(request, response);
    } 

    /** 
    * Handles the HTTP <code>POST</code> method.
    * @param request servlet request
    * @param response servlet response
    */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        processRequest(request, response);
    }

    /** 
    * Returns a short description of the servlet.
    */
    public String getServletInfo() {
        return "Short description";
    }// </editor-fold>

}
