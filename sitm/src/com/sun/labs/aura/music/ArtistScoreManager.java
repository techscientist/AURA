/*
 *  Copyright (c) 2008, Sun Microsystems Inc.
 *  See license.txt for license.
 */

package com.sun.labs.aura.music;

import com.sun.labs.aura.util.Scored;
import com.sun.labs.aura.util.ScoredComparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author plamere
 */


class ArtistScoreManager {
    private Set<Artist> seedSet = new HashSet<Artist>();
    private Map<Artist, Double> artistScores = new HashMap<Artist, Double>();
    private Map<Artist, Map<String, Double>> reasonMap = new HashMap();
    private double maxPopularity = 0.0;
    private double minPopularity = Double.MAX_VALUE;
    private double sumPopularity = 0.0;
    private boolean popularityFilter = true;
    
    ArtistScoreManager(boolean popularityFilter) {
        this.popularityFilter = popularityFilter;
    }

    void addSeedArtist(Artist artist) {
        seedSet.add(artist);
        if (artist.getPopularity() > maxPopularity) {
            maxPopularity = artist.getPopularity();
        // System.out.println("max pop " + artist.getName() + " " + artist.getPopularity());
        }

        if (artist.getPopularity() < minPopularity) {
            minPopularity = artist.getPopularity();
        // System.out.println("min pop " + artist.getName() + " " + artist.getPopularity());
        }
        sumPopularity += artist.getPopularity();
    }


    void accum(Artist artist, double val) {
        Double score = artistScores.get(artist);

        if (score == null) {
            score = new Double(val);
        } else {
            score = new Double(score.doubleValue() + val);
        }
        artistScores.put(artist, score);
    } 

    void accum(Artist artist, String reason, double val) {
        Double score = artistScores.get(artist);

        if (score == null) {
            score = new Double(val);
        } else {
            score = new Double(score.doubleValue() + val);
        }
        artistScores.put(artist, score);

        Map<String, Double> reasonMapEntry = reasonMap.get(artist);
        if (reasonMapEntry == null) {
            reasonMapEntry = new HashMap();
            reasonMap.put(artist, reasonMapEntry);
        } 
        reasonMapEntry.put(reason, Double.valueOf(val));
    } 

    List<Scored<String>> getReason(Artist artist) {
        Map<String, Double> reasonMapEntry = reasonMap.get(artist);
        List<Scored<String>> results = new ArrayList();
        if (reasonMapEntry != null) {
            for (Map.Entry<String, Double> e : reasonMapEntry.entrySet()) {
                results.add(new Scored<String>(e.getKey(), e.getValue()));
            }
            Collections.sort(results, new ScoredComparator<String>());
            Collections.reverse(results);
        }
        return results;
    }

    List<Scored<Artist>> getTop(int num) {
        List<Scored<Artist>> scoredList = new ArrayList<Scored<Artist>>();
        double averagePopularity = Double.MAX_VALUE;
        if (seedSet.size() > 0) {
            averagePopularity = sumPopularity / seedSet.size();
        }

        // System.out.println("avg pop " + averagePopularity);
        for (Artist a : artistScores.keySet()) {
            if (!seedSet.contains(a)) {
                if (!popularityFilter || a.getPopularity() < averagePopularity) {
                    scoredList.add(new Scored<Artist>(a, artistScores.get(a)));
                }
            }
        }

        Collections.sort(scoredList, new ScoredComparator<Artist>());
        Collections.reverse(scoredList);
        if (scoredList.size() > num) {
            scoredList = scoredList.subList(0, num);
        }
        return scoredList;
    }
}