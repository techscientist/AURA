/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.grid;

import com.sun.caroline.platform.BaseFileSystemConfiguration;
import com.sun.caroline.platform.ConflictingHostNameException;
import com.sun.caroline.platform.DuplicateNameException;
import com.sun.caroline.platform.DynamicNatConfiguration;
import com.sun.caroline.platform.FileSystem;
import com.sun.caroline.platform.Grid;
import com.sun.caroline.platform.HostNameBinding;
import com.sun.caroline.platform.HostNameBindingConfiguration;
import com.sun.caroline.platform.HostNameZone;
import com.sun.caroline.platform.Network;
import com.sun.caroline.platform.NetworkAddress;
import com.sun.caroline.platform.NetworkAddressAllocationException;
import com.sun.caroline.platform.NetworkConfiguration;
import com.sun.caroline.platform.NetworkSetting;
import com.sun.caroline.platform.ProcessConfiguration;
import com.sun.caroline.platform.ProcessRegistration;
import com.sun.caroline.platform.RunState;
import com.sun.caroline.platform.StorageManagementException;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Queue;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * A set of basic utilities for grid deployment.
 */
public class GridUtil {
    
    static Logger log = Logger.getLogger("com.sun.labs.aura.grid.GridUtil");

    /**
     * Creates a process registration on a grid, reusing an on-grid registration
     * if one exists.
     * @param grid the grid where the registration should be created
     * @param name the name of the registration
     * @param config the configuration for the process
     * @return the on-grid process registration.
     * @throws java.lang.Exception if anything goes wrong creating the registration.
     */
    public static ProcessRegistration createProcess(Grid grid, 
            String name,
            ProcessConfiguration config) throws Exception {
        ProcessRegistration reg = null;
        try {
            reg = grid.createProcessRegistration(name, config);
        } catch(DuplicateNameException dne) {
            log.fine("ProcessRegistration: " + name + " already exists, reusing");
            reg = grid.getProcessRegistration(name);
        }
        return reg;
    }

    public static void startRegistration(ProcessRegistration reg) throws Exception {
        startRegistration(reg, true);
    }

    public static void startRegistration(final ProcessRegistration reg, boolean wait)
            throws Exception {
        Thread starter = new Thread() {

            public void run() {
                try {
                    reg.start(true);
                } catch(Exception e) {
                    log.severe("Registration start failed " + e.
                            getMessage());
                }

                log.fine("Registration " + reg.getName() + " started");
            }
        };
        starter.start();
        if(wait) {
            while(reg.getRunState() != RunState.RUNNING) {
                reg.waitForStateChange(1000000L);
            }
        }
    }

    /**
     * Issues a gentle shutdown for a process.  The resulting process registration
     * should be added to a queue and waitForFinish should be called with that queue.
     * This allows us to quickly start the termination of a number of registrations.
     * @param name
     * @return
     * @throws java.lang.Exception
     */
    public static ProcessRegistration stopProcess(Grid grid, String name) throws Exception {
        ProcessRegistration reg = grid.getProcessRegistration(name);
        if(reg != null) {
            log.fine("Stopping: " + reg);
            reg.shutdownGently(true, 1000);
        } else {
            log.fine("No registration for " + name + " to stop");
        }
        return reg;
    }

    public static void waitForFinish(Queue<ProcessRegistration> q)
            throws Exception {
        waitForFinish(q, 600000);
    }

    /**
     * Waits for the queue of processes to finish.
     * @param q a queue of registrations that we're interested in.
     * @param timeout how long we should wait before forcefully killing processes.
     * @throws java.lang.Exception
     */
    public static void waitForFinish(Queue<ProcessRegistration> q, long timeout)
            throws Exception {
        long finish = timeout + System.currentTimeMillis();
        int n = 0;
        while(q.size() > 0) {
            ProcessRegistration reg = q.poll();

            if(reg == null) {
                continue;
            }

            //
            // If it's not done, then put it back on the queue.
            if(reg.getProcessOutcome() == null || reg.getRunState() !=
                    RunState.NONE) {
                q.offer(reg);
                Thread.sleep(500);
            }

            if(System.currentTimeMillis() > finish) {
                break;
            }

        }

        while(q.size() > 0) {
            ProcessRegistration reg = q.poll();
            if(reg != null) {
                reg.shutdownForcefully(true);
            }
        }
    }

    /**
     * Gets a file system on the grid, creating it if necessary.
     * @param fsName the name of the file system to create.
     * @return a fileystem on the grid.
     */
    public static FileSystem getFS(Grid grid, String fsName) throws
            RemoteException,
            StorageManagementException {
        return getFS(grid, fsName, true);
    }

    /**
     * Create a file system, or accept an existing one with the same name
     * @param fsName
     */
    public static FileSystem getFS(Grid grid, String fsName, boolean allowCreate) throws
            RemoteException,
            StorageManagementException {

        BaseFileSystemConfiguration fsConfiguration =
                new BaseFileSystemConfiguration();

        FileSystem fileSystem = grid.getFileSystem(fsName);

        if(fileSystem == null) {
            if(allowCreate) {
                log.fine("Creating filesystem " + fsName);
                try {
                fileSystem = grid.createBaseFileSystem(fsName, fsConfiguration);
                } catch (DuplicateNameException dne) {
                    //
                    // A filesystem could sneak in between the check and create
                    // calls, so we better deal with that here.
                    log.fine("Found existing filesystem " + fsName);
                    fileSystem = grid.getFileSystem(fsName);
                }
            } else {
                log.fine("Filesystem " + fsName + " not found");
            }
        } else {
            log.fine("Found existing filesystem " +
                    fileSystem.getName());
        }
        return fileSystem;
    }

