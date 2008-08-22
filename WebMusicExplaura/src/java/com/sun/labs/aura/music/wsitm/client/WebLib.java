/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.labs.aura.music.wsitm.client;

import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.ClickListener;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.sun.labs.aura.music.wsitm.client.event.DataEmbededClickListener;
import com.sun.labs.aura.music.wsitm.client.event.DualDataEmbededClickListener;
import com.sun.labs.aura.music.wsitm.client.items.ArtistCompact;
import com.sun.labs.aura.music.wsitm.client.items.ArtistDetails;
import com.sun.labs.aura.music.wsitm.client.items.ItemInfo;
import com.sun.labs.aura.music.wsitm.client.items.TagDetails;

/**
 *
 * @author mailletf
 */
public abstract class WebLib {

    public static final String ICON_WAIT = "ajax-bar.gif";

    public enum PLAY_ICON_SIZE {
        SMALL, MEDIUM, BIG
    }

    /**
     * Convert the play button size enum into the actual size in px
     * @param size PLAY_ICON_SIZE enum
     * @return actual size in px
     */
    private static int playIconSizeToInt(PLAY_ICON_SIZE size) {
        if (size == PLAY_ICON_SIZE.SMALL) {
            return 20;
        } else if (size == PLAY_ICON_SIZE.MEDIUM) {
            return 30;
        } else {
            return 40;
        }
    }

    /**
     * Disables the browsers default context menu for the specified element.
     *
     * @see http://groups.google.com/group/Google-Web-Toolkit/browse_thread/thread/73c80118cb781390/3b76ad6a17a6c376
     * @param elem the element whos context menu will be disabled
     */
    public static native void disableContextMenu(Element elem) /*-{
    elem.oncontextmenu=elem.onclick;
    }-*/;

    /**
     * Selection MUST be reenabled when widget is deleted to prevent a memory leak
     * @see Taken from Gwt EXT 1.0
     * @param disable Set to true to disable the selection and to false to reenable it
     */
    public native static void disableTextSelectInternal(Element e, boolean disable)/*-{
    if (disable) {
    e.ondrag = function () { return false; };
    e.onselectstart = function () { return false; };
    } else {
    e.ondrag = null;
    e.onselectstart = null;
    }
    }-*/;

    public static Widget getLastFMListenWidget(final ArtistCompact aD, PLAY_ICON_SIZE size,
            MusicSearchInterfaceAsync musicServer, boolean isLoggedIn, ClickListener cL) {
        int intSize = playIconSizeToInt(size);
        Image image = new Image("play-lastfm-"+intSize+".jpg");
        image.setTitle("Play music like " + aD.getName() + " at last.fm");
        image.addClickListener(new ClickListener() {

            public void onClick(Widget sender) {
                popupSimilarArtistRadio(aD, true);
            }
        });
        if (isLoggedIn) {
            image.addClickListener(new DualDataEmbededClickListener<MusicSearchInterfaceAsync, String>(musicServer, aD.getId()) {

                public void onClick(Widget arg0) {
                    AsyncCallback callback = new AsyncCallback() {

                        public void onSuccess(Object result) {}
                        public void onFailure(Throwable caught) {
                            Window.alert("Unable to add your play attention for artist " + sndData + ". " + caught.toString());
                        }
                    };

                    try {
                        data.addPlayAttention(sndData, callback);
                    } catch (Exception ex) {
                        Window.alert(ex.getMessage());
                    }
                }
            });
        }
        if (cL != null) {
            image.addClickListener(cL);
        }
        return image;
    }

    public static Widget getSpotifyListenWidget(final ArtistCompact aD, PLAY_ICON_SIZE size,
            MusicSearchInterfaceAsync musicServer, boolean isLoggedIn, ClickListener cL) {
        String musicURL = aD.getSpotifyId();
        int intSize = playIconSizeToInt(size);
        if (musicURL != null && !musicURL.equals("")) {
            HTML html = new HTML("<a href=\"" + musicURL + "\" target=\"spotifyFrame\"><img src=\"play-spotify-"+intSize+".jpg\"/></a>");
            html.setTitle("Play " + aD.getName() + " with Spotify");
            if (isLoggedIn) {
                html.addClickListener(new DualDataEmbededClickListener<MusicSearchInterfaceAsync, String>(musicServer, aD.getId()) {

                    public void onClick(Widget arg0) {
                        AsyncCallback callback = new AsyncCallback() {
                            public void onSuccess(Object result) {}
                            public void onFailure(Throwable caught) {
                                Window.alert("Unable to add your play attention for artist " + sndData + ". " + caught.toString());
                            }
                        };

                        try {
                            data.addPlayAttention(sndData, callback);
                        } catch (Exception ex) {
                            Window.alert(ex.getMessage());
                        }
                    }
                });
            }
            if (cL != null) {
                html.addClickListener(cL);
            }
            return html;
        } else {
            return getLastFMListenWidget(aD, size, musicServer, isLoggedIn, cL);
        }
    }

