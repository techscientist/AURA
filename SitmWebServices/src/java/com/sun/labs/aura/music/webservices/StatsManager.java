/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.labs.aura.music.webservices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

/**
 *
 * @author plamere
 */
public class StatsManager {

    private final Map<String, Stats> statsMap = new HashMap<String, Stats>();
    private final Map<String, Integer> requesters = new HashMap<String, Integer>();
    private long startTime;

    StatsManager() {
        startTime = System.currentTimeMillis();
    }

    synchronized void addStats(HttpServletRequest request, String name, boolean status, long time) {
        Stats stats = statsMap.get(name);
        if (stats == null) {
            stats = new Stats();
            statsMap.put(name, stats);
        }

        if (status) {
            stats.goodCount++;
            stats.goodTime += time;
            stats.lastTime = time;
            if (time < stats.minTime) {
                stats.minTime = time;
            }
            if (time > stats.maxTime) {
                stats.maxTime = time;
            }
        } else {
            stats.badCount++;
        }

        //
        String remoteAddr = request.getRemoteAddr();
        Integer count = requesters.get(remoteAddr);
        if (count == null) {
            requesters.put(remoteAddr, Integer.valueOf(1));
        } else {
            requesters.put(remoteAddr, count + 1);
        }
    }

    public long getStartTime() {
        return startTime;
    }

    synchronized List<String> getNames() {
        List<String> names = new ArrayList<String>(statsMap.keySet());
        Collections.sort(names);
        return names;
    }

    synchronized Stats getStats(String name) {
        Stats stats = statsMap.get(name);
        if (stats != null) {
            return stats.copy();
        }
        return null;
    }

    synchronized Map<String, Integer> getRequestors() {
        return new HashMap<String, Integer>(requesters);
    }
}

class Stats {

    long goodCount;
    long badCount;
    long goodTime;
    long lastTime;
    long minTime = Long.MAX_VALUE;
    long maxTime = -Long.MAX_VALUE;

    public long getBadCount() {
        return badCount;
    }

    public long getGoodCount() {
        return goodCount;
    }

    public long getGoodTime() {
        return goodTime;
    }

    public long getLastTime() {
        return lastTime;
    }

    public long getMaxTime() {
        return maxTime;
    }

    public long getMinTime() {
        return minTime;
    }

    Stats copy() {
        Stats s = new Stats();
        s.goodCount = goodCount;
        s.badCount = badCount;
        s.goodTime = goodTime;
        s.lastTime = lastTime;
        s.minTime = minTime;
        s.maxTime = maxTime;
        return s;
    }
}