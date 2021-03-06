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

package com.sun.labs.aura.music.wsitm.server;

import com.google.gwt.user.client.rpc.StatusCodeException;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.sun.labs.aura.datastore.Attention;
import com.sun.labs.aura.datastore.Attention.Type;
import com.sun.labs.aura.music.ArtistTag;
import com.sun.labs.aura.music.wsitm.client.WebLib;
import com.sun.labs.aura.music.wsitm.client.items.ArtistDetails;
import com.sun.labs.aura.music.wsitm.client.items.ItemInfo;
import com.sun.labs.aura.music.wsitm.client.MusicSearchInterface;
import com.sun.labs.aura.music.wsitm.client.SearchResults;
import com.sun.labs.aura.music.wsitm.client.items.TagDetails;
import com.sun.labs.aura.music.wsitm.client.WebException;
import com.sun.labs.aura.music.wsitm.client.items.ArtistCompact;
import com.sun.labs.aura.music.wsitm.client.items.ArtistRecommendation;
import com.sun.labs.aura.music.wsitm.client.items.AttentionItem;
import com.sun.labs.aura.music.wsitm.client.items.ListenerDetails;
import com.sun.labs.aura.music.wsitm.client.items.RecsNTagsContainer;
import com.sun.labs.aura.music.wsitm.client.items.ScoredC;
import com.sun.labs.aura.music.wsitm.client.items.ScoredTag;
import com.sun.labs.aura.music.wsitm.client.items.ServerInfoItem;
import com.sun.labs.aura.music.wsitm.client.ui.widget.AbstractSearchWidget.searchTypes;
import com.sun.labs.aura.util.AuraException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 *
 * @author plamere
 */
