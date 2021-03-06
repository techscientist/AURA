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

package com.sun.labs.aura.aardvark.dashboard.web;

import com.sun.labs.aura.aardvark.Aardvark;
import com.sun.labs.aura.datastore.DataStore;
import com.sun.labs.aura.util.StatService;
import com.sun.labs.util.props.ConfigurationManager;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 *
 */
public class ServletListener implements ServletContextListener {

    protected Logger logger = Logger.getLogger("");

    public void contextInitialized(ServletContextEvent sce) {
        try {
            ServletContext context = sce.getServletContext();

            URL config = context.getResource("/dashboardWebConfig.xml");
            logger.info("Config URL is " + config);

            //
            // Get the Aardvark interface
            try {
                ConfigurationManager cm = new ConfigurationManager();
                cm.addProperties(config);
                context.setAttribute("configManager", cm);

                Aardvark aardvark = (Aardvark) cm.lookup("aardvark");
                context.setAttribute("aardvark", aardvark);
                DataStore dataStore = (DataStore) cm.lookup("dataStore");
                context.setAttribute("dataStore", dataStore);

                // not sure why this lookup is failing
                //StatService statService = (StatService) cm.lookup("statService");
                StatService statService = null;
                if (statService == null) {
                    logger.severe("Failed to get statsevice handle");
                } else {
                    context.setAttribute("statService", statService);
                }

            } catch (IOException ioe) {
                logger.log(Level.SEVERE, "Failed to get Aardvark handle", ioe);
            }
        } catch (MalformedURLException ex) {
            logger.severe("Bad URL to config file " + ex.getMessage());
        }
    }

    public void contextDestroyed(ServletContextEvent arg0) {

    }
}