    /**
     * Gets the file system where log files should be stored.
     * @param grid the grid where we should get the file system
     * @param instance the name of the instance we want the file system for
     * @return the log file system
     * @throws java.rmi.RemoteException
     * @throws com.sun.caroline.platform.StorageManagementException
     */
    public static FileSystem getAuraLogFS(Grid grid, String instance) throws RemoteException, StorageManagementException {
        return getFS(grid, instance + "-aura.logs");
    }
    

    /**
     * The mount point for the logs file system in a deployed service.
     */
    public static final String logFSMntPnt = "/files/auraLogs";

    /**
     * Gets the file system where code should be stored.
     * @param grid the grid where we should get the filesystem
     * @param instance the instance we want the file system for
     * @return the code file system
     * @throws java.rmi.RemoteException
     * @throws com.sun.caroline.platform.StorageManagementException
     */
    public static FileSystem getAuraDistFS(Grid grid, String instance) throws RemoteException, StorageManagementException {
        return getFS(grid, instance + "-aura.dist");
    }

    /**
     * The mount point for the code file system in a deployed service.
     */
    public static final String auraDistMntPnt = "/files/auraDist";

    /**
     * Gets the usage (in bytes) for all of the filesystems registered for a
     * given grid account.
     * @param grid the grid for which we want disk usage.
     * @return the usage (in bytes) on the grid
     * @throws java.lang.Exception if there is a problem getting the usage from
     * the grid
     */
    public static long getDiskUsage(Grid grid) throws Exception {

        long total = 0;
        for(FileSystem fs : grid.findAllFileSystems()) {
            total += fs.getMetrics().getSpaceUsed();
        }
        return total;
    }
    /**
     * Get an address for a given hostname.  The hostname should be based
     * on the process name.  The address is allocated and a host name binding
     * is created for it.
     * 
     * @param hostName
     * @return
     * @throws java.lang.Exception
     */
    public static NetworkAddress getAddressFor(Grid grid, Network network, String hostName) throws Exception {
        // Allocate the internal addresses and the real services behind the
        // virtual service
        HostNameZone hnZone = grid.getInternalHostNameZone();
        NetworkAddress internalAddress = null;

        try {
            internalAddress =
                    network.allocateAddress("addr-" + hostName);
            log.fine("Allocated internal address " + internalAddress.
                    getAddress());
        } catch(DuplicateNameException e) {
            internalAddress = network.getAddress("addr-" + hostName);
            log.finer("Reusing address " + internalAddress.getAddress());
        } catch(NetworkAddressAllocationException e) {
            log.severe("Error allocating address: " + e.getMessage());
            throw e;
        }

        bindHostName(hnZone, internalAddress, hostName);
        return internalAddress;
    }

    public static NetworkAddress getExternalAddressFor(Grid grid, Network network,
            String name) throws Exception {
        // Allocate an external address for the virtual service if necessary
        NetworkAddress externalAddress = null;
        try {
            externalAddress =
                    grid.allocateExternalAddress(name + "-ext");
            log.fine("Allocated external address " +
                    externalAddress.getAddress());
        } catch(DuplicateNameException e) {
            log.finer("External address exists, reusing");
            externalAddress = grid.getExternalAddress(name + "-ext");
        } catch(NetworkAddressAllocationException e) {
            log.severe("Error allocating external address: " + e.getMessage());
            System.exit(2);
        }
        bindHostName(grid.getExternalHostNameZone(), externalAddress, name);
        return externalAddress;
    }

    public static void bindHostName(HostNameZone hnZone,
            NetworkAddress addr,
            String hostName) throws Exception {
        HostNameBinding binding = null;
        try {
            HostNameBindingConfiguration hnbConf =
                    new HostNameBindingConfiguration();
            Collection<UUID> addrs = new ArrayList<UUID>();
            addrs.add(addr.getUUID());
            hnbConf.setAddresses(addrs);
            hnbConf.setHostName(hostName);
            binding =
                    hnZone.createBinding(hnbConf.getHostName(), hnbConf);
        } catch(DuplicateNameException dne) {
            binding = hnZone.getBinding(hostName);
            log.finer("Host name \"" + hostName +
                    "\" has already been defined as " +
                    binding.getConfiguration().getAddresses().toArray()[0]);
        } catch(ConflictingHostNameException chne) {
            binding = hnZone.getBinding(hostName);
            log.finer("Host name \"" + hostName +
                    "\" has already been defined as " +
                    binding.getConfiguration().getAddresses().toArray()[0]);
        }

    }

    public static void createNAT(Grid grid, String instance, 
            UUID external, UUID internal, String name)
            throws Exception {
        NetworkConfiguration netConf =
                new DynamicNatConfiguration(external,
                internal);
        try {
            grid.createNetworkSetting(instance + "-" + name + "-nat",
                    netConf);
        } catch(DuplicateNameException dne) {
            NetworkSetting ns = grid.getNetworkSetting(instance +
                    "-" + name + "-nat");
            ns.changeConfiguration(netConf);
        }
    }
    
    public static URL getConfigURL(String arg) {
        URL cu = com.sun.labs.aura.grid.GridUtil.class.getResource(arg);
        if(cu == null) {
            try {
            cu = (new File(arg)).toURI().toURL();
            } catch (MalformedURLException mue) {
                return null;
            }
        }
        return cu;
    }
}