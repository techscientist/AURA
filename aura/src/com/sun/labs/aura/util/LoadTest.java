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

package com.sun.labs.aura.util;

import com.sun.labs.aura.datastore.*;
import com.sun.labs.aura.datastore.Item.ItemType;
import com.sun.labs.aura.datastore.util.DataStoreFactory;
import com.sun.labs.minion.util.Getopt;
import com.sun.labs.minion.util.StopWatch;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates load on the datastore in various ways.
 */
public class LoadTest {
    protected DataStore dataStore;
    protected final static String usage = "LoadTest: \n" +
            "   [ singleAttn | bulkAttn -l <lastFM data file> ]";
    
    public LoadTest(DataStore ds) {
        dataStore = ds;
    }
    
    /**
     * Run a test loading a single attention at a time
     */
    public void singleAttn(File lastFMData) throws Exception {
        int printCnt = 0;
        int totalCnt = 0;
        FileReader fr = new FileReader(lastFMData);
        BufferedReader input = new BufferedReader(fr);
        StopWatch sw = new StopWatch();
        dataStore.defineField("foo");
        String line = null;
        while((line = input.readLine()) != null) {
            String[] fields = line.split("<sep>");
            if(fields.length != 4) {
                continue;
            }
            User existing = dataStore.getUser(fields[0]);
            
            Item artist = StoreFactory.newItem(ItemType.ARTIST, fields[2], fields[1]);
            User user = StoreFactory.newUser(fields[0], fields[0]);
            artist.setField("foo", "bar");

            dataStore.putUser(user);
            dataStore.putItem(artist);
            int numAttentions = Integer.valueOf(fields[3]);
            for(int i = 0; i < numAttentions; i++) {
                sw.start();
                dataStore.attend(StoreFactory.newAttention(user, artist, Attention.Type.VIEWED));
                sw.stop();
            }
            totalCnt += numAttentions;
            printCnt += numAttentions;
            if (printCnt > 5000) {
                printCnt = 0;
                long secs = sw.getTime() / 1000;
                System.out.println("Proccessed " + totalCnt + " attentions at "
                        + (totalCnt / secs) + " attentions/sec");
            }
        }
        input.close();
    }
    
    /**
     * Run a test loading attention in bulk
     */
    public void bulkAttn(File lastFMData) throws Exception {
        int printCnt = 0;
        int totalCnt = 0;
        FileReader fr = new FileReader(lastFMData);
        BufferedReader input = new BufferedReader(fr);
        StopWatch sw = new StopWatch();
        dataStore.defineField("foo");
        String line = null;
        while((line = input.readLine()) != null) {
            String[] fields = line.split("<sep>");
            if(fields.length != 4) {
                continue;
            }

            Item artist = StoreFactory.newItem(ItemType.ARTIST, fields[2], fields[1]);
            User user = StoreFactory.newUser(fields[0], fields[0]);
            artist.setField("foo", "bar");

            dataStore.putUser(user);
            dataStore.putItem(artist);
            int numAttentions = Integer.valueOf(fields[3]);
            List<Attention> attns = new ArrayList<Attention>();
            for(int i = 0; i < numAttentions; i++) {
                attns.add(StoreFactory.newAttention(user, artist, Attention.Type.VIEWED));
            }
            sw.start();
            dataStore.attend(attns);
            sw.stop();
            totalCnt += numAttentions;
            printCnt += numAttentions;
            if (printCnt > 5000) {
                printCnt = 0;
                long secs = sw.getTime() / 1000;
                if (secs > 0) {
                    System.out.println("Proccessed " + totalCnt + " attentions at "
                            + (totalCnt / secs) + " attentions/sec");
                }
            }
        }
        input.close();
    }
    
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.out.println(usage);
                return;
            }
            
            DataStore ds = DataStoreFactory.getSimpleDataStore(null);
            LoadTest tester = new LoadTest(ds);
            
            //
            // Figure out the root command arg
            String cmd = args[0];
            String[] tmp = new String[args.length - 1];
            for (int i = 1; i < args.length; i++) {
                tmp[i - 1] = args[i];
            }
            args = tmp;
            //
            // Check for each supported command, optionally processing further
            // arguments
            if (cmd.equals("singleAttn") ||
                    cmd.equals("bulkAttn")) {
                Getopt gopt = new Getopt(args, "l:");
                int c;
                String lastFMFile = "";
                while ((c = gopt.getopt()) != -1) {
                    switch (c) {
                        case 'l':
                            lastFMFile = gopt.optArg;
                            break;
                        default:
                            System.out.println("Illegal argument: " + ((char)c));
                            return;
                    }
                }
                if (lastFMFile.equals("")) {
                    System.out.println("LastFM file not specified, use -l");
                }
                
                File lfm = new File(lastFMFile);
                if (!lfm.exists()) {
                    System.out.println("LastFM file does not exist!");
                    return;
                }
                if (cmd.equals("singleAttn")) {
                    tester.singleAttn(lfm);
                } else {
                    tester.bulkAttn(lfm);
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to run test: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
