/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.labs.aura.aardvark.util;

/**
 *
 * @author plamere
 */
import com.sun.labs.aura.AuraService;
import com.sun.labs.aura.aardvark.Aardvark;
import com.sun.labs.aura.aardvark.BlogEntry;
import com.sun.labs.aura.aardvark.BlogFeed;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.datastore.Attention;
import com.sun.labs.aura.datastore.DBIterator;
import com.sun.labs.aura.datastore.DataStore;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.datastore.Item.ItemType;
import com.sun.labs.aura.datastore.StoreFactory;
import com.sun.labs.aura.datastore.User;
import com.sun.labs.aura.util.StatService;
import com.sun.labs.util.command.CommandInterface;
import com.sun.labs.util.command.CommandInterpreter;
import com.sun.labs.util.props.ConfigComponent;
import com.sun.labs.util.props.Configurable;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

/**
 * Manages the set of feed crawling threads
 * @author plamere
 */
public class AardvarkShell implements AuraService, Configurable {

    private DataStore dataStore;
    private CommandInterpreter shell;
    private Aardvark aardvark;
    private StatService statService;

    /**
     * Starts crawling all of the feeds
     */
    public void start() {
        shell = new CommandInterpreter();
        shell.setPrompt("aardv% ");

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

        shell.add("attn",
                new CommandInterface() {

                    public String execute(CommandInterpreter ci, String[] args) {
                        try {
                            if (args.length != 2) {
                                return "Usage: attn user";
                            } else {
                                User user = aardvark.getUser(args[1]);
                                SortedSet<Attention> attns = aardvark.getLastAttentionData(user, null, 100);
                                for (Attention attn : attns) {
                                    System.out.printf("%8s %s at %s\n", attn.getType(), attn.getTargetKey(), new Date(attn.getTimeStamp()));
                                }
                            }
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
                        }
                        return "";
                    }

                    public String getHelp() {
                        return "usage: attn user - show attention data for a user";
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
                            if (args.length != 2) {
                                getHelp();
                            } else {
                                User user = aardvark.getUser(args[1]);
                                if (user != null) {
                                    recommend(user);
                                }
                            }
                        } catch (Exception ex) {
                            System.out.println("Error " + ex);
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
                            if (item != null && item instanceof BlogFeed) {
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

    private void recommend(User user) throws AuraException, RemoteException {
        SyndFeed feed = aardvark.getRecommendedFeed(user);
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
        Set<Item> feedItems = dataStore.getAll(ItemType.FEED);
        long numFeeds = 0;
        for (Item feedItem : feedItems) {
            dumpItem(feedItem);
            numFeeds++;
        }
        System.out.println("Dumped " + numFeeds + " feeds");
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
                System.out.println("Entry " + entry.getName());
                System.out.println("   " + entry.getSyndEntry().getPublishedDate());
            }

        } finally {
            iter.close();
        }
    }

    private void dumpItem(Item item) throws AuraException, RemoteException {
        System.out.printf(" %d %s\n", dataStore.getAttentionForTarget(item.getKey()).size(), item.getKey());
    }

    private void dumpFeed(Item feedItem) throws AuraException, RemoteException {
        dumpItem(feedItem);
        BlogFeed feed = new BlogFeed(feedItem);
        System.out.println("   Pulls  : " + feed.getNumPulls());
        System.out.println("   Errors : " + feed.getNumErrors());
    }

    private void dumpAttentionData(List<Attention> attentionData) throws AuraException, RemoteException {
        for (Attention attention : attentionData) {
            Item source = dataStore.getItem(attention.getSourceKey());
            Item target = dataStore.getItem(attention.getTargetKey());
            String type = attention.getType().toString();
            System.out.printf("   %s(%s) -- %s -- %s(%s)\n", source.getKey(), source.getName(),
                    type, target.getKey(), target.getName());
        }

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
