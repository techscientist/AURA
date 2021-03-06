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

package com.sun.labs.aura.grid.ec2;

import com.xerox.amazonws.ec2.AttachmentInfo;
import com.xerox.amazonws.ec2.InstanceType;
import com.xerox.amazonws.ec2.KeyPairInfo;
import com.xerox.amazonws.ec2.ReservationDescription;
import com.xerox.amazonws.ec2.VolumeInfo;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.Properties;

/**
 * A main class that will start whatever parts of Aura need to be started.
 */
public class StartAura {

    public static String[] prefixes = new String[] {"00", "01", "10", "11"};

    public static String getReggieMetaData() {
        return "auraGroup=live-aura\n";
    }

    public static String getDSHeadMetaData(String regHost) {
        return String.format("registryHost=%s\n", regHost) +
                "auraGroup=live-aura\n" +
                "name.0=dshead\n" +
                "config.0=/com/sun/labs/aura/grid/ec2/resource/dataStoreHeadConfig.xml\n" +
                "starter.0=starter\n" +
                "opts.0=-Xmx1g\n" +
                "logType.0=dshead\n";
    }

    public static String getReplicantMetaData(String regHost, String prefix) {
        return String.format("registryHost=%s\n", regHost) +
                "auraGroup=live-aura\n" +
                String.format("name.0=rep-%s\n", prefix) +
                "config.0=/com/sun/labs/aura/grid/ec2/resource/repPCConfig.xml\n" +
                "starter.0=starter\n" +
                String.format("opts.0=-Xmx1500m -DauraHome=/datapool/aura -Dprefix=%s\n", prefix) +
                "logType.0=rep\n";
    }

    public static void main(String[] args) throws Exception {

        String instance = "live";
        InstanceType repType = InstanceType.DEFAULT;
        
        if(args.length > 0) {
            instance = args[0];
        }

        if(args.length > 1) {
            repType = InstanceType.valueOf(args[1]);
        }

        System.out.println(String.format("Instance: %s Replicant type: %s", instance, repType));

        //
        // Load the user's property file for this instance, or the default one,
        // if there isn't one for this instance.
        File defaultPropsFile = new File(System.getProperty("user.home") +
                File.separatorChar + ".ec2" +
                File.separatorChar + "aws.properties");
        File instancePropsFile = new File(System.getProperty("user.home") +
                File.separatorChar + ".ec2" +
                File.separatorChar + "aws." + instance + ".properties");
        EC2Grid grid;
        if(instancePropsFile.exists()) {
            System.out.println(String.format("Loading properties for instance %s", instance));
            grid = new EC2Grid(instancePropsFile);
        } else {
            System.out.println(String.format("Loading default properties"));
            grid = new EC2Grid(defaultPropsFile);
        }

        //
        // The distribution server is running reggie and exports the Aura
        // code distribution over nfs.
        VolumeInfo distVol = grid.getDistVol();
        KeyPairInfo kpi = grid.getKeyPairInfo("aura");
        ReservationDescription.Instance distInst;

        //
        // If the dist volume isn't attached to anything, then attach it.  In
        // either case, remember the private host name, so that we can set up the
        // data node's instance metadata.
        AttachmentInfo ai = grid.getAttachmentInfo(distVol);
        if(ai == null || !ai.getStatus().startsWith("attach")) {
            System.out.println(String.format("Starting dist instance"));
            distInst = grid.launch(grid.getProperty("ami.aura-dist"),null,
                    kpi, getReggieMetaData(), distVol, 2);
        } else {
            System.out.println(String.format("Getting dist instance"));
            distInst = grid.getInstance(ai.getInstanceId());
        }
        System.out.println(String.format("Reggie/dist instance: %s %s",
                distInst.getInstanceId(), distInst.getDnsName()));
        String regHost = distInst.getPrivateDnsName();
        grid.setProperty("instance.dist", distInst.getInstanceId());
        grid.setProperty("aura.registryHost", regHost);
        
        //
        // Start the data store head.
        String dsInstID = grid.getProperty("instance.dsHead");
        ReservationDescription.Instance dshInst;
        if(dsInstID == null) {
            System.out.println(String.format("Starting data store head instance"));
            dshInst = grid.launch(grid.getProperty("ami.aura-nondata"),
                    null,
                    kpi,
                    getDSHeadMetaData(regHost));
        } else {
            dshInst = grid.getInstance(dsInstID);
            if(dshInst == null || dshInst.isTerminated()) {
                System.out.println(String.format(
                        "Starting data store head instance"));
                dshInst = grid.launch(grid.getProperty("ami.aura-nondata"),
                        null,
                        kpi,
                        getDSHeadMetaData(regHost));
            }
        }
        grid.setProperty("instance.dsHead", dshInst.getInstanceId());

        //
        // Start the replicants.
        for(String prefix : prefixes) {
            System.out.println(String.format("Prefix: " + prefix));
            String repInstID = grid.getProperty("instance.rep." + prefix);
            VolumeInfo repVol = grid.getVolumeInfo(grid.getProperty("volume.data."+prefix));
            ReservationDescription.Instance repInst;
            if(repInstID == null) {
                System.out.println(String.format("Starting instance for prefix %s", prefix));
                repInst = grid.launch(grid.getProperty("ami.aura-data"),
                        repType,
                        kpi,
                        getReplicantMetaData(regHost, prefix),
                        repVol);
            } else {
                repInst = grid.getInstance(repInstID);
                if(repInst == null || repInst.isTerminated()) {
                    System.out.println(String.format(
                            "Starting instance for prefix %s", prefix));
                    repInst = grid.launch(grid.getProperty("ami.aura-data"),
                            repType,
                            kpi,
                            getReplicantMetaData(regHost, prefix),
                            repVol);
                }
            }
            grid.setProperty("instance.rep." + prefix, repInst.getInstanceId());
        }

        Properties props = grid.getProperties();

        //
        // Write out the props with the instance IDs.
        //
        // Load the user's property file.
        String outPropsFile = System.getProperty("user.home") +
                File.separatorChar + ".ec2" +
                File.separatorChar + "aws." + instance + ".properties";
        Writer w = new FileWriter(outPropsFile);
        props.store(w, " Properties for the " + instance + " instance of aura");
        w.close();
    }

}
