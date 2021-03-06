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

package com.sun.labs.aura.datastore;

import java.io.Serializable;

/**
 * Describes the constraints placed on Attention during querying.  This class
 * is provided as a parameter to the various methods that retrieve Attention
 * objects from the DataStore.  Any value that is set in this class is
 * considered to be a required parameter for looking up the attention.
 */
public class AttentionConfig implements Serializable {
    protected String sourceKey = null;
    protected String targetKey = null;
    protected Attention.Type type = null;
    protected String stringVal = null;
    protected Long numberVal = null;

    public AttentionConfig() {
        
    }

    /**
     * Gets the constraint on the source key
     * 
     * @return the required source key
     */
    public String getSourceKey() {
        return sourceKey;
    }

    /**
     * Sets the constraint on the source key
     * 
     * @param sourceKey the source key to require
     */
    public void setSourceKey(String sourceKey) {
        if (sourceKey != null && sourceKey.length() > 0) {
            this.sourceKey = sourceKey;
        }
    }

    /**
     * Gets the constraint on the target key
     * 
     * @return the required target key
     */
    public String getTargetKey() {
        return targetKey;
    }

    /**
     * Sets the constraint on the target key
     * 
     * @param targetKey the target key to require
     */
    public void setTargetKey(String targetKey) {
        if (targetKey != null && targetKey.length() > 0) {
            this.targetKey = targetKey;
        }
    }

    /**
     * Gets the constraint on the attention type
     * 
     * @return the required type of attention
     */
    public Attention.Type getType() {
        return type;
    }

    /**
     * Sets the constraint on the attention type
     * 
     * @param type the type of attention to require
     */
    public void setType(Attention.Type type) {
        this.type = type;
    }

    /**
     * Gets the constraint on the attention's string value
     * 
     * @return the required string value
     */
    public String getStringVal() {
        return stringVal;
    }

    /**
     * Sets the contraint on the attention's string value
     * 
     * @param stringVal the string value to require
     */
    public void setStringVal(String stringVal) {
        if (stringVal != null && stringVal.length() > 0) {
            this.stringVal = stringVal;
        }
    }

    /**
     * Gets the constraint on the attention's numeric value
     * 
     * @return the required numeric value
     */
    public Long getNumberVal() {
        return numberVal;
    }

    /**
     * Sets the constraint on the attention's numeric value
     * 
     * @param numberVal the numeric value to require
     */
    public void setNumberVal(Long numberVal) {
        this.numberVal = numberVal;
    }

    public boolean isEmpty() {
        if (sourceKey == null &&
                targetKey == null &&
                type == null &&
                stringVal == null &&
                numberVal == null) {
            return true;
        }
        return false;
    }

    public int hashCode() {
        if (isEmpty()) {
            return 0;
        }

        //
        // We'll base the hashcode just on the source and target key for
        // this config if possible (since source and target make up the
        // partitioning key for attentions)

        String codeStr = "";
        if (sourceKey != null) {
            codeStr += sourceKey;
        }
        if (targetKey != null) {
            codeStr += targetKey;
        }
        if (!codeStr.isEmpty()) {
            return codeStr.hashCode();
        }

        //
        // Move on to the other fields if necessary
        if (type != null) {
            return type.ordinal();
        }

        if (numberVal != null) {
            return numberVal.intValue();
        }

        if (stringVal != null) {
            return stringVal.hashCode();
        }

        return 0;
    }

    public boolean equals(Object other) {
        if ((other instanceof AttentionConfig)) {
            return false;
        }
        AttentionConfig o = (AttentionConfig)other;


        if (xorNull(sourceKey, o.sourceKey)) {
            return false;
        } else {
            if (sourceKey != null && !sourceKey.equals(o.sourceKey)) {
                return false;
            }
        }

        if (xorNull(targetKey, o.targetKey)) {
            return false;
        } else {
            if (targetKey != null && !targetKey.equals(o.targetKey)) {
                return false;
            }
        }

        if (xorNull(type, o.type)) {
            return false;
        } else {
            if (type != null && !type.equals(o.type)) {
                return false;
            }
        }

        if (xorNull(numberVal, o.numberVal)) {
            return false;
        } else {
            if (numberVal != null && !numberVal.equals(o.numberVal)) {
                return false;
            }
        }

        if (xorNull(stringVal, o.stringVal)) {
            return false;
        } else {
            if (stringVal != null && !stringVal.equals(o.stringVal)) {
                return false;
            }
        }

        return true;
    }

    protected boolean xorNull(Object o1, Object o2) {
        if ((o1 != null && o2 == null) ||
                (o1 == null && o2 != null)) {
            return true;
        }
        return false;
    }
}