    public static Widget getListenWidget(final TagDetails tagDetails) {
        Image image = new Image("play-icon30.jpg");
        image.setTitle("Play music like " + tagDetails.getName() + " at last.fm");
        image.addClickListener(new ClickListener() {

            public void onClick(Widget sender) {
                popupTagRadio(tagDetails, true);
            }
        });
        return image;
    }

    public static Widget getSimilarArtistRadio(ArtistDetails artist) {
        String embeddedObject = "<object width=\"340\" height=\"123\">" + "<param name=\"movie\" value=\"http://panther1.last.fm/webclient/50/defaultEmbedPlayer.swf\" />" + "<param name=FlashVars value=\"viral=true&lfmMode=radio&amp;radioURL=lastfm://artist/ARTIST_NAME/similarartists&amp;" + "restTitle= ARTIST_NAME’s Similar Artists \" />" + "<param name=\"wmode\" value=\"transparent\" />" + "<embed src=\"http://panther1.last.fm/webclient/50/defaultEmbedPlayer.swf\" width=\"340\" " + "FlashVars=\"viral=true&lfmMode=radio&amp;radioURL=" + "lastfm://artist/ARTIST_NAME/similarartists&amp;restTitle= ARTIST_NAME’s Similar Artists \" height=\"123\" " + "type=\"application/x-shockwave-flash\" wmode=\"transparent\" />" + "</object>";
        embeddedObject = embeddedObject.replaceAll("ARTIST_NAME", artist.getEncodedName());
        return new HTML(embeddedObject);
    }

    public static String getTagRadioLink(String tagName) {
        tagName = tagName.replaceAll("\\s+", "%20");
        String link = "http://www.last.fm/webclient/popup/?radioURL=" + "lastfm://globaltags/TAG_REPLACE_ME/&resourceID=undefined" + "&resourceType=undefined&viral=true";
        return link.replaceAll("TAG_REPLACE_ME", tagName);
    }

    public static void popupSimilarArtistRadio(ArtistCompact artist, boolean useTags) {
        Window.open(WebLib.getSimilarArtistRadioLink(artist, useTags), "lastfm_popup", "width=400,height=170,menubar=no,toolbar=no,directories=no," + "location=no,resizable=no,scrollbars=no,status=no");
    }
    
    public static ItemInfo getBestTag(ArtistCompact artist) {
        ItemInfo tag = null;
        ItemInfo[] tags = artist.getDistinctiveTags();
        if (tags == null && tags.length == 0) {
            tags = artist.getDistinctiveTags();
        }
        if (tags != null && tags.length > 0) {
            tag = tags[0];
        }
        return tag;
    }

    public static String getSimilarArtistRadioLink(ArtistCompact artist, boolean useTags) {
        if (useTags) {
            ItemInfo tag = getBestTag(artist);
            if (tag != null) {
                return getTagRadioLink(tag.getItemName());
            } else {
                return getSimilarArtistRadioLink(artist, false);
            }
        } else {
            String link = "http://www.last.fm/webclient/popup/?radioURL=" + "lastfm://artist/ARTIST_REPLACE_ME/similarartists&resourceID=undefined" + "&resourceType=undefined&viral=true";
            return link.replaceAll("ARTIST_REPLACE_ME", artist.getEncodedName());
        }
    }

    public static void popupTagRadio(TagDetails tagDetails, boolean useTags) {
        Window.open(getTagRadioLink(tagDetails.getName()), "lastfm_popup", "width=400,height=170,menubar=no,toolbar=no,directories=no," + "location=no,resizable=no,scrollbars=no,status=no");
    }

