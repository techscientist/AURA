package com.sun.labs.aura.util;

import com.sun.labs.aura.AuraService;
import com.sun.labs.aura.datastore.impl.DataStoreHead;
import com.sun.labs.util.props.ConfigComponent;
import com.sun.labs.util.props.Configurable;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An implementation of the 
 */
public class StatServiceImpl implements StatService, AuraService, Configurable {

    @ConfigComponent(type = com.sun.labs.aura.datastore.DataStore.class)
    public static final String PROP_DATA_STORE = "dataStore";
    private DataStoreHead dataStore;
    private Map<String, Counter> counters;

    public StatServiceImpl() {
        counters = new HashMap<String, Counter>();
    }

    private Counter getCounter(String counterName) {
        Counter c = counters.get(counterName);
        if (c == null) {
            c = new Counter(counterName);
            counters.put(counterName, c);
        }
        return c;
    }

    public void create(String counterName) throws RemoteException {
        getCounter(counterName);
    }

    public void set(String counterName, long val) throws RemoteException {
        getCounter(counterName).set(val);
    }

    public long incr(String counterName) {
        return getCounter(counterName).incr(1, 1);
    }

    public long incr(String counterName, int val) throws RemoteException {
        return getCounter(counterName).incr(val, 1);
    }

    public long incr(String counterName, int val, int n) throws RemoteException {
        return getCounter(counterName).incr(val, n);
    }

    public long get(String counterName) throws RemoteException {
        return getCounter(counterName).value.get();
    }

    public double getAverage(String counterName) throws RemoteException {
        Counter c = getCounter(counterName);
        if (c.ticks.get() > 0) {
            return (double) c.value.get() / c.ticks.get();
        } else {
            return 0.0;
        }
    }

    public double getAveragePerSecond(String counterName) throws RemoteException {
        Counter c = getCounter(counterName);
        double time = (System.currentTimeMillis() - c.start) / 1000.0;
        if (time > 0) {
            return c.value.get() / time;
        } else {
            return 0.0;
        }
    }

    public double getAveragePerMinute(String counterName) throws RemoteException {
        Counter c = getCounter(counterName);
        double time = (System.currentTimeMillis() - c.start) / (1000.0 * 60.0);
        if (time > 0) {
            return c.value.get() / time;
        } else {
            return 0.0;
        }
    }

    public String[] getCounterNames() throws RemoteException {
        return counters.keySet().toArray(new String[counters.keySet().size()]);
    }

    public void newProperties(PropertySheet ps) throws PropertyException {
        dataStore = (DataStoreHead) ps.getComponent(PROP_DATA_STORE);
    }

    public class Counter implements Serializable {

        public Counter(String name) {
            this.name = name;
            value = new AtomicLong();
            ticks = new AtomicLong();
            start = System.currentTimeMillis();
        }

        public long incr(int v, int n) {
            long ret = value.addAndGet(v);
            ticks.addAndGet(n);
            return ret;
        }

        public void set(long v) {
            value.set(v);
            ticks.set(0);
            start = System.currentTimeMillis();
        }
        String name;
        AtomicLong value;
        AtomicLong ticks;
        long start;
    }

    public void start() {
    //
    // Read from persistent storage.
    }

    public void stop() {
    //
    // Write to persistent storage.
    }
}
