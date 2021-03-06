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

package com.sun.labs.aura.util.classifiers;

import com.sun.labs.minion.Log;
import com.sun.labs.minion.SearchEngine;
import com.sun.labs.minion.SearchEngineFactory;
import com.sun.labs.minion.classification.ClassifierModel;
import com.sun.labs.minion.engine.SearchEngineImpl;
import com.sun.labs.minion.indexer.partition.DiskPartition;
import com.sun.labs.minion.indexer.partition.InvFileDiskPartition;
import com.sun.labs.minion.util.Getopt;
import com.sun.labs.util.SimpleLabsLogFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * Evaluates the performance of a set of classifiers on a set of test data.
 *
 * @author Stephen Green <stephen.green@sun.com>
 */
public class EvaluateClassifiers {

    /**
     * Creates a EvaluateClassifiers
     */
    public EvaluateClassifiers() {
    }

    protected static String logTag = "EC";

    public static void main(String[] args) throws Exception {

        String flags = "c:d:e:f:n:o:w";
        Getopt gopt = new Getopt(args, flags);

        //
        // Use the labs format logging.
        final Logger logger = Logger.getLogger("");
        for(Handler h : logger.getHandlers()) {
            h.setFormatter(new SimpleLabsLogFormatter());
        }

        Log.setLogger(logger);
        Log.setLevel(3);


        String logTag = "EVAL";

        //
        // Handle the options.
        int c;
        List<String> classes = new ArrayList<String>();
        List<String> ignore = new ArrayList<String>();
        String classDir = null;
        String testDir = null;
        String fieldName = null;
        int top = -1;
        boolean wikiOutput = false;
        while((c = gopt.getopt()) != -1) {
            switch(c) {
                case 'c':
                    classes.add(gopt.optArg);
                    break;
                case 'd':
                    classDir = gopt.optArg;
                    break;
                case 'e':
                    testDir = gopt.optArg;
                    break;
                case 'f':
                    fieldName = gopt.optArg;
                    break;
                case 'n':
                    ignore.add(gopt.optArg);
                    break;
                case 'o':
                    top = Integer.parseInt(gopt.optArg);
                    break;
                case 'w':
                    wikiOutput = true;
                    break;
            }
        }

        if(classDir == null || testDir == null || fieldName == null) {
            usage();
            return;
        }

        SearchEngine classEngine =
                SearchEngineFactory.getSearchEngine(classDir,
                "aardvark_search_engine");
        
        SearchEngine testEngine =
                SearchEngineFactory.getSearchEngine(testDir,
                "aardvark_search_engine");

        //
        // If there are no specific classes to build, we'll build them all.
        if(classes.size() == 0) {
            classes = Util.getTopClasses(classEngine, fieldName, top);
        }

        classes.removeAll(ignore);

        //
        // Get the number of topics and test documents.
        int nTopics = classes.size();
        int nTestDocs = testEngine.getNDocs();

        logger.info(nTopics + " topics, " + nTestDocs + " test docs");

        //
        // We're going to build a contingency table per class that we're
        // evaluating.
        ContingencyTable tables[] = new ContingencyTable[nTopics];
        int p = 0;

        Set<String> evaled = new HashSet<String>();

        //
        // We want to run the partitions directly.  It's an imperfect world,
        // eh?
        for(String className : classes) {
            
            className = className.toLowerCase();

            tables[p] = new ContingencyTable();

            //
            // Get the number of training/testing documents in this topic.
            try {
            tables[p].train = classEngine.search(
                    String.format("%s = \"%s\"", fieldName, className),
                    "-score").size();
            tables[p].test = testEngine.search(
                    String.format("%s = \"%s\"", fieldName, className),
                    "-score").size();
            } catch (Exception e) {
                logger.info("Error for class: " + className);
                continue;
            }

            ClassifierModel cm =
                    ((SearchEngineImpl) classEngine).getClassifierManager().
                    getClassifier(className);
            
            logger.info(className + " " + 
                    tables[p].train + " " +
                    tables[p].test);

            if(cm == null) {
                logger.info("No classifier for " + className);
                continue;
            }

            int check = 0;
            for(DiskPartition dp : ((SearchEngineImpl) testEngine).getPM().
                    getActivePartitions()) {
                float[] f = cm.classify(dp);
                for(int i =1; i < f.length; i++) {
                    if(dp.isDeleted(i)) {
                        continue;
                    }
                    List<String> vals = new ArrayList<String>();
                    for(String v : (List<String>) ((InvFileDiskPartition) dp).getSavedFieldData(fieldName, i)) {
                        vals.add(v.toLowerCase());
                    }
                    boolean inClass = vals.contains(className);
                    if(inClass) {
                        check++;
                    }
                    if(f[i] > 0) {
                        if(inClass) {
                            tables[p].a++;
                        } else {
                            tables[p].b++;
                        }
                    } else if(f[i] <= 0) {
                        if(inClass) {
                            tables[p].c++;
                        }
                    }
                }
            }

            evaled.add(className);

            //
            // Remember the name of the topic.
            tables[p].name = className;
            p++;
        }

        logger.info("Evaluated " + p + " classes\n");
        com.sun.labs.minion.util.Util.sort(tables, 0, p, new Comparator<ContingencyTable>() {
            public int compare(ContingencyTable o1, ContingencyTable o2) {
                return (o1.test + o1.train) - (o2.test + o2.train);
            }
        });

        //
        // We may only want to print the top n...
        printTables(tables, p, top, wikiOutput);

        classEngine.close();
        testEngine.close();
    }