    public static VerticalPanel createSection(String title, Widget widget) {
        return createSection(new HTML("<h2>" + title + "</h2>"), widget);
    }

    public static VerticalPanel createSection(Widget title, Widget widget) {
        VerticalPanel panel = new VerticalPanel();
        panel.add(title);
        panel.add(widget);
        return panel;
    }

    public static String createAnchoredImage(String imageURL, String url, String style) {
        String styleSpec = "";
        if (style != null) {
            styleSpec = "style=\"" + style + "\"";
        }
        return createAnchor("<img class=\"inlineAlbumArt\" " + styleSpec + " src=\"" + imageURL + "\"/>", url);
    }

    /**
     * Creates a link
     * @param text link description
     * @param url link url
     * @return html formated link
     */
    public static String createAnchor(String text, String url) {
            return "<a href=\"" + url + "\" target=\"window1\">" + text + "</a>";
    }

    public static Widget getLoadingBarWidget() {
        FlowPanel panel = new FlowPanel();
        panel.add(new HTML("<img src='" + ICON_WAIT + "'/>"));
        return panel;
    }

    public static Widget getPopularityWidget(String name, double normPopularity, boolean log, String style) {
        return getPopularityWidget(name, normPopularity, 100, log, style);
    }

    /**
     * Build a horizontal bar representing the popularity
     * @param name name of the artist or concept
     * @param normPopularity popularity as a number between 0 and 1
     * @param log plot on a log scale
     * @param style style to apply to the name
     */
    public static Widget getPopularityWidget(String name, double normPopularity, int maxWidth, boolean log, String style) {

        VerticalPanel vPanel = new VerticalPanel();
        Label lbl = new Label(name);
        if (style!=null && !style.equals("")) {
            lbl.addStyleName(style);
        }
        vPanel.add(lbl);
        vPanel.add(getPopularityHisto(normPopularity, log, 15, maxWidth));
        return vPanel;
    }

    public static Widget getSmallPopularityWidget(double normPopularity, boolean log, boolean displayName) {
        return getSmallPopularityWidget(normPopularity, 100, log, displayName);
    }

    /**
     * Get small version of horizontal bar representing the popularity
     * @param normPopularity popularity as a number between 0 and 1
     * @param maxWidth width of the popularity histo if normPopularity=1
     * @param log plot on log scale
     * @return
     */
    public static Widget getSmallPopularityWidget(double normPopularity, int maxWidth, boolean log, boolean displayName) {

        HorizontalPanel hPanel = new HorizontalPanel();
        hPanel.setVerticalAlignment(VerticalPanel.ALIGN_MIDDLE);
        Label lbl = new Label("Popularity: ");
        lbl.setStyleName("recoTags");
        lbl.addStyleName("marginRight");
        lbl.addStyleName("bold");
        if (displayName) {
            hPanel.add(lbl);
        }
        hPanel.add(getPopularityHisto(normPopularity, log, 8, maxWidth));
        return hPanel;
    }

    /**
     * Returns a populatiry histrogram. To get it wrapped with a title, use other
     * utility functions getSmallPopularityWidget() or getPopularityWidget()
     * @param normPopularity popularity as a number between 0 and 1
     * @param log plot on log scale
     * @param height maximum size of 15
     * @param maxWidth
     * @return
     */
    public static HorizontalPanel getPopularityHisto(double normPopularity, boolean log, int height, int maxWidth) {

        if (log) {
            normPopularity = Math.log(normPopularity + 1) / Math.log(2); // get the base 2 log
        }
        int leftWidth = (int) (normPopularity * maxWidth);
        if (leftWidth < 1) {
            leftWidth = 1;
        } else if (leftWidth > maxWidth) {
            leftWidth = maxWidth;
        }
        int rightWidth = maxWidth - leftWidth;

        HorizontalPanel table = new HorizontalPanel();
        table.setWidth(maxWidth+"px");
        table.setBorderWidth(0);
        table.setSpacing(0);

        Widget left = new Label("");
        left.setStyleName("popLeft");
        left.setWidth(leftWidth + "");
        left.setHeight(height+"px");

        Widget right = new Label("");
        right.setStyleName("popRight");
        right.setWidth(rightWidth + "");
        left.setHeight(height+"px");

        table.add(left);
        table.add(right);

        return table;
    }
}
