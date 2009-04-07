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

package com.sun.labs.aura.aardvark.dashboard.graphics;

import com.jme.math.FastMath;
import com.sun.labs.aura.aardvark.dashboard.story.Story;

/**
 *
 * @author plamere
 */
public class InteractivePoint extends CPoint {
    Story story;
    private Command[] pokeSet = {
        new CmdJiggle(true),
        new CmdWait(3),
        new CmdJiggle(false),
    };

    private Command[] center = {
        new CmdControl(true),
        new CmdVeryStiff(),
        new CmdMove(0, 0, 45),
        //new CmdRotate(0, FastMath.PI, 0, .5f),
        new CmdWait(.5f),
        new CmdRotate(0, 0, 0),
        new CmdWait(.5f)
    };

    InteractivePoint(Story story, float x, float y, float z) {
        super(x, y, z);
        this.story = story;
        addSet("poke", pokeSet);
        addSet("center", center);
    }

    InteractivePoint(float x, float y, float z) {
        this(null, x, y, z);
    }

    public void init() {

    }

    public void findStories() {
    }

    public void findTags() {
    }

    public void open() {
    }
    
    public void makeCurrent(boolean isCur) {

    }
}
