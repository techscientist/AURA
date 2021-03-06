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

package com.sun.labs.aura.util.io;

import com.sun.labs.minion.util.ChannelUtil;
import com.sun.labs.util.SimpleLabsLogFormatter;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * Sorts a keyed input stream.
 */
public class Sorter<K, V> {

    private File in;

    private File bf;

    private File sf;

    private int buffSize;
    
    /**
     * The default buffer size for the blocks to sort.
     */
    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    /**
     * Creates a sorter that will sort records from the input into the sorted
     * file.  We use an intermediate file to hold sorted blocks from the 
     * input before the merge.
     * @param in the input file
     * @param bf a temporary output file for blocks.   This file will be overwritten
     * if it exists and deleted at the end of sorting
     * @param sf the sorted output file.  This file will be overwritten.
     */
    public Sorter(File in, File bf, File sf) throws FileNotFoundException, IOException {
        this(in, bf, sf, DEFAULT_BUFFER_SIZE);
    }
    
    /**
     * Creates a sorter that will sort records from the input into the sorted
     * file.  We use an intermediate file to hold sorted blocks from the 
     * input before the merge.
     * @param in the input file
     * @param bf a temporary output file for blocks.   This file will be overwritten
     * if it exists and deleted at the end of sorting
     * @param sf the sorted output file.  This file will be overwritten.
     * @param buffSize the amount of data from the file to buffer in memory.
     */
    public Sorter(File in, File bf, File sf, int buffSize) throws FileNotFoundException, IOException {
        this.in = in;
        this.bf = bf;
        this.sf = sf;
        this.buffSize = buffSize;
    }

    @SuppressWarnings(value="DLS_DEAD_LOCAL_STORE",
                      justification="Valid use of local variable")
    public void sort() throws IOException {

        StructuredKeyedInputStream<K, V> input = new StructuredKeyedInputStream<K, V>(in);
        
        //
        // If it's already sorted, we're done!
        if(input.getSorted()) {
            StructuredKeyedOutputStream<K,V> output =
                    new StructuredKeyedOutputStream<K, V>(sf, true);
            ChannelUtil.transferFully(input.getChannel(), output.getChannel());
            input.close();
            output.close();
            return;
        }
        
        StructuredKeyedOutputStream<K, V> output =
                new StructuredKeyedOutputStream<K, V>(bf, false);

        Record<K, V> rec;

        List<Record<K, V>> recs = new ArrayList<Record<K, V>>();

        List<SortedRegion> regions = new ArrayList<SortedRegion>();

        SortedRegion cr = new SortedRegion(input.position());

        //
        // Read to the end of file, building up a list of records until we
        // pass the buffer size then sorting that region and writing it to the 
        // blocked file.
        while((rec = input.read()) != null) {
            recs.add(rec);
            long len = input.position() - cr.start;
            if(len >= buffSize) {
                cr = writeSortedRecords(input, cr, regions, recs, output);
            }
        }
        
        //
        // Deal with the last region.
        if(recs.size() > 0) {
            cr = writeSortedRecords(input, cr, regions, recs, output);
        }
        
        input.close();
        output.close();
        
        //
        // If we only had one region, then we're done.  Just rename the blocked
        // file to the sorted file.
        if(regions.size() == 0) {
           if (!bf.renameTo(sf)) {
                throw new IOException("Failed to rename blocked file "
                        + bf.getPath() + " to " + sf.getPath());
           }
           return;
        }

        //
        // OK, now the blocked file contains a number of sorted regions.  We want
        // to merge those regions.  We'll just pretend that they're keyed input
        // files and use the merger.
        List<StructuredKeyedInputStream<K,V>> inputs =
                new ArrayList<StructuredKeyedInputStream<K, V>>();
        for(SortedRegion sr : regions) {
            inputs.add(new StructuredKeyedInputStream<K, V>(bf, sr));
        }
        Merger m = new Merger();
        output = new StructuredKeyedOutputStream<K, V>(sf, true);
        m.merge(inputs, output, null);
        for(StructuredKeyedInputStream<K,V> kis : inputs) {
            kis.close();
        }
        output.close();
        if (!bf.delete()) {
            throw new IOException("Failed to delete blocked file "
                    + bf.getPath());
        }
    }
    
    private SortedRegion writeSortedRecords(StructuredKeyedInputStream<K,V> input, 
            SortedRegion cr, 
            List<SortedRegion> regions, 
            List<Record<K,V>> recs, 
            StructuredKeyedOutputStream<K,V> output) throws IOException {
        //
        // We've exceeded the buffer size, so sort this block and write
        // it to the output file.
        long pos = input.position();
        cr.setLen(pos - cr.start);
        cr.setSize(recs.size());
        Collections.sort(recs);
        for(Record<K, V> r : recs) {
            output.write(r);
        }
        recs.clear();

        //
        // Save this region.
        regions.add(cr);
        cr = new SortedRegion(pos);
        return cr;
    }

    public class SortedRegion {

        protected int size;

        protected long start;

        protected long len;

        public SortedRegion(long start) {
            this.start = start;
        }

        public void setLen(long len) {
            this.len = len;
        }

        public void setSize(int size) {
            this.size = size;
        }
        
        public String toString() {
            return String.format("size: %d start: %d len: %d", size, start, len);
        }
    }
    
    public static void main(String[] args) throws Exception {
        //
        // Use the labs format logging.
        Logger rl = Logger.getLogger("");
        for(Handler h : rl.getHandlers()) {
            h.setFormatter(new SimpleLabsLogFormatter());
        }

        Sorter s = new Sorter(new File(args[0]), 
                new File(args[1]), new File(args[2]));
        s.sort();
    }
}
