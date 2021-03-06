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

package com.sun.labs.aura.dbbrowser.server;
import com.sun.labs.aura.dbbrowser.client.query.AttnDesc;
import com.sun.labs.aura.dbbrowser.client.query.ItemDesc;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.sun.labs.aura.datastore.Attention;
import com.sun.labs.aura.datastore.AttentionConfig;
import com.sun.labs.aura.datastore.DataStore;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.SimilarityConfig;
import com.sun.labs.aura.dbbrowser.client.query.DBService;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.RemoteComponentManager;
import com.sun.labs.aura.util.Scored;
import com.sun.labs.minion.util.StopWatch;
import com.sun.labs.util.props.ConfigurationManager;
import java.io.IOException;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 *
 */
public class DBServiceImpl extends RemoteServiceServlet implements
        DBService {

    protected static RemoteComponentManager rcm;
    protected static Logger logger = Logger.getLogger("");

    /**
     * @return the store
     */
    public static DataStore getStore() throws AuraException {
        return (DataStore)rcm.getComponent();
    }
    
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        ServletContext context = getServletContext();
        ConfigurationManager cm = (ConfigurationManager)context.getAttribute("configManager");
        rcm = new RemoteComponentManager(cm, DataStore.class);
    }
    
    /**
     * Log the user and host when new sessions start
     * 
     * @param request the request
     * @param response the response
     */
    @Override
    public void service(HttpServletRequest request,
                        HttpServletResponse response)
            throws ServletException, IOException
    {
        HttpSession s = request.getSession();
        if (s.isNew()) {
            logger.info("New session started for "
                    + request.getRemoteUser()
                    + " from " + request.getRemoteHost());
        }
        super.service(request, response);
    }

    public ItemDesc[] searchItemByKey(String key) {
        try {
            String q = "aura-key <substring> " + key;
            StopWatch sw = new StopWatch();
            sw.start();
            List<Scored<Item>> res = getStore().query(q, 10, null);
            sw.stop();
            ItemDesc[] results = new ItemDesc[res.size() + 1];
            results[0] = new ItemDesc(sw.getTime());
            int i = 1;
            for (Scored<Item> si : res) {
                results[i++] = Factory.itemDesc(si.getItem());
            }
            return results;
        } catch (AuraException ex) {
            logger.log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public ItemDesc[] searchItemByName(String key) {
        try {
            String q = "aura-name <substring> " + key;
            StopWatch sw = new StopWatch();
            sw.start();
            List<Scored<Item>> res = getStore().query(q, 10, null);
            sw.stop();
            ItemDesc[] results = new ItemDesc[res.size() + 1];
            results[0] = new ItemDesc(sw.getTime());
            int i = 1;
            for (Scored<Item> si : res) {
                results[i++] = Factory.itemDesc(si.getItem());
            }
            return results;
        } catch (AuraException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public ItemDesc[] searchItemByGen(String query) {
        try {
            StopWatch sw = new StopWatch();
            sw.start();
            List<Scored<Item>> res = getStore().query(query, 10, null);
            sw.stop();
            ItemDesc[] results = new ItemDesc[res.size() + 1];
            results[0] = new ItemDesc(sw.getTime());
            int i = 1;
            for (Scored<Item> si : res) {
                results[i++] = Factory.itemDesc(si.getItem());
            }
            return results;
        } catch (AuraException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public ItemDesc[] findSimilar(String key) {
        try {
            StopWatch sw = new StopWatch();
            sw.start();
            SimilarityConfig fsc = new SimilarityConfig("content", 10);
            List<Scored<Item>> res = getStore().findSimilar(key, fsc);
            sw.stop();
            ItemDesc[] results = new ItemDesc[res.size() + 1];
            results[0] = new ItemDesc(sw.getTime());
            int i = 1;
            for (Scored<Item> si : res) {
                results[i++] = Factory.itemDesc(si.getItem());
            }
            return results;
        } catch (AuraException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    
    public AttnDesc[] getAttentionForSource(String key) {
        try {
            StopWatch sw = new StopWatch();
            sw.start();
            AttentionConfig ac = new AttentionConfig();
            ac.setSourceKey(key);
            List<Attention> attn = getStore().getAttention(ac);
            sw.stop();
            int numResults = Math.min(attn.size(), 100);
            AttnDesc[] results = new AttnDesc[numResults + 1];
            results[0] = new AttnDesc(sw.getTime(), attn.size());
            int i = 1;
            for (Attention a : attn) {
                if (i > numResults) {
                    break;
                }
                results[i++] = Factory.attnDesc(a);
            }
            return results;
        } catch (AuraException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public AttnDesc[] getAttentionForTarget(String key) {
        try {
            StopWatch sw = new StopWatch();
            sw.start();
            AttentionConfig ac = new AttentionConfig();
            ac.setTargetKey(key);
            List<Attention> attn = getStore().getAttention(ac);
            sw.stop();
            int numResults = Math.min(attn.size(), 100);
            AttnDesc[] results = new AttnDesc[numResults + 1];
            results[0] = new AttnDesc(sw.getTime(), attn.size());
            int i = 1;
            for (Attention a : attn) {
                if (i > numResults) {
                    break;
                }
                results[i++] = Factory.attnDesc(a);
            }
            return results;
        } catch (AuraException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    public HashMap getItemInfo(String key) {
        try {
            HashMap<String,String> result = new HashMap<String,String>();
            Item i = getStore().getItem(key);
            for (Entry<String,Serializable> ent : i) {
                String name = ent.getKey();
                Serializable val = ent.getValue();
                
                String display = val.toString();
                result.put(name, display);
            }
            if (result.isEmpty()) {
                return null;
            }
            return result;
        } catch (AuraException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public void deleteItem(String key) {
        try {
            getStore().deleteItem(key);
        } catch (AuraException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public AttnDesc[] doTest() {
        try {
            StopWatch sw = new StopWatch();
            sw.start();
            AttentionConfig ac = new AttentionConfig();
            ac.setTargetKey("854a1807-025b-42a8-ba8c-2a39717f1d25");
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd-MMM-yyyy hh:mm");
            Date date = null;
            try {
                date = sdf.parse("07-JUL-2008 10:39");
            }catch(java.text.ParseException p) {
                System.out.println(p.toString());
            }
            List<Attention> attn = getStore().getLastAttention(ac, 35);
            sw.stop();
            int numResults = Math.min(attn.size(), 100);
            AttnDesc[] results = new AttnDesc[numResults + 1];
            results[0] = new AttnDesc(sw.getTime(), attn.size());
            int i = 1;
            for (Attention a : attn) {
                if (i > numResults) {
                    break;
                }
                results[i++] = Factory.attnDesc(a);
            }
            return results;
        } catch (AuraException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(DBServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

}
