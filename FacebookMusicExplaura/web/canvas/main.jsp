<%-- 
    Document   : main
    Created on : Mar 3, 2009, 2:49:09 PM
    Author     : ja151348
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c"%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
   "http://www.w3.org/TR/html4/loose.dtd">

<style type="text/css">

.fakeTabCurr {
    border-top: 1px solid #d8dfea;
    border-right: 1px solid #d8dfea;
    border-left: 1px solid #d8dfea;
    border-bottom: 1px solid #ffffff;
    font-size: 13px;
    font-weight: bold;
    display: inline;
    padding-top: 3px;
    padding-right: 11px;
    padding-bottom: 3px;
    padding-left: 11px;
    white-space: nowrap;
}

.fakeTab {
    background-color: #d8dfea;
    color: #3b5998;
    border-top: 1px solid #d8dfea;
    border-right: 1px solid #d8dfea;
    border-left: 1px solid #d8dfea;
    border-bottom-width: 0pt;
    font-size: 13px;
    font-weight: bold;
    display: inline;
    padding-top: 3px;
    padding-right: 11px;
    padding-bottom: 3px;
    padding-left: 11px;
    white-space: nowrap;
}

.fakeTab:hover {
    background-color: #627aad;
    border-top-color: #627aad;
    border-right-color: #627aad;
    border-bottom-color: #627aad;
    border-left-color: #627aad;
    color: #ffffff;
}

.controlBtn {
    border: 1px solid #7f93bc;
    padding: 3px 15px 3px 15px;
    color: #3b5998;
}

.controlBtn:hover {
    color: #ffffff;
    background-color: #3b5998;
}

</style>

<h1>Music Explaura</h1>
<br/>
The Music Explaura looks at the info in the "Favorite Music" section of your
profile.  It tries to identify each band listed by name, then builds a tag
cloud by combining the most distinctive terms that describe each band.
<br/><br/>
<!-- Facebook roundtrip: ${time} -->

<c:if test="${nomusic == true}">
It looks like you don't have any music listed in your profile, so we've
generated a tag cloud based solely on the band Coldplay.
</c:if>
<c:if test="${nomusic == false}">
Looking at your favorite music, we recognized the following bands:
<p>
<c:forEach items="${artists}" var="artist" varStatus="loop">
    ${artist.name}<c:if test="${!loop.last}">,</c:if>
</c:forEach>
</p>
</c:if>
<br/>

<!-- The "tabs" -->
<div style="border-bottom: 1px solid #d8dfea; padding-bottom:3px; padding-left:10px;">
    <span class="fakeTabCurr" id="cloudTab">My Cloud</span>&nbsp;
    <span class="fakeTab" id="compareTab">Compare</span>
</div>

<!-- Get artist time: ${auraTime} -->

<!-- The main display area (below the tabs) -->
<div style="width: 700px; min-height: 200px; padding: 5px;" id="mainSection">
</div>
<br/><br/>

<div style="float:right; clear: right;" id="inviteArea">
</div>
<!-- The add to profile button -->
<div style="float: right; clear: right;" id="addToProfileArea">
</div>

<br/><br/><br/><br/>

<!-- The credits -->
<div style="padding: 3px; font-size: 8px; text-align: center; border: 1px solid #222222">
    <img src="${server}/image/sun_logo.png"/><br/>
    The Music Explaura is developed by <a href="http://research.sun.com/">Sun Labs</a> as part of The AURA Project.<br/>
    Data was used from <a href="http://musicbrainz.org">Musicbrainz</a> and <a href="http://last.fm">Last.fm</a>

</div>

<!-- pre-rendered content to be used in the page -->
<fb:js-string var="friendPicker">
    <form id="friendSelector">
        <table><tr><td>
        Select a friend to compare to:
        </td><td>
        <fb:friend-selector idname="selected_id" />
        </td><td>
        <span class="controlBtn" id="compareGoBtn">Compare</span>
        </td><td>
        <span class="controlBtn" id="seeOtherBtn">See Cloud</span>
        </td></tr></table>
    </form>
    <div id="compareResults">
    </div>
</fb:js-string>