public class MusicSearchInterfaceImpl extends RemoteServiceServlet 
        implements MusicSearchInterface {

    static int count;
    private DataManager dm;
    private Logger logger = Logger.getLogger("");

    @Override
    public void init(ServletConfig sc) throws WebException {
        try {
            super.init(sc);
            logger.info("Init MSII");
            dm = (DataManager) sc.getServletContext().getAttribute("DataManager");
            logger.info(String.format("dm: %s", dm));
        } catch (ServletException sE) {
            logger.severe("ServletException :: "+WebLib.traceToString(sE));
            throw new WebException(WebException.errorMessages.INIT_ERROR);
        } catch (StatusCodeException sce) {
            logger.severe("StatusCodeException :: "+WebLib.traceToString(sce));
            throw new WebException(WebException.errorMessages.INIT_ERROR);
        } catch (NullPointerException npe) {
            logger.severe("NullPointerException :: "+WebLib.traceToString(npe));
            throw new WebException(WebException.errorMessages.INIT_ERROR);
        }
    }

    @Override
    public SearchResults tagSearch(String searchString, int maxResults) throws WebException {
        logger.info("tagSearch: "+searchString);
        try {
            return dm.tagSearch(searchString, maxResults);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public SearchResults artistSearch(String searchString, int maxResults) throws WebException {
        logger.info("artistSearch: "+searchString);
        try {
            return dm.artistSearch(searchString, maxResults);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        } 
    }

    @Override
    public SearchResults artistSearchByTag(String searchString, int maxResults) 
            throws WebException {
        logger.info("artistSearchByTag: "+searchString);
        try {
            // Make sure the tag has the right header
            if (!searchString.startsWith("artist-tag:")) {
                searchString=ArtistTag.nameToKey(searchString);
            }
            return dm.artistSearchByTag(searchString, maxResults);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }        
    }

    @Override
    public ArtistDetails getArtistDetails(String id, boolean refresh, String simTypeName, String popularity) throws WebException {
        logger.finer("getArtistDetails: "+id);
        try {
            return dm.getArtistDetails(id, false, simTypeName, popularity);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public TagDetails getTagDetails(String tagName, boolean refresh, String simTypeName) throws WebException {
        logger.finer("getTagDetails: "+tagName);
        try {
            if (!tagName.startsWith("artist-tag:"))
                tagName=ArtistTag.nameToKey(tagName);
            return dm.getTagDetails(tagName, refresh);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public ArrayList<ScoredC<ArtistCompact>> getRepresentativeArtistsOfTag(String tagId) throws WebException {
        logger.finer("getRepArtists for tag: "+tagId);
        try {
            return dm.getRepresentativeArtistsOfTag(tagId);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }
    
    @Override
    public ItemInfo[] getCommonTags(String artistID1, String artistID2, int num, String simType) 
            throws WebException {
        logger.finer("getCommonTags for "+artistID1+" and "+artistID2+" (sim:"+simType+")");
        try {
            return dm.getCommonTags(artistID1, artistID2, num, simType);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    /**
     * Gets the combination tag cloud for the two artists. 
     * @param artistID1
     * @param artistID2
     * @param num
     * @param simType
     * @return
     * @throws com.sun.labs.aura.music.wsitm.client.WebException
     */
    @Override
    public ItemInfo[] getComboTagCloud(String artistID1, String artistID2, int num, String simType)
            throws WebException {
        logger.finer("getCommonTags for "+artistID1+" and "+artistID2);
        try {
            ItemInfo[] a1Tags = normalize(dm.getDistinctiveTags(artistID1, num));
            ItemInfo[] combo =  normalize(dm.getCommonTags(artistID1, artistID2, num / 2, simType));
            ItemInfo[] a2Tags = normalize(dm.getDistinctiveTags(artistID2, num));

            Set<String> commonNames = getNameSet(combo);
            List<ItemInfo> infos = new ArrayList<ItemInfo>();

            List<ItemInfo> head = negative(getTopUniqueInfo(a1Tags, commonNames, num /2));
            infos.addAll(head);

            combo = ItemInfo.shuffle(combo);
            for (ItemInfo ii : combo) {
                infos.add(ii);
            }
            
            List<ItemInfo> tail = negative(getTopUniqueInfo(a2Tags, commonNames, num /2));
            Collections.reverse(tail);
            infos.addAll(tail);

            return infos.toArray(new ItemInfo[0]);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    private List<ItemInfo> getTopUniqueInfo(ItemInfo[] infos, Set<String> names, int maxSize) {
        Arrays.sort(infos, ItemInfo.getScoreSorter());

        List<ItemInfo> list = new ArrayList<ItemInfo>();
        for (ItemInfo ii : infos) {
            if (!names.contains(ii.getItemName())) {
                list.add(ii);
            }
        }
        if (list.size() > maxSize) {
            list = list.subList(0, maxSize);
        }
        return list;
    }

    private ItemInfo[] normalize(ItemInfo[] itemInfo) {
        ItemInfo[] retItemInfo = new ItemInfo[itemInfo.length];
        double max = findMax(itemInfo);
        for (int i = 0; i < itemInfo.length; i++) {
            retItemInfo[i] = new ItemInfo(itemInfo[i].getId(), itemInfo[i].getItemName(), 
                    itemInfo[i].getScore() / max, itemInfo[i].getPopularity());
        }
        return retItemInfo;
    }

    private List<ItemInfo> negative(List<ItemInfo> infos) {
        List<ItemInfo> retList = new ArrayList<ItemInfo>();
        for (ItemInfo ii : infos) {
            retList.add(new ItemInfo(ii.getId(), ii.getItemName(), 
                    -ii.getScore(), ii.getPopularity()));
        }
        return retList;
    }
    
    private double findMax(ItemInfo[] itemInfos) {
        double max = -Double.MAX_VALUE;

        for (ItemInfo ii : itemInfos) {
            if (ii.getScore() > max) {
                max = ii.getScore();
            }
        }
        return max;
    }

    private Set<String> getNameSet(ItemInfo[] itemInfo) {
        Set<String> set = new HashSet<String>();
        for (int i = 0; i < itemInfo.length; i++) {
            set.add(itemInfo[i].getItemName());
        }
        return set;
    }

    @Override
    public ItemInfo[] getCommonTags(Map<String, ScoredTag> tagMap, String artistID, int num) throws WebException {
        String stringMap = "";
        for (String key : tagMap.keySet()) {
            stringMap += (tagMap.get(key).isSticky() ? "(S)": "")+key+":"+tagMap.get(key).getScore()+",";
        }
        logger.finer("getCommonTags for "+artistID+" and cloud={"+stringMap+"}");
        try {
            return dm.getCommonTags(tagMap, artistID, num);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        try {
            dm.close();
        } catch (AuraException ex) {
            Logger.getLogger(MusicSearchInterfaceImpl.class.getName()).log(Level.SEVERE, null, ex);
        } catch (RemoteException ex) {
            Logger.getLogger(MusicSearchInterfaceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Override
    public ArrayList<ScoredC<String>> getArtistOracle() {
        logger.finer("getArtistOracle");
        return dm.getArtistOracle();
    }
    
    @Override
    public ArrayList<ScoredC<String>> getTagOracle() {
        logger.finer("getTagOracle");
        return dm.getTagOracle();
    }

    @Override
    public ListenerDetails getNonOpenIdLogInDetails(String userKey) throws WebException {
        logger.finer("getNonOpenIdLogInDetails for key:"+userKey);
        try {
            ListenerDetails lD = dm.establishNonOpenIdUserConnection(userKey);
            
            if (lD.isLoggedIn()) {
                HttpSession session = this.getThreadLocalRequest().getSession();
                session.setAttribute(OpenIDServlet.openIdCookieName, lD.getOpenId());
            }
            return lD;
            
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public ListenerDetails getLogInDetails() throws WebException {
        logger.finer("getLogInDetails");
        try {
            ListenerDetails lD = new ListenerDetails();

            HttpSession session = this.getThreadLocalRequest().getSession();
            if (session.getAttribute(OpenIDServlet.openIdCookieName) != null) {
                lD.setOpenId( (String) session.getAttribute(OpenIDServlet.openIdCookieName));
                lD.setUserKey(dm.encryptUserKey(lD.getOpenId()));
            }
            if (session.getAttribute(OpenIDServlet.ATTR_BIRTHDATE) != null) {
                lD.setBirhtDate( (String) session.getAttribute(OpenIDServlet.ATTR_BIRTHDATE));
            }
            if (session.getAttribute(OpenIDServlet.ATTR_COUNTRY) != null) {
                lD.setCountry( (String) session.getAttribute(OpenIDServlet.ATTR_COUNTRY));
            }
            if (session.getAttribute(OpenIDServlet.ATTR_EMAIL) != null) {
                lD.setEmail( (String) session.getAttribute(OpenIDServlet.ATTR_EMAIL));
            }
            if (session.getAttribute(OpenIDServlet.ATTR_GENDER) != null) {
                lD.setGender( (String) session.getAttribute(OpenIDServlet.ATTR_GENDER));
            }
            if (session.getAttribute(OpenIDServlet.ATTR_LANGUAGE) != null) {
                lD.setLanguage( (String) session.getAttribute(OpenIDServlet.ATTR_LANGUAGE));
            }
            if (session.getAttribute(OpenIDServlet.ATTR_NICKNAME) != null) {
                lD.setNickName( (String) session.getAttribute(OpenIDServlet.ATTR_NICKNAME));
            }
            if (session.getAttribute(OpenIDServlet.ATTR_NAME) != null) {
                lD.setRealName( (String) session.getAttribute(OpenIDServlet.ATTR_NAME));
            }
            if (session.getAttribute(OpenIDServlet.ATTR_STATE) != null) {
                lD.setState( (String) session.getAttribute(OpenIDServlet.ATTR_STATE));
            }

            if (lD.getOpenId() != null && (lD.getRealName() != null || lD.getNickName() != null)) {
                lD.setIsLoggedIn(true);
                dm.establishUserConnection(lD);
            }
            return lD;

        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    /**
     * Return the current userid from its session
     * @return openid of loggedin user or null if not logged in
     */
    private String getOpenIdFromSession() throws WebException {
        HttpSession session = this.getThreadLocalRequest().getSession();
        String userId = (String) session.getAttribute(OpenIDServlet.openIdCookieName);
        if (userId != null && userId.length() > 0) {
            return userId;
        } else {
            throw new WebException(WebException.errorMessages.MUST_BE_LOGGED_IN);
        }
    }

    @Override
    public void addSearchAttention(String userKey, String target, searchTypes sT, String searchStr) throws WebException {
        try {
            logger.finer("addSearchAttention: userKey:'"+userKey+"' search:"+sT.toString()+":"+searchStr+" target:"+target);
            dm.addSearchAttention(userKey, sT.toString()+":"+searchStr , target);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public void terminateSession() {
        logger.finer("terminateSession");
        HttpSession session = this.getThreadLocalRequest().getSession();
        session.setAttribute(OpenIDServlet.openIdCookieName, null);
    }

    @Override
    public void updateListener(ListenerDetails lD) throws WebException {
        logger.finer("UpdateListener :: "+lD.getOpenId());
        try {
            dm.updateUser(lD);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public void updateUserSongRating(int rating, String artistID) throws WebException {

        String userId = getOpenIdFromSession();
        logger.finer("UpdateUserSongRating :: user:"+userId+" artist:"+artistID+" rating:"+rating);

        try {
            dm.updateUserSongRating(userId, rating, artistID);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public Integer fetchUserSongRating(String artistID) throws WebException {

        String userId = getOpenIdFromSession();
        logger.finer("fetchUserSongRating :: user:"+userId+" artist:"+artistID);

        try {
            return new Integer(dm.fetchUserSongRating(userId, artistID));
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public HashMap<String, Integer> fetchUserSongRating(HashSet<String> artistID) throws WebException {

        String userId = getOpenIdFromSession();
        logger.finer("fetchUserSongRating :: user:"+userId+" and set of artists");

        try {
            return dm.fetchUserSongRating(userId, artistID);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public HashMap<String, String> getSimTypes() {
         return dm.getSimTypes();
    }
    
    @Override
    public HashMap<String, String> getArtistRecommendationTypes() {
        return dm.getArtistRecommendationTypes();
    }

    @Override
    public ItemInfo[] getDistinctiveTags(String artistID, int count) throws WebException {
        try {
            return dm.getDistinctiveTags(artistID, count);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        }
    }

    @Override
    public ArrayList<ScoredC<ArtistCompact>> getSteerableRecommendations(Map<String, ScoredTag> tagMap, String popularity) throws WebException {
        String stringMap = "";
        for (String key : tagMap.keySet()) {
            stringMap += (tagMap.get(key).isSticky() ? "(S)": "")+key+":"+tagMap.get(key).getScore()+",";
        }
        logger.finer("getSteerableRecommendations for cloud:{"+stringMap+"}");
        try {
            ArrayList<ScoredC<ArtistCompact>> aC = dm.getSteerableRecommendations(tagMap, popularity);
            logger.finest("returning "+aC.size()+" recommendations");
            return aC;
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public RecsNTagsContainer getRecommendationsFromString(String tagQuery) throws WebException {
        try {
            logger.finer("getRecsFromString : "+tagQuery);
            return dm.getRecommendationsFromString(tagQuery);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }


    @Override
    public void addUserTagsForItem(String itemId, Set<String> tag) throws WebException {
        
        String userId = getOpenIdFromSession();
        logger.finer("addUserTagForItem :: user:"+userId+", item:"+itemId+", "+tag.size()+" tags");

        try {
            for (String s : tag) {
                dm.addUserTagForItem(userId, itemId, s);
            }
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public void addPlayAttention(String artistId) throws WebException {

        String userId = getOpenIdFromSession();
        logger.finer("addPlayAttention :: user:"+userId+", artist:"+artistId);

        try {
            dm.addItemAttention(userId, artistId, Type.PLAYED);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public void addNotInterestedAttention(String artistId) throws WebException {

        String userId = getOpenIdFromSession();
        logger.finer("addNotInterestedAttention :: user:"+userId+", artist:"+artistId);

        try {
            dm.addItemAttention(userId, artistId, Type.VIEWED);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public ArrayList<AttentionItem<ArtistCompact>> getLastTaggedArtists(int count, boolean returnDistinct) throws WebException {
        return getLastAttentionArtists(count, Type.TAG, true, returnDistinct);
    }

    @Override
    public ArrayList<AttentionItem<ArtistCompact>> getLastRatedArtists(int count, boolean returnDistinct) throws WebException {
        return getLastAttentionArtists(count, Type.RATING, true, returnDistinct);
    }

    @Override
    public ArrayList<AttentionItem<ArtistCompact>> getLastPlayedArtists(int count, boolean returnDistinct) throws WebException {
        return getLastAttentionArtists(count, Type.PLAYED, true, returnDistinct);
    }

    public ArrayList<AttentionItem<ArtistCompact>> getLastAttentionArtists(int count, Type attentionType, boolean fetchUserTags, boolean returnDistinct) throws WebException {

        String userId = getOpenIdFromSession();
        logger.finer("getLastAttentionArtists :: user:"+userId+" attention:"+attentionType.toString());

        try {
            ArrayList<AttentionItem<ArtistCompact>> aI = new ArrayList<AttentionItem<ArtistCompact>>();
            Set<String> artistIds = new HashSet<String>();

            int mult = 1;
            // If we want distinct results, fetch more in case we have to throw away some
            if (returnDistinct) {
                mult = 2;
            }

            List<Attention> att = dm.getLastAttentionData(userId, attentionType, count * mult);
            for (Attention a : att) {
                if (!returnDistinct || (returnDistinct && !artistIds.contains(a.getTargetKey()))) {
                    AttentionItem newAi = new AttentionItem(getArtistCompact(a.getTargetKey()));
                    if (fetchUserTags) {
                        newAi.setTags(fetchUserTagsForItem(a.getTargetKey()));
                    }
                    newAi.setDate(a.getTimeStamp());
                    aI.add(newAi);
                    artistIds.add(a.getTargetKey());
                    
                    if (artistIds.size() == count) {
                        break;
                    }
                }
            }

            // Fetch ratings for all songs
            Map<String,Integer> ratings = dm.fetchUserSongRating(userId, artistIds);
            for (AttentionItem a : aI) {
                ArtistCompact aC = (ArtistCompact)a.getItem();
                if (ratings.containsKey(aC.getId())) {
                    a.setRating(ratings.get(aC.getId()));
                }
            }

            return aI;

        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public HashSet<String> fetchUserTagsForItem(String itemId) throws WebException {
        
        String userId = getOpenIdFromSession();
        logger.finer("fetchUserTagForItem :: user:"+userId+", item:"+itemId);

        try {
            return dm.fetchUserTagsForItem(userId, itemId);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public ArtistCompact getArtistCompact(String artistId) throws WebException {
        logger.finer("getArtistCompact : "+ artistId);
        try {
            return dm.getArtistCompact(artistId);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    public ArtistCompact[] getRandomPopularArtists(int nbr) throws WebException {
        logger.finer("getRandomPopularArtists :: nbr="+nbr);
        try {
            return dm.getRandomPopularArtists(nbr);
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public ItemInfo[] getSimilarTags(String tagId) throws WebException {
        logger.finer("getSimilarTags to '"+tagId+"'");
        try {
            return dm.getSimilarTags(tagId);
         } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }
    
    @Override
    public ArrayList<ArtistRecommendation> getRecommendations(String recTypeName, int cnt) throws WebException {
        String userId = getOpenIdFromSession();
        logger.finer("getLastRatedArtists :: user:"+userId);
        try {
            return dm.getRecommendations(recTypeName, userId, cnt);
         } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public ServerInfoItem getServerInfo() throws WebException {
        logger.finer("getServerInfo");
        try {
            return dm.getServerInfo();
         } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public ArrayList<ScoredC<ArtistCompact>> getSimilarArtists(String id, String simTypeName, String popularity) throws WebException {
        try {
            return dm.getSimilarArtists(id, dm.stringToSimType(simTypeName), dm.stringToPopularity(popularity));
        } catch (AuraException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.severe(WebLib.traceToString(ex));
            throw new WebException(WebException.errorMessages.ITEM_STORE_COMMUNICATION_FAILED, ex);
        }
    }

    @Override
    public void triggerException() throws WebException {
        throw new WebException("Throwing a test exception with included NullPointerException", new NullPointerException());
    }

}
