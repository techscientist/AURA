/*
 * MusicSearchInterfaceImpl.java
 *
 * Created on March 3, 2007, 7:47 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.labs.aura.music.wsitm.server;

import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.sun.labs.aura.music.wsitm.client.SearchResults;
import com.sun.labs.aura.music.wsitm.client.ArtistDetails;
import com.sun.labs.aura.music.wsitm.client.ItemInfo;
import com.sun.labs.aura.music.wsitm.client.MusicSearchInterface;
import com.sun.labs.aura.music.wsitm.client.SearchResults;
import com.sun.labs.aura.music.wsitm.client.TagDetails;
import com.sun.labs.aura.music.wsitm.client.TagTree;
import com.sun.labs.aura.util.AuraException;
import java.rmi.RemoteException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

/**
 *
 * @author plamere
 */
public class MusicSearchInterfaceImpl extends RemoteServiceServlet 
        implements MusicSearchInterface {

    static int count;
    private DataManager dm;
    private Logger logger;

    @Override
    public void init(ServletConfig sc) throws ServletException {
        super.init(sc);
        dm = ServletTools.getDataManager(sc);
        //logger = dm.getLogger();
        //logger.log("_system_", "startup", "");
    }

    public SearchResults tagSearch(String searchString, int maxResults) {
        //logger.log("anon", "tagSearch", searchString);
        return dm.tagSearch(searchString, maxResults);
    }

    public SearchResults artistSearch(String searchString, int maxResults) {
        //logger.log("anon", "artistSearch", searchString);
        return dm.artistSearch(searchString, maxResults);
    }

    public SearchResults artistSearchByTag(String searchString, int maxResults) {
        //logger.log("anon", "artistSearchByTag", searchString);
        return dm.artistSearchByTag(searchString, maxResults);
    }

    public ArtistDetails getArtistDetails(String id, boolean refresh) {
        try {
            //logger.log("anon", "getArtistDetails", id);
            return dm.getArtistDetails(id, refresh);
        } catch (AuraException ex) {
            Logger.getLogger(MusicSearchInterfaceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(MusicSearchInterfaceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            //@todo fix this
            return null;
        }
    }

    public TagDetails getTagDetails(String id, boolean refresh) {
        //logger.log("anon", "getTagDetails", id);
        return dm.getTagDetails(id, refresh);
    }

    public TagTree getTagTree() {
        return dm.getTagTree();
    }
    
    public ItemInfo[] getCommonTags(String artistID1, String artistID2, int num) {
        //logger.log("anon", "getCommonTags", artistID1);
        return dm.getCommonTags(artistID1, artistID2, num);
    }

    public void destroy() {
        super.destroy();
        try {
            //logger.log("_system_", "shutdown", "");
            dm.close();
        } catch (AuraException ex) {
            Logger.getLogger(MusicSearchInterfaceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(MusicSearchInterfaceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}