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

package com.sun.labs.aura.music.wsitm.agentspecific.impl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;
import org.cobogw.gwt.user.client.ui.RoundedLinePanel;
import org.cobogw.gwt.user.client.ui.RoundedPanel;

/**
 *
 * @author mailletf
 */
public class CssDefsImpl {

    public static final CssDefsImpl impl = GWT.create(CssDefsImpl.class);

    public void setRoundedPopupWidth(PopupPanel rP, int width) {}
    public String getLastFmRadioPrefix() { return ""; }
    public RoundedPanel createRoundedPanel(Widget w, int corners, int cornerHeight) {
        return new RoundedPanel(w, corners, cornerHeight);
    }
    public RoundedLinePanel createRoundedLinePanel(Widget w, int corners, int cornerHeight) {
        return new RoundedLinePanel(w, corners, cornerHeight);
    }

}