<script type="text/javascript">
<!--
    var server = "${server}";
    var canvasPath = "${server}/canvas";
    var fbSession = "${fbSession}";
    
    var artistIDs = [];
    <c:forEach items="${artists}" var="artist" varStatus="loop">
        artistIDs[${loop.index}] = "${artist.key}";
    </c:forEach>

        //
        // clear out the contents of a div
        function clearDiv(thediv) {
            var kids = thediv.childNodes;
            if (kids != null) {
                for (var i = 0; i < kids.length; i++) {
                    thediv.removeChild(kids[i]);
                }
            }
            thediv.setTextValue("");
        }

        //
        // Creates the DOM that represents a cloud from cloud JSON data
        function getDOMForCloud(cloudData) {
            var cloud = document.createElement("div");
            for (var i=0; i < cloudData.length; i++) {
                var curr = document.createElement("span");
                curr.setTextValue(cloudData[i].name + " ");
                var size = cloudData[i].size;
                var orange = '#f8981d';
                var blue = '#5382a1';
                if (size < 0) {
                    orange = '#fbdcbd';
                    blue = '#baccd8';
                    size = size * -1;
                }
                var fontsize = size + "px";

                if (i % 2 == 0) {

                    curr.setStyle({'fontSize': fontsize, 'color': orange});
                } else {
                    curr.setStyle({'fontSize': fontsize, 'color': blue});
                }
                cloud.appendChild(curr);
            }
            return cloud;
        }

        function displayCloudCallback(data) {
            var thediv = document.getElementById('mainSection');
            clearDiv(thediv);
            //
            // Check for an error
            var status = data.shift();
            if (status.error != null) {
                showDialog("Error", status.error);
                return;
            }
            thediv.appendChild(getDOMForCloud(data));

            //
            // Put in the add-to-profile button if relevant
            var profile = document.getElementById("addToProfileArea");
            profile.setInnerFBML(status.fbml_profile);
        }

        function displayCompareCallback(data) {
            var thediv = document.getElementById('compareResults');
            clearDiv(thediv);
            //
            // Check for an error
            var status = data.shift();
            if (status.error != null) {
                showDialog("Error", status.error);
                return;
            }
            thediv.appendChild(getDOMForCloud(data));
            //
            // Should we add an invite button?
            if (status.isAppUser == "false") {
                var inv = document.getElementById("inviteArea");
                inv.setInnerFBML(status.fbml_invite);
            }
        }

        function showDialog(title, msg) {
            dialog = new Dialog(Dialog.DIALOG_POP).
                showMessage(title, msg);
        }

        function getSelectedFriend() {
            var selector = document.getElementById('friendSelector');
            var selected = selector.serialize().selected_id;
            return selected;
        }

        function switchToLoader(thediv) {
            clearDiv(thediv);
            var loader = document.createElement("img");
            loader.setSrc(server + "/image/loader.gif");
            loader.setStyle({'position': 'relative', 'top': '90px', 'left': '340px'});
            thediv.appendChild(loader);
        }

        function cloudTabClicked() {
            //
            // Switch "selected" tab
            var cloudTab = document.getElementById("cloudTab");
            var compareTab = document.getElementById("compareTab");
            cloudTab.setClassName("fakeTabCurr");
            compareTab.setClassName("fakeTab");

            //
            // Fetch the cloud data
            var main = document.getElementById('mainSection');
            switchToLoader(main);
            fetchAndShowCloud();

            //
            // Clear the invite area
            var inv = document.getElementById("inviteArea");
            clearDiv(inv);
        }

        function compareTabClicked() {
            //
            // Switch "selected" tab
            var cloudTab = document.getElementById("cloudTab");
            var compareTab = document.getElementById("compareTab");
            cloudTab.setClassName("fakeTab");
            compareTab.setClassName("fakeTabCurr");

            //
            // Show the area where you can choose a friend
            var main = document.getElementById('mainSection');
            clearDiv(main);
            main.setInnerFBML(friendPicker);
            var goBtn = document.getElementById('compareGoBtn');
            goBtn.addEventListener('click', fetchAndShowCompare);
            var seeBtn = document.getElementById('seeOtherBtn');
            seeBtn.addEventListener('click', fetchAndShowFriendCloud);

            //
            // Clear the add to profile area
            var profile = document.getElementById("addToProfileArea");
            clearDiv(profile);
        }

        /*
         * Shows a comparison cloud between the logged in user and a selected
         * friend.
         */
        function fetchAndShowCompare() {
            var selected = getSelectedFriend();
            var tgt = document.getElementById('compareResults');
            switchToLoader(tgt);
            
            var ajax = new Ajax();
            ajax.responseType = Ajax.JSON;
            ajax.ondone = displayCompareCallback;
            var query = {"artists" : artistIDs.join(","),
                         "fbSession" : fbSession,
                         "friendUID" : selected};
            ajax.post(canvasPath + "/ajax/getCompareCloud", query);
        }

        /*
         * Shows a cloud for the logged-in user and updates their profile FBML
         */
        function fetchAndShowCloud() {
            var ajax = new Ajax();
            ajax.responseType = Ajax.JSON;
            ajax.ondone = displayCloudCallback;
            var query = {"artists" : artistIDs.join(","),
                         "fbSession" : fbSession};
            ajax.post(canvasPath + "/ajax/updateCloudFromArtistIDs", query);
        }

        /*
         * Shows a cloud (on the compare screen) representing the selected
         * friend's musical tastes
         */
        function fetchAndShowFriendCloud() {
            var selected = getSelectedFriend();
            var tgt = document.getElementById('compareResults');
            switchToLoader(tgt);

            var ajax = new Ajax();
            ajax.responseType = Ajax.JSON;
            ajax.ondone = displayCompareCallback;
            var query = {"fbSession" : fbSession,
                         "friendUID" : selected};
            ajax.post(canvasPath + "/ajax/getOtherCloud", query);
        }

        //
        // Run this when the page loads:
        var main = document.getElementById('mainSection');
        switchToLoader(main);
        fetchAndShowCloud();

        var cloudTab = document.getElementById("cloudTab");
        cloudTab.addEventListener('click', cloudTabClicked);

        var compareTab = document.getElementById("compareTab");
        compareTab.addEventListener('click', compareTabClicked);

//-->
</script>


<!--

<c:forEach var='parameter' items='${paramValues}'>
	<c:out value='${parameter.key}'/> =
	<c:forEach var='value' items='${parameter.value}'>
		<c:out value='${value}'/><br>
	</c:forEach>
</c:forEach>

-->