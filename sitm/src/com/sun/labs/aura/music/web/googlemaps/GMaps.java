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

package com.sun.labs.aura.music.web.googlemaps;

import com.sun.labs.aura.music.web.Commander;
import com.sun.labs.aura.music.web.WebServiceAccessor;
import com.sun.labs.aura.util.AuraException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 *
 * @author plamere
 */
public class GMaps extends WebServiceAccessor {

    private Commander commander;

    public GMaps() throws IOException, AuraException {

        super("GoogleMaps", "GOOGLEMAPS_API_KEY");

        commander = new Commander("google maps", "http://maps.google.com/maps/geo", "&output=csv&sensor=false&key=" + API_KEY);
        commander.setRetries(1);
        commander.setTimeout(1000);
        commander.setTraceSends(false);
        commander.setMinimumCommandPeriod(500);
    }

    public Location getLocation(String placeName) throws IOException {
        InputStream is = commander.sendCommandRaw("?q=" + commander.encode(placeName));
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        String line = in.readLine();
        String[] fields = line.split(",");
        if (fields.length == 4) {
            int status = Integer.parseInt(fields[0]);
            if (status == 200) {
                int accuracy = Integer.parseInt(fields[1]);
                float lat = Float.parseFloat(fields[2]);
                float longitude = Float.parseFloat(fields[3]);
                return new Location(lat, longitude);
            } else if (status == 620) {
                System.err.println("WARNING: gmaps query too fast");
            }
        }
        return null;
    }

    private static void dump(GMaps gmaps, String place) throws IOException {
        Location l = gmaps.getLocation(place);
        if (l != null) {
            System.out.printf("%.5f, %.5f %s\n", l.getLatitude(), l.getLongitude(), place);
        } else {
            System.out.printf("Can't find place for " + place);
        }
    }

    public static void main(String[] args) throws Exception {
        GMaps gmaps = new GMaps();

        dump(gmaps, "nashua nh");
        dump(gmaps, "montreal ca");
        dump(gmaps, "montreal ca");
        dump(gmaps, "london uk");
        dump(gmaps, "akron oh");
        dump(gmaps, "sydney australia");
        dump(gmaps, "ludlow vermont");
        dump(gmaps, "austin tx");
        dump(gmaps, "garbage location tx");
    }
}
