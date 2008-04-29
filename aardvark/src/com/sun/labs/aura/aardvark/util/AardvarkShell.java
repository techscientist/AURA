/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.labs.aura.aardvark.util;

/**
 *
 * @author plamere
 */
import com.sun.kt.search.FieldFrequency;
import com.sun.kt.search.WeightedField;
import com.sun.labs.aura.AuraService;
import com.sun.labs.aura.aardvark.Aardvark;
import com.sun.labs.aura.aardvark.BlogEntry;
import com.sun.labs.aura.aardvark.BlogFeed;
import com.sun.labs.aura.aardvark.impl.recommender.TypeFilter;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.datastore.Attention;
import com.sun.labs.aura.datastore.DBIterator;
import com.sun.labs.aura.datastore.DataStore;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.Item.ItemType;
import com.sun.labs.aura.datastore.StoreFactory;
import com.sun.labs.aura.datastore.User;
import com.sun.labs.aura.util.ItemAdapter;
import com.sun.labs.aura.util.Scored;
import com.sun.labs.aura.util.StatService;
import com.sun.labs.aura.util.Tag;
import com.sun.labs.util.command.CommandInterface;
import com.sun.labs.util.command.CommandInterpreter;
import com.sun.labs.util.props.ConfigComponent;
import com.sun.labs.util.props.Configurable;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedOutput;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import ngnova.util.NanoWatch;

/**
 * Manages the set of feed crawling threads
 * @author plamere
 */
public class AardvarkShell implements AuraService, Configurable {

    private DataStore dataStore;
    private CommandInterpreter shell;
    private Aardvark aardvark;
    private StatService statService;
    private Logger logger;
    private int nHits = 10;

