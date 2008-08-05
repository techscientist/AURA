
package com.sun.labs.aura.datastore.impl;

import com.sun.labs.aura.AuraService;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.util.props.ComponentRegistry;
import com.sun.labs.util.props.Configurable;
import com.sun.labs.util.props.ConfigurationManager;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import net.jini.core.lookup.ServiceItem;
import net.jini.core.lookup.ServiceRegistrar;

/**
 * A ProcessManager that waits for processes to be started manually and returns
 * once the processes have appeared.
 */
public class ManualProcessManager implements ProcessManager,
        Configurable,
        AuraService {
    
    protected ConfigurationManager cm;
    
    protected Logger logger;
    
    public ManualProcessManager() {
        
    }

    public PartitionCluster createPartitionCluster(DSBitSet prefix)
            throws AuraException, RemoteException {
        //
        // Enter a loop waiting for the requested partition to appear in
        // the service registry.
        PartitionCluster target = null;
        boolean found = false;
        int iter = 0;
        while (!found && iter < 12) {
            try {
                Thread.sleep(15 * 1000);
            } catch (InterruptedException e) {
            }
            iter++;
            logger.info("Checking for PC " + prefix);
            List<ServiceItem> svcs = getServices();
            for (ServiceItem svc : svcs) {
                if (svc.service instanceof PartitionCluster) {
                    PartitionCluster pc = (PartitionCluster)svc.service;
                    if (pc.getPrefix().equals(prefix)) {
                        target = pc;
                        found = true;
                    }
                }
            }
        }

        iter = 0;
        while (found && !target.isReady() && iter < 10) {
            try {
                Thread.sleep(3 * 1000);
            } catch (InterruptedException e) {
            }
            iter++;
        }
        return target;
    }

    public Replicant createReplicant(DSBitSet prefix)
            throws AuraException, RemoteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void newProperties(PropertySheet ps) throws PropertyException {
        cm = ps.getConfigurationManager();
        logger = ps.getLogger();
    }

    public void start() {
        
    }

    public void stop() {
        
    }
    
    protected List<ServiceItem> getServices() {
        ComponentRegistry cr = cm.getComponentRegistry();
        Map<ServiceRegistrar,List<ServiceItem>> reggies = cr.getJiniServices();
        ServiceRegistrar sr = reggies.keySet().iterator().next();
        return reggies.get(sr);
    }
}