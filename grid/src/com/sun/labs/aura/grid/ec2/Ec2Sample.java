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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.xerox.amazonws.ec2.ConsoleOutput;
import com.xerox.amazonws.ec2.Jec2;
import com.xerox.amazonws.ec2.ImageDescription;
import com.xerox.amazonws.ec2.KeyPairInfo;
import com.xerox.amazonws.ec2.ReservationDescription;
import com.xerox.amazonws.ec2.ReservationDescription.Instance;

public class Ec2Sample {
    private static Log logger = LogFactory.getLog(Ec2Sample.class);

	public static void main(String [] args) throws Exception {
		Properties props = new Properties();
		props.load(Ec2Sample.class.getResourceAsStream("aws.properties"));
		Jec2 ec2 = new Jec2(props.getProperty("aws.accessId"), props.getProperty("aws.secretKey"));
        
		List<String> params = new ArrayList<String>();
		List<ImageDescription> images = ec2.describeImages(params);
		System.out.println(String.format("%d available images", images.size()));
		for (ImageDescription img : images) {
			if (img.getImageState().equals("available")) {
				System.out.println(img.getImageId()+"\t"+img.getImageLocation()+"\t"+img.getImageOwnerId());
			}
		}

		// describe instances
		params = new ArrayList<String>();
		List<ReservationDescription> instances = ec2.describeInstances(params);
		System.out.println(String.format("%d instances", instances.size()));
		String instanceId = null;
		for (ReservationDescription res : instances) {
			System.out.println(res.getOwner()+"\t"+res.getReservationId());
			if (res.getInstances() != null) {
				for (Instance inst : res.getInstances()) {
					System.out.println("\t"+inst.getImageId()+"\t"+inst.getDnsName()+"\t"+inst.getState()+"\t"+inst.getKeyName());
					instanceId = inst.getInstanceId();
				}
			}
		}

		// test console output
        if(instanceId != null) {
            ConsoleOutput consOutput = ec2.getConsoleOutput(instanceId);
            System.out.println("Console Output:");
            System.out.println(consOutput.getOutput());
        }

		// show keypairs
		List<KeyPairInfo> info = ec2.describeKeyPairs(new String [] {});
		System.out.println("keypair list");
		for (KeyPairInfo i : info) {
			System.out.println("keypair : "+i.getKeyName()+", "+i.getKeyFingerprint());
		}
	}
}

