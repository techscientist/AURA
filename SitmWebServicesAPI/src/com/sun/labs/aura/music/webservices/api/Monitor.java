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

package com.sun.labs.aura.music.webservices.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author plamere
 */
public class Monitor {
    private boolean trace;
    private boolean periodicDump;
    private Map<String, Stats> statsMap = Collections.synchronizedMap(new HashMap<String, Stats>());
    private final static long DUMP_DELTA_MS = 20000L;
    private long nextDump = 0;
    private long startupTime;
    private long totalCalls;
    private long periodTime;
    private long periodCalls;

    public Monitor(boolean trace, boolean periodicDump) {
        this.trace = trace;
        this.periodicDump = periodicDump;
        reset();
    }

    public void reset() {
        statsMap.clear();
        startupTime = System.currentTimeMillis();
        totalCalls = 0;
        nextDump = 0;
        
        periodCalls = 0;
        periodTime = System.currentTimeMillis();
    }

    public long opStart() {
        return System.currentTimeMillis();
    }

    public void opFinish(String name, long startTime, long servletTime, boolean ok) {
        long delta = System.currentTimeMillis() - startTime;
        updateStats(name, delta, ok);
        updateStats(name + "-svlt", servletTime, ok);

        updateStats("AllStats", delta, ok);
        updateStats("AllStats-svlt", servletTime, ok);

        if (trace) {
            if (ok) {
                System.out.printf("%8d %8d ms %s\n", delta, servletTime, name);
            } else {
                System.out.printf("ERROR %s\n", name);
            }
        }
        totalCalls++;
        periodCalls++;
        checkForPeriodicDump();
    }

    private synchronized void checkForPeriodicDump() {
        if (periodicDump) {
            long now = System.currentTimeMillis();
            if (now >= nextDump) {
                nextDump = now + DUMP_DELTA_MS;
                dumpAllStats();
            }
        }
    }

    public void opFinish(String name, long startTime, long servletTime) {
        opFinish(name, startTime, servletTime, true);
    }

    public void opError(String name) {
        opFinish(name, 0L, 0L, false);
    }

    private void updateStats(String name, long delta, boolean ok) {
        Stats stats = getStats(name);
        synchronized (stats) {
            stats.count++;

            if (ok) {
                if (delta < stats.minTime) {
                    stats.minTime = delta;
                }

                if (delta > stats.maxTime) {
                    stats.maxTime = delta;
                }

                stats.sumTime += delta;
            } else {
                stats.errors++;
            }
        }
    }

    private synchronized Stats getStats(String name) {
        Stats stats = statsMap.get(name);
        if (stats == null) {
            stats = new Stats();
            statsMap.put(name, stats);
        }
        return stats;
    }

    public void dumpAllStats() {
        long now = System.currentTimeMillis();

        List<String> keys = new ArrayList<String>(statsMap.keySet());
        Collections.sort(keys);
        System.out.printf("%8s %8s %8s %8s %8s %s\n",
                "Count", "AvgTime", "Min", "Max", "Errs", "Operation");
        for (String key : keys) {
            Stats stats = getStats(key);
            if (stats.count > 0) {
                System.out.printf("%8d %8d %8d %8d %8d %s\n",
                        stats.count,
                        stats.sumTime / stats.count,
                        stats.minTime, stats.maxTime,
                        stats.errors, key);
            }
        }

        long totalPeriodTime = now - periodTime;
        float periodCallsPerSecond = periodCalls / (totalPeriodTime / 1000.f);
        System.out.printf("Period Runtime: %d  Calls: %d  Avg Calls/second: %.3f\n",
                totalPeriodTime, periodCalls, periodCallsPerSecond);

        long totalTime = now - startupTime;
        float callsPerSecond = totalCalls / (totalTime / 1000.f);
        System.out.printf(" Total Runtime: %d  Calls: %d  Avg Calls/second: %.3f\n",
                totalTime, totalCalls, callsPerSecond);
        periodCalls = 0;
        periodTime = System.currentTimeMillis();
    }
}

class Stats {

    int count;
    int errors;
    long minTime;
    long maxTime;
    long sumTime;

    Stats() {
        count = 0;
        errors = 0;
        minTime = Long.MAX_VALUE;
        maxTime = -Long.MAX_VALUE;
        sumTime = 0;
    }
}
