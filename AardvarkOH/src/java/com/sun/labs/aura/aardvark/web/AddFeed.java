/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.aardvark.web;

import com.sun.labs.aura.aardvark.Aardvark;
import com.sun.labs.aura.util.AuraException;
import java.io.*;
import java.net.*;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import javax.servlet.*;
import javax.servlet.http.*;

/**
 *
 * @author ja151348
 */
public class AddFeed extends HttpServlet {
    protected Logger logger = Logger.getLogger("");
    
    protected Aardvark aardvark;
    
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        aardvark = (Aardvark)config.getServletContext()
                .getAttribute("aardvark");
    }
    
    /** 
    * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
    * @param request servlet request
    * @param response servlet response
    */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        ServletContext context = getServletContext();
        String op = request.getParameter("op");
        if (op == null) {
            if (request.getContentType() != null &&
                    request.getContentType().startsWith("multipart/form-data")) {
                op = "Upload";
            } else {
                op = "";
            }
        }
        
        try {
            Shared.fillPageHeader(request, aardvark);
        } catch (AuraException e) {
            logger.log(Level.WARNING, "Failed to fill stats", e);
        }
        if (op.equals("Add")) {
            String feed = request.getParameter("feed");
            try {
                aardvark.addFeed(feed);
                request.setAttribute("msg", "Added feed to Aardvark");
            } catch (AuraException e) {
                logger.log(Level.WARNING, "Failed to add feed", e);
                request.setAttribute("msg", "Failed to add feed: " + e.getMessage());
            }
        } else if (op.equals("Upload")) {
            //String opml = request.getParameter("opml");
            //System.out.println("OPML:\n" + opml);
            try {
                ByteArrayDataSource bads =
                        new ByteArrayDataSource(request.getInputStream(), "text/xml");
                MimeMultipart multiPart = new MimeMultipart(bads);
                BodyPart bp = multiPart.getBodyPart(0);
                InputStream is = bp.getInputStream();
                byte[] content = new byte[bp.getSize()];
                is.read(content, 0, bp.getSize());
                aardvark.addOPML(content);
                request.setAttribute("msg", "Done");
            } catch (MessagingException e) {
                request.setAttribute("msg", "Error reading uploaded file: "
                        + e.getMessage());
            } catch (AuraException e) {
                request.setAttribute("msg", "Error parsing file: "
                        + e.getMessage());
            }
        }
        RequestDispatcher rd = context.getRequestDispatcher("/addFeed.jsp");
        rd.forward(request, response);
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
    }
    // </editor-fold>
}