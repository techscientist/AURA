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
import com.sun.labs.aura.datastore.Attention.Type;
import com.sun.labs.aura.datastore.AttentionConfig;
import com.sun.labs.aura.datastore.StoreFactory;
import com.sun.labs.aura.music.MusicDatabase;
import com.sun.labs.aura.music.admin.client.TestStatus;
import com.sun.labs.aura.util.AuraException;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 *
 * @author plamere
 */
public class AttentionFullTest extends Test {

    private int tries;
    private boolean dump = true;

    AttentionFullTest(int count) {
        super("Attention RoundTrip " + count);
        this.tries = count;
    }

    @Override
    protected void go(MusicDatabase mdb, TestStatus ts) throws AuraException, RemoteException {
        String user = "TEST_USER_" + new Random().nextInt();
        try {
            addAttention(mdb, ts, user);
            testAttention(mdb, ts, user);
        } finally {
            removeUserAndAttention(mdb, ts, user);
        }
    }

    protected void addAttention(MusicDatabase mdb, TestStatus ts, String user) throws AuraException, RemoteException {
        for (int i = 0; i < tries / 2; i++) {
            String target = "target " + i + " for " + user;
            mdb.getDataStore().attend(StoreFactory.newAttention(user, target, Type.VIEWED));
            sleep(2);
        }

        sleep(1000);

        for (int i = tries / 2; i < tries; i++) {
            String target = "target " + i + " for " + user;
            mdb.getDataStore().attend(StoreFactory.newAttention(user, target, Type.PLAYED, Long.valueOf(i)));
            sleep(2);
        }
    }

    protected void testAttention(MusicDatabase mdb, TestStatus ts, String user) throws AuraException, RemoteException {
        testRetrieveByUserKey(mdb, ts, user);
        testRetrieveByTargetKey(mdb, ts, user);
        testRetrieveSince(mdb, ts, user);
    // retrieve by user + type test
    }

    protected void testRetrieveSince(MusicDatabase mdb, TestStatus ts, String user) throws AuraException, RemoteException {
        if (ts.isPassed()) {
            AttentionConfig ac = new AttentionConfig();
            ac.setSourceKey(user);
            List<Attention> attns = mdb.getDataStore().getAttention(ac);
            
            if (attns.size() != tries) {
                ts.fail("(trs) bad attention count for " + user + " expected " + tries + " found " + attns.size());
                return;
            }

            // find the first attention where we transition from VIEWED to PLAYED.
            sortByTimeAdded(attns);

            long firstPlayTime = 0L;
            for (Attention attn : attns) {
                if (attn.getType().equals(Type.PLAYED)) {
                    firstPlayTime = attn.getTimeStamp();
                    break;
                }
            }

            if (firstPlayTime == 0L) {
                ts.fail("(trs) Can't find PLAYED attention");
                return;
            }

            // colllect all the attention since the time where we added the first PLAYED attentin

            List<Attention> recentAttns = mdb.getDataStore().getAttentionSince(ac, new Date(firstPlayTime - 1L));
            if (recentAttns.size() != tries / 2) {
                ts.fail("(trs) bad recent attention count for " + user + " expected " + (tries / 2) + " found " + recentAttns.size());
                return;
            }


            for (Attention attn : recentAttns) {
            // make sure that they occur since then
                if (attn.getTimeStamp() < firstPlayTime) {
                    ts.fail("(trs) bad recent attention timestamp for " + attn + " should be later than " + firstPlayTime);
                    return;
                }

                // make sure that they are of the proper type
                if (!attn.getType().equals(Type.PLAYED)) {
                    ts.fail("(trs) bad recent attention typ for " + attn + " should be " + Type.PLAYED.name());
                    return;
                }

                // test the that user is proper

                if (!attn.getSourceKey().equals(user)) {
                    ts.fail("(trs) unexpected source key for attention " + attn + " expected " + user);
                    return;
                }
            }
        }
    }


    protected void testRetrieveByUserAndType(MusicDatabase mdb, TestStatus ts, String user) throws AuraException, RemoteException {
        if (ts.isPassed()) {
            AttentionConfig ac = new AttentionConfig();
            ac.setSourceKey(user);
            ac.setType(Type.PLAYED);
            List<Attention> attns = mdb.getDataStore().getAttention(ac);
            
            if (attns.size() != tries/2) {
                ts.fail("(trut) bad attention count for " + user + " expected " + tries + " found " + attns.size());
                return;
            }

            for (Attention attn : attns) {

                // make sure that they are of the proper type
                if (!attn.getType().equals(Type.PLAYED)) {
                    ts.fail("(trut) bad recent attention typ for " + attn + " should be " + Type.PLAYED.name());
                    return;
                }

                // test the that user is proper

                if (!attn.getSourceKey().equals(user)) {
                    ts.fail("(trut) unexpected source key for attention " + attn + " expected " + user);
                    return;
                }
            }
        }
    }


