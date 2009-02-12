/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.grid.ec2;

import com.xerox.amazonws.ec2.AttachmentInfo;
import com.xerox.amazonws.ec2.KeyPairInfo;
import com.xerox.amazonws.ec2.ReservationDescription;
import com.xerox.amazonws.ec2.VolumeInfo;
import java.util.List;

/**
 *
 * @author stgreen
 */
public class LaunchAttach {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {

        EC2Grid grid = new EC2Grid("/Users/stgreen/.ec2/aws.properties");
        VolumeInfo distVol = grid.getVolumeInfo(grid.getProperty("aura.distvol"));
        KeyPairInfo kpi = grid.getKeyPairInfo("aura");
        ReservationDescription.Instance distInst;

        String prefix = "00";

        if(args.length > 0) {
            prefix = args[0];
        }

        //
        // If the dist volume isn't attached to anything, then attach it.  In
        // either case, remember the private host name, so that we can set up the
        // data node's instance metadata.
        AttachmentInfo ai = grid.getAttachmentInfo(distVol);
        if(ai == null || !ai.getStatus().startsWith("attach")) {
            System.out.println(String.format("Starting dist instance"));
            String instanceMetaData =
                    "auraGroup=Aura\n";
            distInst =
                    grid.launch(grid.getProperty("ami.aura-reggie"),null,
                    kpi, instanceMetaData, distVol, 2);
        } else {
            distInst = grid.getInstance(ai.getInstanceId());
        }
        System.out.println(String.format("Reggie/dist instance: %s %s",
                distInst.getInstanceId(), distInst.getDnsName()));
//        VolumeInfo dataVol = grid.getVolumeInfo(grid.getProperty("aura.datavol."+prefix));
//        String instanceMetaData =
//                "registryHost=" + distInst.getPrivateDnsName() + "\n";
//        System.out.println(String.format("Starting data inst"));
//        instanceMetaData = "auraGroup=live-aura\n" +
//                "registryHost=" + distInst.getPrivateDnsName() + "\n" +
//                "name.0=dshead\n" +
//                "config.0=/com/sun/labs/aura/grid/ec2/resource/dataStoreHeadConfig.xml\n" +
//                "starter.0=starter\n" +
//                "opts.0=-Xmx1g\n" +
//                "logType.0=dshead\n";
//
//        ReservationDescription.Instance dataInst =
//                grid.launch(grid.getProperty("ami.aura-data"),
//                kpi, dataVol, instanceMetaData, 2);
//        System.out.println(String.format("Data instance: %s %s", dataInst.getInstanceId(), dataInst.getDnsName()));
    }

}
