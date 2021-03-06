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

package com.sun.labs.aura.grid.util;

import com.sun.labs.aura.datastore.DataStore;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.SimilarityConfig;
import com.sun.labs.aura.grid.ServiceAdapter;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.Scored;
import com.sun.labs.util.props.ConfigComponent;
import com.sun.labs.util.props.ConfigInteger;
import com.sun.labs.util.props.ConfigString;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 */
public class RunFindSimilars extends ServiceAdapter {

    @ConfigComponent(type=com.sun.labs.aura.datastore.DataStore.class)
    public static final String PROP_DATA_STORE = "dataStore";

    private DataStore dataStore;

    @ConfigString(defaultValue="aura-type = artist <and> socialtags <contains> canadian")
    public static final String PROP_QUERY = "query";

    private String query;

    @ConfigInteger(defaultValue=10)
    public static final String PROP_THREADS = "threads";

    private int threads;

    @ConfigInteger(defaultValue=10)
    public static final String PROP_RUNS = "runs";

    private int runs;
    
    @Override
    public String serviceName() {
        return getClass().getName();
    }

    @Override
    public void newProperties(PropertySheet ps) throws PropertyException {
        super.newProperties(ps);
        dataStore = (DataStore) ps.getComponent(PROP_DATA_STORE);
        logger.info("dsHead: " + dataStore);
        query = ps.getString(PROP_QUERY);
        threads = ps.getInt(PROP_THREADS);
        runs = ps.getInt(PROP_RUNS);
    }

    @Override
    public void start() {
        try {
            logger.info("Starting query: "+ query);
            List<Scored<Item>> r = dataStore.query(query, 100, null);
            logger.info("Got results: " + r.size());
            List<String> keys = new ArrayList<String>();
            for (Scored<Item> i : r) {
                keys.add(i.getItem().getKey());
            }

            Thread[] t = new Thread[threads];
            FSRunner[] fs = new FSRunner[threads];
            for(int i = 0; i < fs.length; i++) {
                fs[i] = new FSRunner(keys);
                t[i] = new Thread(fs[i]);
                t[i].setName("Runner-"+i);
                t[i].start();
            }

            for(Thread thread : t) {
                thread.join();
            }

        } catch (AuraException ex) {
            logger.log(Level.SEVERE, "Aura exception", ex);
        } catch (RemoteException ex) {
            logger.log(Level.SEVERE, "Aura exception", ex);
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Throwable?", t);
        }
    }

    @Override
    public void stop() {
    }

    public class FSRunner implements Runnable {

        private List<String> keys;

        SimilarityConfig config;

        public FSRunner(List<String> keys) {
            this.keys = new ArrayList<String>(keys);
            Collections.shuffle(this.keys);
            config = new SimilarityConfig("socialtags");
        }

        @Override
        public void run() {
            String tname = Thread.currentThread().getName();
            for(int i = 0; i < runs; i++) {
                for(String key : keys) {
                    try {
                        logger.info(String.format("%s fs start %s", tname, key));
                        List<Scored<Item>> r = dataStore.findSimilar(key, config);
                        logger.info(String.format("%s fs end %s found %d", tname, key, r.size()));
                    } catch (AuraException ax) {
                        logger.log(Level.SEVERE, "Aura exception during fs for " + key, ax);
                    } catch (RemoteException ex) {
                        logger.log(Level.SEVERE, "Remote exception during fs for " + key, ex);
                    }
                }
            }
        }
    }
}