    protected void testRetrieveByUserKey(MusicDatabase mdb, TestStatus ts, String user) throws AuraException, RemoteException {
        if (ts.isPassed()) {
            AttentionConfig ac = new AttentionConfig();
            ac.setSourceKey(user);
            List<Attention> attns = mdb.getDataStore().getAttention(ac);

            

            if (attns.size() != tries) {
                ts.fail("(truk) bad attention count for " + user + " expected " + tries + " found " + attns.size());
                return;
            }

            sortByTimeAdded(attns);
            dump(ts, attns);

            int count = 0;
            for (Attention attn : attns) {
                if (!attn.getSourceKey().equals(user)) {
                    ts.fail("(truk) unexpected source key for attention " + attn + " expected " + user);
                    return;
                }
                String target = "target " + count + " for " + user;
                if (!attn.getTargetKey().equals(target)) {
                    ts.fail("(truk) unexpected target key for attention " + attn + " expected " + target);
                    return;
                }


                Type expectedType = count < tries / 2 ? Type.VIEWED : Type.PLAYED;

                if (!attn.getType().equals(expectedType)) {
                    ts.fail("(truk) unexpected type for attention " + attn + " expected " + expectedType.name());
                    return;
                }
                count++;
            }
        }
    }

    protected void testRetrieveByTargetKey(MusicDatabase mdb, TestStatus ts, String user) throws AuraException, RemoteException {
        if (ts.isPassed()) {

            for (int i = 0; i < tries; i++) {
                String targetKey = "target " + i + " for " + user;
                AttentionConfig ac = new AttentionConfig();
                ac.setTargetKey(targetKey);
                List<Attention> attns = mdb.getDataStore().getAttention(ac);

                if (attns.size() != 1) {
                    ts.fail("(trtk) bad attention count when retrieving by target " + targetKey + " expected 1  found " + attns.size());
                    return;
                }

                Attention attn = attns.get(0);

                if (!attn.getSourceKey().equals(user)) {
                    ts.fail("(trtk) when retrieving by targetKey unexpected source key for attention " + attn + " expected " + user);
                    return;
                }

                if (!attn.getTargetKey().equals(targetKey)) {
                    ts.fail("(trtk) when retrieving by targetKey unexpected target key for attention " + attn + " expected " + targetKey);
                    return;
                }

                Type expectedType = i < tries / 2 ? Type.VIEWED : Type.PLAYED;

                if (!attn.getType().equals(expectedType)) {
                    ts.fail("(trtk) when retrieving by attention, unexpected type for attention " + attn + " expected " + expectedType.name());
                    return;
                }
            }
        }
    }

    protected void removeUserAndAttention(MusicDatabase mdb, TestStatus ts, String user) throws AuraException, RemoteException {

        // delete the user

        mdb.deleteListener(user);

        // delete the attention

        {
            AttentionConfig ac = new AttentionConfig();
            ac.setSourceKey(user);
            List<Attention> attns = mdb.getDataStore().getAttention(ac);
            for (Attention attn : attns) {
                mdb.getDataStore().removeAttention(attn.getSourceKey(), attn.getTargetKey(), attn.getType());
            }
        }




        // test to make sure the attention is gone
        {
            AttentionConfig ac = new AttentionConfig();
            ac.setSourceKey(user);
            List<Attention> attns = mdb.getDataStore().getAttention(ac);
            if (attns.size() > 0) {
                ts.fail("(rua) Attention delete failed after deleting user, " + attns.size() + " attention remain.");
                return;
            }
        }

        // test to make sure the user is gone.
        if (mdb.getListener(user) != null) {
            ts.fail("(rua) User deletion failed for " + user);
            return;
        }
    }

    private void dump(TestStatus ts, List<Attention> attns) {
        StringBuilder sb = new StringBuilder();
        sb.append("Attention dump: size: " + attns.size() + "\n");
        for (Attention attn : attns) {
            sb.append("  " + attn.toString());
            sb.append("\n");
        }
        ts.setStackTrace(sb.toString());
    }
}
