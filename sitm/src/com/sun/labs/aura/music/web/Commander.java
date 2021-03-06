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

package com.sun.labs.aura.music.web;

import com.sun.labs.aura.music.util.ExpiringLRUCache;
import com.sun.labs.aura.music.web.lastfm.LastFM2Impl;
import com.sun.labs.aura.util.AuraException;
import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class Commander {

    private String name;
    private String prefix;
    private String suffix;
    private boolean trace;
    private boolean traceSends;
    private boolean log;
    private final SLong lastCommandTime = new SLong();
    private long minimumCommandPeriod = 0L;
    private static ThreadLocal<DocumentBuilder> builder = new ThreadLocal<DocumentBuilder>() {
        @Override
        protected DocumentBuilder initialValue() {
            try {
                return DocumentBuilderFactory.newInstance().newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                System.out.println("Can't load parser " + e.getMessage());
            }
            return null;
        }
    };
    private PrintStream logFile;
    private int commandsSent = 0;
    private int cacheHits = 0;
    private int timeout = -1;
    private int tryCount = 5;
    private final int DEFAULT_TIMEOUT = 60 * 1000;

    private final ExpiringLRUCache cache;
    private boolean useCache;
    private final int CACHE_SIZE = 500;
    private final int CACHE_SECS2LIVE = 60 * 60;

    private boolean synchronizeSendCommand = true;

    public Commander(String name, String prefix, String suffix) throws IOException {
        this(name, prefix, suffix, false);
    }

    public Commander(String name, String prefix, String suffix, boolean useCache) throws IOException {
        this.name = name;
        this.prefix = prefix;
        this.suffix = suffix;
        trace = Boolean.getBoolean("trace");
        traceSends = Boolean.getBoolean("traceSends");
        log = Boolean.getBoolean("log");

        if (trace) {
            System.out.println("Tracing is on");
        }
        if (log) {
            String logname = name + ".log";
            try {
                logFile = new PrintStream(logname);
            } catch (IOException e) {
                System.err.println("Can't open " + logname);
            }
        }
        setTimeout(DEFAULT_TIMEOUT);

        this.useCache = useCache;
        cache = new ExpiringLRUCache(CACHE_SIZE, CACHE_SECS2LIVE);

    }

    public void setSynchronizeSendCommand(boolean sync) {
        this.synchronizeSendCommand = sync;
    }

    public void setTraceSends(boolean traceSends) {
        this.traceSends = traceSends;
    }

    public void setTrace(boolean trace) {
        this.trace = trace;
    }

    public void setRetries(int retries) {
        tryCount = retries + 1;
        if (tryCount < 1) {
            tryCount = 1;
        }
    }

    public void showStats() {
        System.out.printf("Commands sent to %s: %d\n", name, commandsSent);
        System.out.printf("Cache hits on %s: %d\n", name, cacheHits);
    }

    public String encode(String name) {
        try {
            String encodedName = URLEncoder.encode(name, "UTF-8");
            return encodedName;
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    /**
     * Sets the minimum period between consecutive commands
     * @param minPeriod the minimum period.
     */
    public synchronized void setMinimumCommandPeriod(long minPeriod) {
        minimumCommandPeriod = minPeriod;
    }

    // BUG fix this threading model
    public Document sendCommand(String command) throws IOException {
        Document document = null;

        if (useCache) {
            synchronized (cache) {
                document = (Document) cache.sget(command);
                if (document != null) {
                    cacheHits++;
                    return document;
                }
            }
        }

        InputStream is = sendCommandRaw(command);
        commandsSent++;

        try {
            document = builder.get().parse(is);
        } catch (SAXException e) {
            throw new IOException("SAX Parse Error " + e);
        } finally {
            is.close();
        }

        if (trace) {
            dumpDocument(document);
        }
        
        if (useCache) {
            synchronized(cache) {
                cache.sput(command, document);
            }
        }
        return document;
    }

    public InputStream sendCommandRaw(String command) throws IOException {
        if (synchronizeSendCommand) {
            synchronized (lastCommandTime) {
                try {
                    long curGap = System.currentTimeMillis() - lastCommandTime.getVal();
                    long delayTime = minimumCommandPeriod - curGap;
                    delay(delayTime);

                    InputStream is = doSendCommandRaw(command);

                    return is;
                    
                } finally {
                    // Make sure we set the new lastCommandTime even if we
                    // are throwing an exception
                    lastCommandTime.setVal(System.currentTimeMillis());
                }
            }
        } else {
            return doSendCommandRaw(command);
        }
    }

    private InputStream doSendCommandRaw(String command) throws IOException {
        try {
            String fullCommand = prefix + command + fixSuffix(command, suffix);

            URI uri = new URI(fullCommand);
            URL url = uri.toURL();

            if (trace || traceSends) {
                System.out.println("Sending-->     " + url);
            }
            if (logFile != null) {
                logFile.println("Sending-->     " + url);
            }

            InputStream is = null;
            for (int i = 0; i < tryCount; i++) {
                URLConnection urc = null;
                try {
                    urc = url.openConnection();

                    if (getTimeout() != -1) {
                        urc.setReadTimeout(getTimeout());
                        urc.setConnectTimeout(getTimeout());
                    }
                    is = new BufferedInputStream(urc.getInputStream());
                    break;
                } catch (FileNotFoundException e) {
                    throw e;
                } catch (IOException e) {
                    // If we're getting a 400 response code, we want to capture more information
                    // in case it's the lastfm api telling us it can't find what we're looking for
                    if (fullCommand.startsWith("http")) {
                        try {
                            Document doc = builder.get().parse(new BufferedInputStream(((HttpURLConnection) urc).getErrorStream()));
                            throw new HttpBadRequestException(e.getMessage(), ((HttpURLConnection)urc).getResponseCode(), doc);
                        } catch (SAXException ex) {
                            throw e;
                        }
                    } else {
                        System.out.println(name + " Error: " + e + " cmd: " + command);
                    }
                }
            }

            if (is == null) {
                System.out.println(name + " retry failure  cmd: " + url);
                throw new IOException("Can't send command");
            }
            return is;
        } catch (URISyntaxException ex) {
            throw new IOException("bad uri " + ex);
        }
    }

    // the suffix maybe a param that needs to start with & or ? depending
    // on whether or not this is the only parameter for the command. If the suffix
    // starts with a '&' then it is assumed to be a param, if the command doesn't have
    // any params (i.e. there's no  '?' in the command), then we replace the '&' with a '?'
    private String fixSuffix(String command, String suffix) {
        if (suffix.startsWith("&")) {
            if (command.indexOf("?") == -1) {
                suffix = suffix.replaceFirst("\\&", "?");
            }
        }
        return suffix;

    }

    private void delay(long time) {
        if (time < 0) {
            return;
        } else {
            try {
                Thread.sleep(time);
            } catch (InterruptedException ie) {
            }
        }
    }

    /**
     * A debuging method ... dumps a domdocument to
     * standard out
     */
    public static void dumpDocument(Document document) {
        try {
            // Prepare the DOM document for writing
            Source source = new DOMSource(document);
            Result result = new StreamResult(System.out);

            // Write the DOM document to the file
            // Get Transformer
            Transformer xformer =
                    TransformerFactory.newInstance().newTransformer();
            // Write to a file

            xformer.setOutputProperty(OutputKeys.INDENT, "yes");
            xformer.setOutputProperty(OutputKeys.METHOD, "xml");
            xformer.setOutputProperty(
                    "{http://xml.apache.org/xalan}indent-amount", "4");

            xformer.transform(source, result);
        } catch (TransformerConfigurationException e) {
            System.out.println("TransformerConfigurationException: " + e);
        } catch (TransformerException e) {
            System.out.println("TransformerException: " + e);
        }
    }

    public static String convertToString(Document document) {
        StringWriter sw = new StringWriter();
        try {
            // Prepare the DOM document for writing
            Source source = new DOMSource(document);
            Result result = new StreamResult(sw);

            // Write the DOM document to the file
            // Get Transformer
            Transformer xformer =
                    TransformerFactory.newInstance().newTransformer();
            // Write to a file

            xformer.setOutputProperty(OutputKeys.INDENT, "yes");
            xformer.setOutputProperty(OutputKeys.METHOD, "xml");
            xformer.setOutputProperty(
                    "{http://xml.apache.org/xalan}indent-amount", "4");

            xformer.transform(source, result);
        } catch (TransformerConfigurationException e) {
            System.out.println("TransformerConfigurationException: " + e);
        } catch (TransformerException e) {
            System.out.println("TransformerException: " + e);
        }
        return sw.toString();
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    private class SLong {
        
        long longVal;

        public void setVal(long newVal) {
            longVal = newVal;
        }

        public long getVal() {
            return longVal;
        }
    }

    
    public static void main(String[] args) throws IOException, AuraException {

        Commander commander = new Commander("last.fm2", "http://ws.audioscrobbler.com/2.0/", "&api_key="+new LastFM2Impl().getAPIKey(), true);

        try {
            commander.sendCommand("?method=track.gettoptags&artist=Jethro+Tull&track=One+for+John+Gee+%28B+side+of+%27Song+for+Jeffrey%27+single%29");
        } catch (IOException io) {
            if (io instanceof HttpBadRequestException) {
                System.out.println(((HttpBadRequestException)io).getLastFmErrorMessage());
            }
        }
    }

}
