/*
 *  Copyright (c) 2008, Sun Microsystems Inc.
 *  See license.txt for license.
 */
package com.sun.labs.aura.music.admin.server;

import com.sun.labs.aura.datastore.Attention;
import com.sun.labs.aura.datastore.AttentionConfig;
import com.sun.labs.aura.music.MusicDatabase;
import com.sun.labs.aura.music.admin.client.Constants.Bool;
import com.sun.labs.aura.music.admin.client.WorkbenchResult;
import com.sun.labs.aura.util.AuraException;
import java.rmi.RemoteException;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 * @author plamere
 */
public class ShowRecentAttention extends Worker {

    ShowRecentAttention() {
        super("Show Recent Attention", "Shows recent attention");
        param("src", "the source of the attention", "");
        param("tgt", "the target of the  attention", "");
        param("constrain type", "If true, constrain the type", Bool.values(), Bool.TRUE);
        param("type", "the type of attention", Attention.Type.values(), Attention.Type.VIEWED);
        param("count", "number of attentions to return", 1000);
    }

    @Override
    void go( MusicDatabase mdb, Map<String, String> params, WorkbenchResult result) throws AuraException, RemoteException {
        AttentionConfig ac = new AttentionConfig();
        int count = getParamAsInt(params, "count");
        String src = getParam(params, "src");
        String tgt = getParam(params, "tgt");

        Attention.Type type = (Attention.Type) getParamAsEnum(params, "type");

        if (src.length() > 0) {
            ac.setSourceKey(src);
        }
        if (tgt.length() > 0) {
            ac.setTargetKey(tgt);
        }

        boolean constrainType = getParamAsEnum(params, "constrain type") == Bool.TRUE;
        if (constrainType) {
            ac.setType(type);
        }

        List<Attention> attns = mdb.getDataStore().getLastAttention(ac, count);

        int row = 0;
        for (Attention attn : attns) {
            Long val = attn.getNumber();
            String sval = attn.getString();

            String extra = val != null ? val.toString() : sval != null ? sval : "";
            result.output(String.format("%d %s %s %s %s %s",
                    ++row, attn.getSourceKey(), attn.getTargetKey(), attn.getType().name(),
                    extra, new Date(attn.getTimeStamp()).toString()));

        }
    }
}