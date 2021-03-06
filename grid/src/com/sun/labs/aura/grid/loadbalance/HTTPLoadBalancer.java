/*
 * Copyright 2007-2009 Sun Microsystems, Inc. All Rights Reserved.
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

package com.sun.labs.aura.grid.loadbalance;

import com.sun.caroline.platform.HttpVirtualServiceConfiguration;
import com.sun.caroline.platform.Network;
import com.sun.caroline.platform.NetworkAddress;
import com.sun.caroline.platform.NetworkSetting;
import com.sun.caroline.platform.ProcessRegistration;
import com.sun.caroline.platform.RealService;
import com.sun.caroline.platform.Resource;
import com.sun.caroline.platform.ResourceName;
import com.sun.caroline.platform.RunState;
import com.sun.labs.aura.grid.ServiceAdapter;
import com.sun.labs.util.props.ConfigString;
import com.sun.labs.util.props.PropertyException;
import com.sun.labs.util.props.PropertySheet;
import java.net.URI;
import java.net.URISyntaxException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * A class that can be used to start an HTTP load-balancer on-grid.
 */
public class HTTPLoadBalancer extends ServiceAdapter {

    /**
     * The (partial) name of the services that we'll be load-balancing.
     */
    @ConfigString(defaultValue="www")
    public static final String PROP_SERVICE_NAME = "serviceName";

    private String serviceName;

    /**
     * The external host name to use.
     */
    @ConfigString(defaultValue="")
    public static final String PROP_HOST_NAME = "hostName";

    private String hostName;

    @ConfigString(defaultValue="http://www.tastekeeper.com/sorry")
    public static final String PROP_SORRY_PAGE = "sorryPage";

    private URI sorryPage;

    public String serviceName() {
        return "StartHTTPLB";
    }

    private Pattern servicePattern;
    
    public void start() {

        logger.info(String.format("service: %s host: %s", serviceName, hostName));


        //
        // Get the services that we'll be balancing.  We'll enumerate all of the
        // network addresses and add the ones that match the pattern of the
        // servlet containers that were deployed.  We'll also collect all
        // the internal addresses of the services so that we can create
        // NAT rules for them to talk to the outside world.
        List<RealService> services = new ArrayList<RealService>();
        List<NetworkAddress> intAddrs = new ArrayList<NetworkAddress>();
        Network network = gu.getNetwork();
        for (NetworkAddress addr : network.findAllAddresses()) {
            String name = ResourceName.getCSName(addr.getName());
            if (servicePattern.matcher(name).matches()) {

                //
                // See if this network address is associated with a running
                // process.
                try {
                    for (Resource ref : addr.getReferences()) {
                        if (ref instanceof ProcessRegistration) {
                            RunState state = ((ProcessRegistration) ref).getRunState();
                            if (state == RunState.RUNNING ||
                                    state == RunState.STARTING) {
                                logger.info("Got service at " + addr.getName());
                                services.add(new RealService(addr.getUUID(), 80));
                                intAddrs.add(addr);
                            }
                        }
                    }
                } catch (RemoteException rx) {
                    logger.severe("Error checking network references" + rx);
                }
            }
        }

        logger.info("Got " + services.size() + " services to load balance");

        if (services.size() == 0) {
            return;
        }

        //
        // Now get the configuration.  We'll use the on-grid one if we can.
        NetworkSetting lbns = null;
        HttpVirtualServiceConfiguration config = null;
        String lbName = instance + "-" + serviceName + "-lb";


        try {
            lbns = grid.getNetworkSetting(lbName);
            if(lbns != null) {
                config =
                        (HttpVirtualServiceConfiguration) lbns.getConfiguration();
            }
        } catch (RemoteException rx) {
            logger.log(Level.SEVERE, "Errory getting network configuration", rx);
        }

        boolean create = config == null;

        //
        // There isn't one, so make one.
        if(create) {
            NetworkAddress ext;
            try {
                //
                // We need an external address and hostname.
                ext = gu.getExternalAddressFor(serviceName, hostName);
                logger.info(String.format("external address: %s", ext));
            } catch (Exception ex) {
                logger.log(Level.SEVERE, "Unable to get external address for LB",
                        ex);
                return;
            }
            config = new HttpVirtualServiceConfiguration();
            config.setExternalNetworkAddress(ext.getUUID());
        }

        
        //
        // Set up the load balancer to balance our servlet containers.
        String cookieName = "aura-" + serviceName + "-lb";
        HttpVirtualServiceConfiguration.CookieInfo cookie =
                new HttpVirtualServiceConfiguration.CookieInfo(
                cookieName,
                hostName + ".tastekeeper.com", null);
        HttpVirtualServiceConfiguration.ObjectRule.ComponentMatch rule =
                new HttpVirtualServiceConfiguration.ObjectRule.ComponentMatch(
                HttpVirtualServiceConfiguration.ObjectRule.URIComponent.URI,
                "*");
        HttpVirtualServiceConfiguration.SorrySetting sorry =
                new HttpVirtualServiceConfiguration.SorrySetting(
                HttpVirtualServiceConfiguration.SorrySetting.Action.REDIRECT,
                sorryPage);
        HttpVirtualServiceConfiguration.RequestPolicy policy =
                new HttpVirtualServiceConfiguration.RequestPolicy(rule,
                services, cookie, sorry);
        List<HttpVirtualServiceConfiguration.RequestPolicy> policies =
                new ArrayList();
        policies.add(policy);
        config.setRequestPolicies(policies);

        try {
            if (create) {
                grid.createNetworkSetting(lbName, config);
            } else {
                //
                // Change the actual configuration.
                lbns.changeConfiguration(config);
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE,
                    "Error creating or changing load balancer config",
                    ex);
        }

        //
        // Now create the dynamic NAT rules to allow each machine to talk
        // to the outside world.  Start with an external addr to use.
        try {
            NetworkAddress natExt = gu.getExternalAddressFor(serviceName + "-outbound");
            for (NetworkAddress intAddr : intAddrs) {
                gu.createNAT(natExt.getUUID(), intAddr.getUUID(), ResourceName.getCSName(intAddr.getName()) + "-nat");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to create NAT");
        }
    }

    public void stop() {
    }

    @Override
    public void newProperties(PropertySheet ps) throws PropertyException {
        super.newProperties(ps);
        serviceName = ps.getString(PROP_SERVICE_NAME);
        hostName = ps.getString(PROP_HOST_NAME);
        if(hostName.trim().length() == 0) {
            hostName = serviceName;
        }
        servicePattern = Pattern.compile(String.format("%s-[0-9]+-int$", serviceName));
        logger.info(String.format("service name: %s pattern: %s hostName: %s", serviceName, servicePattern, hostName));
        try {
            sorryPage = new URI(ps.getString(PROP_SORRY_PAGE));
        } catch (URISyntaxException ex) {
            throw new PropertyException(ex, ps.getInstanceName(), PROP_SORRY_PAGE, "Bad URI for sorry page: " + ps.getString(
                    PROP_SORRY_PAGE));
        }
    }



}