    public static void printTables(
            ContingencyTable[] tables,
            int p, int top, boolean wikiOutput) {

        ContingencyTable micro = new ContingencyTable();

        int nTrain = 0;
        int nTest = 0;
        float tr = 0;
        float tp = 0;
        if(wikiOutput) {
            System.out.println(
                    "| *Name* | *Rank* | *nTrain* | *nTest* | *a* | *b* | *a+b* | *c* | *Recall* | *Precision* | *F1* |");
        } else {
            System.out.format(
                    "%-35s%7s%7s%7s%7s%7s%7s%7s%10s%10s%10s\n",
                    "Name", "Rank", "nTrain", "nTest", "a", "b", "a+b", "c",
                    "Recall", "Precision", "F1");
        }

        int stop = 0;
        if(top > 0) {
            stop = p - top;
        }
        int n = 0;
        if(stop < 0) {
            stop = 0;
        }

        for(int j = p - 1; j >= stop; j--) {

            //
            // Collect the data for micro averaging.
            micro.add(tables[j]);

            float recall = tables[j].recall();
            if(!Float.isNaN(recall)) {
                tr += recall;
            }
            float precision = tables[j].precision();
            if(!Float.isNaN(precision)) {
                tp += precision;
            }

            float f1 = tables[j].f1();
            
            nTrain += tables[j].train;
            nTest += tables[j].test;

            System.out.format(
                    wikiOutput ? "|%-35s|%7d|%7d|%7d|%7d|%7d|%7d|%7d|%10.1f|%10.1f|%10.1f|\n"
                    : "%-35s%7d%7d%7d%7d%7d%7d%7d%10.1f%10.1f%10.1f\n",
                    tables[j].name,
                    n + 1,
                    tables[j].train,
                    tables[j].test,
                    tables[j].a,
                    tables[j].b,
                    tables[j].a + tables[j].b,
                    tables[j].c,
                    Float.isNaN(recall) ? 0 : recall * 100,
                    Float.isNaN(precision) ? 0 : precision * 100,
                    Float.isNaN(f1) ? 0 : f1 * 100);

            n++;
        }

        System.out.format(
                wikiOutput ? "|%-35s|%7d|%7d|%7d|%7d|%7d|%7d|%7d|%10.1f|%10.1f|%10.1f|\n"
                : "%-35s%7d%7d%7d%7d%7d%7d%7d%10.1f%10.1f%10.1f\n",
                "Micro Average",
                n + 1,
                nTrain,
                nTest,
                micro.a,
                micro.b,
                0,
                micro.c,
                micro.recall() * 100,
                micro.precision() * 100,
                micro.f1() * 100);

        tr /= n;
        tp /= n;
        System.out.println();
        if(wikiOutput) {
            System.out.format("%-40s%10.1f\n", "   * Macro Average Recall: ",
                    tr * 100);
            System.out.format("%-40s%10.1f\n", "   * Macro Average Precision: ",
                    tp * 100);

        } else {
            System.out.format("%-40s%10.1f\n", "Macro Average Recall: ", tr *
                    100);
            System.out.format("%-40s%10.1f\n", "Macro Average Precision: ", tp *
                    100);
        }
    }

    private static void usage() {
        System.err.println("Usage:  com.sun.labs.rsi.EvaluateClassifiers -d <indexDir> " +
                "-f <field Name> [-c class]...");
    }
}