    /**
     * Starts crawling all of the feeds
     */
    public void start() {
        shell = new CommandInterpreter();
        shell.setPrompt("aardv% ");

        shell.add("setN",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length < 2) {
                                return getHelp();
                            }
                            nHits = Integer.parseInt(args[1]);
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: setN <n> sets the number of hits to return from things.";
                    }
                });

        shell.add("user",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length != 2) {
                                dumpAllUsers();
                            } else {
                                User user = aardvark.getUser(args[1]);
                                if (user != null) {
                                    dumpUser(user);
                                }
                            }
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: user  name = shows info for a user";
                    }
                });

        shell.add("timeGetItem",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            NanoWatch nw = new NanoWatch();
                            System.out.println("args: " + args.length);
                            for (int i = 1; i < args.length; i++) {
                                nw.start();
                                Item item = dataStore.getItem(args[i]);
                                nw.stop();
                            }
                            System.out.printf("%d gets took: %.4f avg: %.4f/get\n",
                                    args.length - 1,
                                    nw.getTimeMillis(),
                                    nw.getTimeMillis() / (args.length - 1));
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                            ex.printStackTrace();
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: getItem <key> gets an item and prints the data map";
                    }
                });

        shell.add("item",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length == 1) {
                                return getHelp();
                            }

                            Item item = dataStore.getItem(args[1]);
                            dumpItemFull(item);
                            if (item != null) {
                                System.out.printf("%-15s %s\n", "autotags", item.getMap().
                                        get("autotag"));
                            }

                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                            ex.printStackTrace();
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: item <key> gets an item and prints the data map";
                    }
                });

        shell.add("tstattn",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length != 3) {
                                return getHelp();
                            }

                            Item item1 = dataStore.getItem(args[1]);
                            Item item2 = dataStore.getItem(args[2]);
                            if (item1 != null && item2 != null) {
                                dataStore.attend(StoreFactory.newAttention(args[1], args[2], Attention.Type.LINKS_TO));
                            }

                            Item item1A = dataStore.getItem(args[1]);
                            Item item2A = dataStore.getItem(args[2]);
                            dumpItemFull(item1A);
                            dumpItemFull(item2A);
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                            ex.printStackTrace();
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: tstattn <key1 key2> tests adding and getting attention";
                    }
                });

        shell.add("dumpTagFrequencies",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            dumpTagFrequencies(nHits);
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                            ex.printStackTrace();
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: dumpTagFrequencies shows top nHits tag frequencies for entries";
                    }
                });

        shell.add("dumpFeedLinkGraph",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            int count = 500;
                            if (args.length >= 2) {
                                count = Integer.parseInt(args[1]);
                            }
                            String regexp = null;
                            if (args.length >= 3) {
                                regexp = args[2];
                            }
                            dumpFeedLinkGraph(count, regexp);
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                            ex.printStackTrace();
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: dumpFeedLinkGraph [count] -  dumps the feed link graph";
                    }
                });

        shell.add("dumpStories",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length != 1 && args.length != 2) {
                                return getHelp();
                            } else {
                                int count = 500;
                                if (args.length >= 2) {
                                    count = Integer.parseInt(args[1]);
                                }
                                dumpStories(count);
                            }
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                            ex.printStackTrace();
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: dumpStories [count]- dump xml description of stories, suitable for the dashboard similator";
                    }
                });


        shell.add("attnTgt",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length != 2) {
                                return "Usage: attnTgt id";
                            } else {
                                List<Attention> attns = dataStore.getAttentionForTarget(args[1]);
                                for (Attention attn : attns) {
                                    System.out.println(attn);
                                }
                            }
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: tgtattn key - shows target attention data for an item";
                    }
                });
        shell.add("attnSrc",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length != 2) {
                                return "Usage: attnSrc id";
                            } else {
                                List<Attention> attns = dataStore.getAttentionForSource(args[1]);
                                for (Attention attn : attns) {
                                    System.out.println(attn);
                                }
                            }
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: attnSrc key - show source arget attention data for an item";
                    }
                });

        shell.add("addStarredFeed",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length != 3) {
                                getHelp();
                            } else {
                                User user = aardvark.getUser(args[1]);
                                if (user != null) {
                                    String surl = args[2];
                                    aardvark.addUserFeed(user, surl, Attention.Type.STARRED_FEED);
                                } else {
                                    return "Can't find user " + args[1];
                                }
                            }
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: addStarredFeed user  url = adds a starred item feed to a user";
                    }
                });


        shell.add("recommend",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length != 2 && args.length != 3) {
                                getHelp();
                            } else {
                                User user = aardvark.getUser(args[1]);
                                int count = args.length >= 3 ? Integer.parseInt(args[2]) : 20;
                                if (user != null) {
                                    recommend(user, count);
                                }
                            }
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                            ex.printStackTrace();
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: recommend  name = recommendations for a user";
                    }
                });

        shell.add("recommendFeed",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length != 2 && args.length != 3) {
                                getHelp();
                            } else {
                                User user = aardvark.getUser(args[1]);
                                int count = args.length >= 3 ? Integer.parseInt(args[2]) : 20;
                                if (user != null) {
                                    SyndFeed feed = aardvark.getRecommendedFeed(user, count);
                                    if (feed != null) {
                                        feed.setLink("http://tastekeeper.com/feed");
                                        SyndFeedOutput output = new SyndFeedOutput();
                                        //feed.setFeedType("atom_1.0");
                                        feed.setFeedType("rss_2.0");
                                        // feed.setLink();
                                        String feedXML = output.outputString(feed);
                                        System.out.println(feedXML);
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                            ex.printStackTrace();
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: recommend  name = recommendations for a user";
                    }
                });


        shell.add("feed",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) throws Exception {
                        if (args.length != 2) {
                            dumpAllFeeds();
                        } else {
                            String key = args[1];
                            Item item = dataStore.getItem(key);
                            if (item != null && item.getType() == ItemType.FEED) {
                                dumpFeed(item);
                            }
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: feed  id = shows info for a feed";
                    }
                });



        shell.add("feeds",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] arg1) {
                        try {
                            dumpAllFeeds();
                        } catch (Exception e) {
                            System.out.println("Error " + e);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "shows the current feeds";
                    }
                });

        shell.add("entries",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] arg1) {
                        try {
                            dumpLastEntries();
                        } catch (Exception e) {
                            System.out.println("Error " + e);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "dumps the entries added in the last 24 hours";
                    }
                });

        shell.add("entryTitles",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] arg1) {
                        try {
                            dumpEntryTitles(10000);
                        } catch (Exception e) {
                            System.out.println("Error " + e);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "dumps 10,000 entries";
                    }
                });

        shell.add("enrollUser",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        if (args.length != 2) {
                            return "Usage: addUser user-id";
                        }
                        try {
                            String id = args[1];
                            if (aardvark.getUser(id) != null) {
                                return "user " + id + " already exists";
                            } else {
                                User user = aardvark.enrollUser(id);
                                return "User " + user.getKey() + " created.";
                            }
                        } catch (Exception e) {
                            System.out.println("Error " + e);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "Enrolls a new user in the database";
                    }
                });

        shell.add("dbExerciseWrite",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) throws Exception {
                        try {
                            if (args.length == 2) {
                                long timeStamp = System.currentTimeMillis();
                                int count = Integer.parseInt(args[1]);
                                for (int i = 0; i < count; i++) {
                                    String key = "key:" + timeStamp + "-" + i;
                                    Item item = StoreFactory.newItem(Item.ItemType.BLOGENTRY, key, key);
                                    item = dataStore.putItem(item);
                                }
                            } else {
                                getHelp();
                            }
                        } catch (Exception e) {
                            System.out.println("Error " + e);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "dbExercise count - exercise the database by repeated fetching items";
                    }
                });
        shell.add("astats",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length > 2) {
                                return "Usage: astats";
                            }

                            System.out.println(aardvark.getStats());
                        } catch (Exception e) {
                            System.out.println("Error " + e);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "shows the current stats";
                    }
                });
        shell.add("stats",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length > 2) {
                                return "Usage: stats [prefix]";
                            }

                            String prefix = args.length == 2 ? args[1] : "";
                            String[] counters = statService.getCounterNames();
                            Arrays.sort(counters);
                            System.out.printf("%20s %8s %8s %8s\n", "Stat", "counter", "average", "per min");
                            System.out.printf("%20s %8s %8s %8s\n", "----", "-------", "-------", "-------");
                            for (String counter : counters) {
                                if (counter.startsWith(prefix)) {
                                    long count = statService.get(counter);
                                    double avg = statService.getAverage(counter);
                                    double avgPerMin = statService.getAveragePerMinute(counter);
                                    System.out.printf("%20s %8d %8.3f %8.3f\n", counter, count, avg, avgPerMin);
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Error " + e);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "shows the current stats";
                    }
                });
        shell.add("query",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        String query = stuff(args, 1);
                        List<Scored<Item>> items = dataStore.query(query, nHits, null);
                        for (Scored<Item> item : items) {
                            System.out.printf("%.3f ", item.getScore());
                            dumpItem(item.getItem());
                        }

                        return "";
                    }

                    public String getHelp() {
                        return "Runs a query";
                    }
                });
        shell.add("qe",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        String query = stuff(args, 1);
                        List<Scored<Item>> items = dataStore.query(query, nHits, new TypeFilter(Item.ItemType.BLOGENTRY));
                        for (Scored<Item> item : items) {
                            System.out.printf("%.3f ", item.getScore());
                            dumpItem(item.getItem());
                        }

                        return "";
                    }

                    public String getHelp() {
                        return "Runs a query";
                    }
                });
        shell.add("gat",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        String autotag = stuff(args, 1).trim();
                        List<Scored<Item>> items = dataStore.getAutotagged(autotag, nHits);
                        for (Scored<Item> item : items) {
                            System.out.printf("%.3f ", item.getScore());
                            dumpItem(item.getItem());
                        }

                        return "";
                    }

                    public String getHelp() {
                        return "get top auttagged items:   gat <autotag>";
                    }
                });

        shell.add("gtt",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        String autotag = stuff(args, 1).trim();
                        List<Scored<String>> terms = dataStore.getTopAutotagTerms(autotag, nHits);
                        for (Scored<String> term : terms) {
                            System.out.println(term);
                        }

                        return "";
                    }

                    public String getHelp() {
                        return "get top autotag terms: gtt <autotag>";
                    }
                });

        shell.add("tsim",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        String autotag = stuff(args, 1).trim();
                        List<Scored<String>> autotags =
                                dataStore.findSimilarAutotags(autotag, nHits);
                        for (Scored<String> tag : autotags) {
                            System.out.println(tag);
                        }

                        return "";
                    }

                    public String getHelp() {
                        return "Get autotags most similar to the given tag: tsim <autotag>";
                    }
                });

        shell.add("etsim",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        if (args.length < 3) {
                            return getHelp();
                        }
                        List<Scored<String>> terms =
                                dataStore.explainSimilarAutotags(args[1], args[2], nHits);
                        for (Scored<String> term : terms) {
                            System.out.println(term);
                        }

                        return "";
                    }

                    public String getHelp() {
                        return "Explain autotag similarity: etsim <autotag> <autotag>";
                    }
                });

        shell.add("fs",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        String key = args[1];
                        List<Scored<Item>> items = dataStore.findSimilar(key, nHits, null);
                        for (Scored<Item> item : items) {
                            System.out.printf("%.3f ", item.getScore());
                            dumpItem(item.getItem());
                        }

                        return "";
                    }

                    public String getHelp() {
                        return "Find similar";
                    }
                });
        shell.add("ffs",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        String field = args[1];
                        String key = args[2];
                        List<Scored<Item>> items = dataStore.findSimilar(key, field, nHits, null);
                        for (Scored<Item> item : items) {
                            System.out.printf("%.3f ", item.getScore());
                            dumpItem(item.getItem());
                        }

                        return "";
                    }

                    public String getHelp() {
                        return "Find similar with a field";
                    }
                });
        shell.add("efs",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        if (args.length < 3) {
                            return getHelp();
                        }
                        String key1 = args[1];
                        String key2 = args[2];
                        List<Scored<String>> expn = dataStore.explainSimilarity(key1, key2, nHits);
                        for (Scored<String> term : expn) {
                            System.out.print(term + " ");
                        }
                        System.out.println("");
                        return "";
                    }

                    public String getHelp() {
                        return "Explain Find similar: efs <key1> <key2>";
                    }
                });
        shell.add("effs",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        if (args.length < 4) {
                            return getHelp();
                        }
                        String field = args[1];
                        String key1 = args[2];
                        String key2 = args[3];
                        List<Scored<String>> expn = dataStore.explainSimilarity(key1, key2, field, nHits);
                        for (Scored<String> term : expn) {
                            System.out.print(term + " ");
                        }
                        System.out.println("");
                        return "";
                    }

                    public String getHelp() {
                        return "Explain Fielded Find similar: efs <field> <key1> <key2>";
                    }
                });


        shell.add("fwfs",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        WeightedField[] fields = {
                            new WeightedField("content", 1),
                        //    new WeightedField("autotag", 1),
                        //   new WeightedField("aura-name", 1),
                        };
                        String key = args[1];

                        System.out.println("Using fields:");
                        for (WeightedField wf : fields) {
                            System.out.printf("   %s: %f\n", wf.getFieldName(), wf.getWeight());
                        }
                        List<Scored<Item>> items = dataStore.findSimilar(key, fields, nHits, null);
                        for (Scored<Item> item : items) {
                            System.out.printf("%.3f ", item.getScore());
                            dumpItem(item.getItem());
                        }

                        return "";
                    }

                    public String getHelp() {
                        return "Find similar with weighted fields";
                    }
                });

        shell.add("topTerms",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        String key = args[1];
                        String field = args.length > 2 ? args[2] : "content";
                        List<Scored<String>> terms = dataStore.getTopTerms(key,
                                field, nHits);
                        for (Scored<String> term : terms) {
                            System.out.printf("%.3f %s\n", term.getScore(), term.getItem());
                        }

                        return "";
                    }

                    public String getHelp() {
                        return "<key> [<field>] gets the top terms from the given field (default: content) in the given document.";
                    }
                });

        shell.add("explain",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args)
                            throws Exception {
                        String autotag = args[1];
                        String key = args[2];
                        List<Scored<String>> terms = dataStore.getExplanation(key, autotag, nHits);
                        for (Scored<String> term : terms) {
                            System.out.printf("%.3f %s\n", term.getScore(), term.getItem());
                        }

                        return "";
                    }

                    public String getHelp() {
                        return "<autotag> <key> explain the classification of key into autotag";
                    }
                });

        Thread t = new Thread() {

            public void run() {
                shell.run();
                shell = null;
            }
        };
        t.start();
    }

    private void dumpAllUsers() throws AuraException, RemoteException {
        for (Item item : dataStore.getAll(ItemType.USER)) {
            dumpUser((User) item);
        }
    }

    private void dumpUser(User user) throws AuraException, RemoteException {
        dumpItem(user);
    }

    private void recommend(User user, int count) throws AuraException, RemoteException {
        SyndFeed feed = aardvark.getRecommendedFeed(user, count);
        System.out.println("Feed " + feed.getTitle());
        for (Object syndEntryObject : feed.getEntries()) {
            SyndEntry syndEntry = (SyndEntry) syndEntryObject;
            String title = syndEntry.getTitle();
            String link = syndEntry.getLink();
            Date date = syndEntry.getPublishedDate();
            String sdate = "";
            if (date != null) {
                sdate = "(" + date.toString() + ")";
            }
            System.out.printf("  %s from %s %s\n", title, link, sdate);
        }

    }

    private void dumpAllFeeds() throws AuraException, RemoteException {
        List<Item> feedItems = dataStore.getAll(ItemType.FEED);
        List<Scored<Item>> scoredItems = new ArrayList();

        for (Item feed : feedItems) {
            scoredItems.add(new Scored<Item>(feed, dataStore.getAttentionForTarget(feed.getKey()).size()));
        }

        long numFeeds = 0;

        Collections.sort(scoredItems);

        for (Scored<Item> scoredItem : scoredItems) {
            dumpScoredItem(scoredItem);
            numFeeds++;
        }
        System.out.println("Dumped " + numFeeds + " feeds");
    }

    private void dumpFeedLinkGraph(int topN, String regexp) throws AuraException, RemoteException, IOException {
        double MIN_WIDTH = .5;
        double MIN_HEIGHT = .3;
        double RANGE_WIDTH = 3;
        double RANGE_HEIGHT = 2;
        PrintWriter out = new PrintWriter("feedGraph.dot");
        out.println("digraph Feeds {");
        List<Item> feedItems = dataStore.getAll(ItemType.FEED);

        List<Scored<Item>> scoredItems = new ArrayList();
        for (Item feed : feedItems) {
            if (regexp == null || feed.getKey().matches(regexp)) {
                scoredItems.add(new Scored<Item>(feed, dataStore.getAttentionForTarget(feed.getKey()).size()));
            }
        }

        if (scoredItems.size() == 0) {
            return;
        }

        Collections.sort(scoredItems);
        Collections.reverse(scoredItems);
        if (scoredItems.size() > topN) {
            scoredItems = scoredItems.subList(0, topN);
        }

        Set<String> validKeys = new HashSet();
        for (Scored<Item> item : scoredItems) {
            validKeys.add(item.getItem().getKey());
        }

        // recalculate the scores with only the valid keys
        List<Scored<Item>> prunedScoredItems = new ArrayList();
        for (Scored<Item> item : scoredItems) {
            Item tgt = item.getItem();
            int actualInputLinks = 0;
            for (Attention attn : dataStore.getAttentionForTarget(tgt.getKey())) {
                if (attn.getType() == Attention.Type.LINKS_TO) {
                    Item src = dataStore.getItem(attn.getSourceKey());
                    if (src != null && validKeys.contains(src.getKey())) {
                        actualInputLinks++;
                    }
                }
            }
            if (actualInputLinks > 0) {
                prunedScoredItems.add(new Scored(tgt, actualInputLinks));
            }
        }

        Collections.sort(prunedScoredItems);
        Collections.reverse(prunedScoredItems);
        double maxScore = prunedScoredItems.get(0).getScore();
        double minScore = prunedScoredItems.get(prunedScoredItems.size() - 1).getScore();
        double rangeScore = maxScore - minScore;

        for (Scored<Item> item : prunedScoredItems) {
            Item tgt = item.getItem();
            for (Attention attn : dataStore.getAttentionForTarget(tgt.getKey())) {
                if (attn.getType() == Attention.Type.LINKS_TO) {
                    Item src = dataStore.getItem(attn.getSourceKey());
                    if (src != null && validKeys.contains(src.getKey())) {
                        out.println("   " + formatNameForGraphviz(src.getName()) + " -> " + formatNameForGraphviz(tgt.getName()));
                    }
                }
            }
            double width = MIN_WIDTH + ((item.getScore() - minScore) / rangeScore) * RANGE_WIDTH;
            double height = MIN_HEIGHT + ((item.getScore() - minScore) / rangeScore) * RANGE_HEIGHT;
            out.printf("%s [width=%f height=%f]\n",
                    formatNameForGraphviz(tgt.getName()), width, height);
        }
        out.println("}");
        out.close();
    }

    private String formatNameForGraphviz(String name) {
        name = name.replaceAll("\"", "");
        name = name.replaceAll("\\s+", " ");
        return "\"" + name + "\"";
    }

    private void dumpLastEntries() throws AuraException, RemoteException {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, -2);
        Date date = cal.getTime();
        DBIterator<Item> iter = dataStore.getItemsAddedSince(ItemType.BLOGENTRY, date);
        try {
            while (iter.hasNext()) {
                Item item = iter.next();
                BlogEntry entry = new BlogEntry(item);
                System.out.println(entry.getKey());
                System.out.println(" " + entry.getName());
            }

        } finally {
            iter.close();
        }
    }

    private void dumpItem(Item item) throws AuraException, RemoteException {
        if (item == null) {
            System.out.println("null");
        } else {
            System.out.printf(" %d %s\n", dataStore.getAttentionForTarget(item.getKey()).
                    size(), item.getKey());
        }
    }
    private void dumpItemFull(Item item) throws AuraException, RemoteException {
        if (item == null) {
            System.out.println("null");
        } else {
            System.out.println(ItemAdapter.toString(item));
            dumpAttentionData("src", dataStore.getAttentionForSource(item.getKey()));
            dumpAttentionData("tgt", dataStore.getAttentionForTarget(item.getKey()));
        }
    }

    private void dumpScoredItem(Scored<Item> scoredItem) throws AuraException, RemoteException {
        if (scoredItem == null) {
            System.out.println("null");
        } else {
            System.out.printf(" %.0f %s\n", scoredItem.getScore(),
                    scoredItem.getItem().getKey());
        }
    }

    private void dumpFeed(Item feedItem) throws AuraException, RemoteException {
        dumpItem(feedItem);
        BlogFeed feed = new BlogFeed(feedItem);
        System.out.println("   Pulls  : " + feed.getNumPulls());
        System.out.println("   Errors : " + feed.getNumErrors());
        System.out.println("   Authority: " + feed.getAuthority());
        for (Attention attn : dataStore.getAttentionForTarget(feedItem.getKey())) {
            System.out.println("   " + attn);
        }
    }

    private void dumpAttentionData(String msg, List<Attention> attentionData) throws AuraException, RemoteException {
        System.out.println("Attention " + msg);
        for (Attention attention : attentionData) {
            Item source = dataStore.getItem(attention.getSourceKey());
            Item target = dataStore.getItem(attention.getTargetKey());
            String type = attention.getType().toString();

            System.out.printf("   %s -- %s -- %s\n", fmtItem(source), type, fmtItem(target));
        }
    }
    
    private String fmtItem(Item item) {
        if (item == null) {
            return "(null)";
        } else {
            return item.getKey() + "(" + item.getName() + ")";
        }
    }

    private void dumpEntryTitles(int count) {
        try {
            DBIterator<Item> iter = dataStore.getItemsAddedSince(ItemType.BLOGENTRY, new Date(0));

            try {
                while (count-- > 0 && iter.hasNext()) {
                    Item item = iter.next();
                    BlogEntry entry = new BlogEntry(item);
                    System.out.println(entry.getName());
                }
            } finally {
                iter.close();
            }
        } catch (AuraException ex) {
            logger.severe("dumpTagFrequencies " + ex);
        } catch (RemoteException ex) {
            logger.severe("dumpTagFrequencies " + ex);
        }
    }

    private void dumpStories(int count) {
        try {
            DBIterator<Item> iter = dataStore.getItemsAddedSince(ItemType.BLOGENTRY, new Date(0));

            try {
                System.out.println("<stories>");
                while (count-- > 0 && iter.hasNext()) {
                    Item item = iter.next();
                    BlogEntry entry = new BlogEntry(item);
                    dumpStory(entry);
                }
                System.out.println("</stories>");
            } finally {
                iter.close();
            }
        } catch (AuraException ex) {
            logger.severe("dumpStories " + ex);
        } catch (RemoteException ex) {
            logger.severe("dumpStories " + ex);
        }
    }

    void dumpStory(BlogEntry entry) throws AuraException, RemoteException {
        Item ifeed = dataStore.getItem(entry.getFeedKey());
        BlogFeed feed = new BlogFeed(ifeed);
        System.out.println("    <story score =\"1.0\">");

        dumpTag("        ", "source", feed.getName());
        dumpTag("        ", "imageUrl", feed.getImage());
        dumpTag("        ", "url", entry.getKey());
        dumpTag("        ", "title", entry.getTitle());
        if (entry.getContent() != null) {
            System.out.println("        <description>" + excerpt(filterHTML(entry.getContent()), 100) + "</description>");
        }

        List<Tag> tags = entry.getTags();
        if (tags.size() == 0) {
            tags = feed.getTags();
        }
        for (Tag tag : tags) {
            System.out.println("        <class score=\"1.0\">" + filterTag(tag.getName()) + "</class>");
        }
        System.out.println("    </story>");
    }

    private void dumpTag(String indent, String tag, String value) {
        if (value != null) {
            value = filterTag(value);
            System.out.println(indent + "<" + tag + ">" + value + "</" + tag + ">");
        }
    }

    private String filterTag(String s) {
        s = s.replaceAll("[^\\p{ASCII}]", "");
        s = s.replaceAll("\\&", "&amp;");
        s = s.replaceAll("\\<", "&lt;");
        s = s.replaceAll("\\>", "&gt;");

        return s;
    }

    private String filterHTML(String s) {
        s = detag(s);
        s = deentity(s);
        s = s.replaceAll("[^\\p{ASCII}]", "");
        s = s.replaceAll("\\s+", " ");
        s = s.replaceAll("[\\<\\>\\&]", " ");
        return s;
    }

    private String detag(String s) {
        return s.replaceAll("\\<.*?\\>", "");
    }

    private String deentity(String s) {
        return s.replaceAll("\\&[a-zA-Z]+;", " ");
    }

    private String excerpt(String s, int maxWords) {
        StringBuilder sb = new StringBuilder();
        String[] words = s.split("\\s+");
        for (int i = 0; i < maxWords && i < words.length; i++) {
            sb.append(words[i] + " ");
        }

        if (maxWords < words.length) {
            sb.append("...");
        }
        return sb.toString().trim();
    }

    private void dumpTagFrequencies(int n) {

        try {
            List<FieldFrequency> tagFreqs = dataStore.getTopValues("tag", n,
                    true);
            for (FieldFrequency ff : tagFreqs) {
                System.out.printf("%d %s\n", ff.getFreq(), ff.getVal().toString().trim());
            }
        } catch (AuraException ex) {
            logger.severe("dumpTagFrequencies " + ex);
        } catch (RemoteException ex) {
            logger.severe("dumpTagFrequencies " + ex);
        }
    }

    private String stuff(String[] args, int p) {
        StringBuilder sb = new StringBuilder();
        for (int i = p; i < args.length; i++) {
            sb.append(args[i]);
            sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * Stops crawling the feeds
     */
    public void stop() {
        if (shell != null) {
            shell.close();
        }
    }

    /**
     * Reconfigures this component
     * @param ps the property sheet
     * @throws com.sun.labs.util.props.PropertyException
     */
    public void newProperties(PropertySheet ps) throws PropertyException {
        dataStore = (DataStore) ps.getComponent(PROP_DATA_STORE);
        aardvark = (Aardvark) ps.getComponent(PROP_AARDVARK);
        statService = (StatService) ps.getComponent(PROP_STAT_SERVICE);
        logger = ps.getLogger();
    }
    /**
     * the configurable property for the itemstore used by this manager
     */
    @ConfigComponent(type = DataStore.class)
    public final static String PROP_DATA_STORE = "dataStore";
    @ConfigComponent(type = Aardvark.class)
    public final static String PROP_AARDVARK = "aardvark";
    @ConfigComponent(type = StatService.class)
    public final static String PROP_STAT_SERVICE = "statService";
}

class TagAccumulator {

    private Map<String, Tag> tags = new HashMap<String, Tag>();

    void add(String stag) {
        add(stag, 1);
    }

    void add(String stag, int count) {
        stag = stag.toLowerCase();
        Tag tag = tags.get(stag);
        if (tag == null) {
            tag = new Tag(stag, count);
            tags.put(tag.getName(), tag);
        } else {
            tag.accum(count);
        }
    }

    void add(Tag tag) {
        add(tag.getName(), tag.getCount());
    }

    void dump() {
        List<Tag> tagList = new ArrayList<Tag>(tags.values());
        Collections.sort(tagList);
        Collections.reverse(tagList);
        int which = 0;
        for (Tag tag : tagList) {
            System.out.printf(" %d %d %s\n", ++which, tag.getCount(), tag.getName());
        }
    }
}