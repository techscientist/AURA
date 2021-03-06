/*
 * Copyright 2008-2009 Sun Microsystems, Inc. All Rights Reserved.
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

package com.sun.labs.aura.music.admin.server;

import com.sun.labs.aura.datastore.Attention;
import com.sun.labs.aura.datastore.Item.ItemType;
import com.sun.labs.aura.music.Artist;
import com.sun.labs.aura.music.ArtistTag;
import com.sun.labs.aura.music.Listener;
import com.sun.labs.aura.music.MusicDatabase;
import com.sun.labs.aura.music.admin.client.WorkbenchResult;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.Scored;
import java.rmi.RemoteException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 *
 * @author plamere
 */
public class MDBHelper {

    static List<String> artistIDs;
    static List<String> listenerIDs;
    static List<String> artistTagIDs;
    static Random rng = new Random();
    static final Object lock = new Object();
    // some test helper methods
    String selectRandomArtistKey(MusicDatabase mdb) throws AuraException, RemoteException {
        return getArtistIDs(mdb).get(rng.nextInt(artistIDs.size()));
    }

    Artist selectRandomArtist(MusicDatabase mdb) throws AuraException, RemoteException {
        return mdb.artistLookup(selectRandomArtistKey(mdb));
    }

    String selectRandomArtistTagKey(MusicDatabase mdb) throws AuraException, RemoteException {
        return getArtistTagIDs(mdb).get(rng.nextInt(artistTagIDs.size()));
    }

    ArtistTag selectRandomArtistTag(MusicDatabase mdb) throws AuraException, RemoteException {
        return mdb.artistTagLookup(selectRandomArtistTagKey(mdb));
    }

    String selectRandomListenerKey(MusicDatabase mdb) throws AuraException, RemoteException {
        return getListenerIDs(mdb).get(rng.nextInt(listenerIDs.size()));
    }

    Listener selectRandomListener(MusicDatabase mdb) throws AuraException, RemoteException {
        return mdb.getListener(selectRandomListenerKey(mdb));
    }
    

    void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
        }
    }
    
    void sortByTimeAdded(List<Attention> attns) {
        Collections.sort(attns, new Comparator<Attention>() {

            @Override
            public int compare(Attention o1, Attention o2) {
                if (o1.getTimeStamp() > o2.getTimeStamp()) {
                    return 1;
                } else if (o1.getTimeStamp() < o2.getTimeStamp()) {
                    return -1;
                } else {
                    return 0;
                }
            }
        });
    }


    Artist lookupByNameOrKey(MusicDatabase mdb, String nameOrKey) throws AuraException, RemoteException {
        if (isKey(nameOrKey)) {
            return mdb.artistLookup(nameOrKey);
        } else {
            List<Scored<Artist>> results = mdb.artistSearch(nameOrKey, 1);
            if (results.size() != 1) {
                throw new AuraException("Can't find artist " + nameOrKey);
            }
            return results.get(0).getItem();
        }
    }

    ArtistTag lookupArtistTag(MusicDatabase mdb, String name) throws AuraException, RemoteException {
        List<Scored<ArtistTag>> results = mdb.artistTagSearch(name, 1);
        if (results.size() != 1) {
            throw new AuraException("Can't find artist tag" + name);
        }
        return results.get(0).getItem();
    }

    boolean isKey(String nameOrKey) {
        String[] fields = nameOrKey.split("-");
        return fields.length == 5 && fields[0].length() == 8 && fields[4].length() == 12;
    }

    List<String> getArtistIDs(MusicDatabase mdb) throws AuraException, RemoteException {
        synchronized (lock) {
            if (artistIDs == null) {
                artistIDs = mdb.getAllItemKeys(ItemType.ARTIST);
            }
        }
        return artistIDs;
    }

    List<String> getListenerIDs(MusicDatabase mdb) throws AuraException, RemoteException {
        synchronized (lock) {
            if (listenerIDs == null) {
                listenerIDs = mdb.getAllItemKeys(ItemType.USER);
            }
        }
        return listenerIDs;
    }

    List<String> getArtistTagIDs(MusicDatabase mdb) throws AuraException, RemoteException {
        synchronized (lock) {
            if (artistTagIDs == null) {
                artistTagIDs = mdb.getAllItemKeys(ItemType.ARTIST_TAG);
            }
        }
        return artistTagIDs;
    }

    boolean hasTag(List<Scored<ArtistTag>> tags, String tag) {
        for (Scored<ArtistTag> at : tags) {
            if (at.getItem().getName().equalsIgnoreCase(tag)) {
                return true;
            }
        }
        return false;
    }

    boolean hasArtist(List<Scored<Artist>> artists, String artist) {
        for (Scored<Artist> a : artists) {
            if (a.getItem().getName().equalsIgnoreCase(artist)) {
                return true;
            }
        }
        return false;
    }

    void dump(MusicDatabase mdb, WorkbenchResult result, List<Scored<Artist>> sartists) throws AuraException, RemoteException {
        result.output(String.format("%5s %5s %s", "Score", "Pop", "Name"));
        for (Scored<Artist> sartist : sartists) {
            result.output(String.format("%5.3f %5.3f %s",
                    sartist.getScore(),
                    mdb.artistGetNormalizedPopularity(sartist.getItem()),
                    sartist.getItem().getName()));
        }
    }

    void dumpArtistTags(MusicDatabase mdb, WorkbenchResult result, List<Scored<ArtistTag>> sartistTags) throws AuraException, RemoteException {
        result.output(String.format("%5s %5s %s", "Score", "Pop", "Name"));
        for (Scored<ArtistTag> sartistTag : sartistTags) {
            result.output(String.format("%5.3f %5.3f %s",
                    sartistTag.getScore(),
                    mdb.artistTagGetNormalizedPopularity(sartistTag.getItem()),
                    sartistTag.getItem().getName()));
        }
    }
}
