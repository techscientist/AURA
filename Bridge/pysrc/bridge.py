 ######
 # Copyright 2007-2009 Sun Microsystems, Inc. All Rights Reserved.
 # DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 # 
 # This code is free software; you can redistribute it and/or modify
 # it under the terms of the GNU General Public License version 2
 # only, as published by the Free Software Foundation.
 # 
 # This code is distributed in the hope that it will be useful, but
 # WITHOUT ANY WARRANTY; without even the implied warranty of
 # MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 # General Public License version 2 for more details (a copy is
 # included in the LICENSE file that accompanied this code).
 # 
 # You should have received a copy of the GNU General Public License
 # version 2 along with this work; if not, write to the Free Software
 # Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 # 02110-1301 USA
 # 
 # Please contact Sun Microsystems, Inc., 16 Network Circle, Menlo
 # Park, CA 94025 or visit www.sun.com if you need additional
 # information or have any questions.
 #####


import jpype as J
import os

class AuraBridge():

    def __init__(self, jvm_path=J.getDefaultJVMPath(),
                    classpath_prefix=os.path.join("..", "dist")):
    
	cP = os.path.join(classpath_prefix, "Bridge.jar")
        J.startJVM(jvm_path, "-DauraGroup=live-aura", "-DauraHome=/aura/sitm/db/", \
                            "-DauraPolicy=/aura/sitm/dist/jsk-all.policy", "-Djava.class.path="+cP)

        AuraBridge = J.JClass("com.sun.labs.aura.bridge.AuraBridge")
        self._bridge = AuraBridge()


#############################################
#   Implementation of ItemStore interface   #
#############################################
    def get_item(self, key):
        """Gets an item from the store"""
	return self._bridge.getItem(key)


    def get_user(self, key):
        """Gets a user from the store"""
	return self._bridge.getUser(key)


    def get_items(self, keys):
        """Gets a list of items from the store"""
        if not isinstance(keys, list):
            raise WrongTypeException("keys argument should be a list")

        aL = self._bridge.getItems(_lst_to_jArrayList(keys))
        return _jarraylist_to_lst(aL)


    def get_all(self, itemType):
        """
        Gets all items of a particular type from the store.
        This can be **VERY** long. Consider using get_all_iterator()
        """
        itemType = _assert_type_itemtype(itemType)
        jL = self._bridge.getAll(itemType)
        return _jarraylist_to_lst(jL)


    def get_attention_count(self, attn_config):
        """Gets the attention count for a particular attention configuration"""
        _assert_type_attn_config(attn_config)
        return self._bridge.getAttentionCount(attn_config).longValue()


    def get_attention_since(self, attn_config, timestamp):
        """Gets attentions created since timestamp, given an attention configuration"""
        _assert_type_attn_config(attn_config)
        aL = self._bridge.getAttentionSince(attn_config, J.java.util.Date(timestamp))
        return _jarraylist_to_lst(aL)


    def get_attention_since_count(self, attn_config, timestamp):
        """Gets the attention count created since timestamp for a particular attention configuration"""
        _assert_type_attn_config(attn_config)
        return self._bridge.getAttentionSinceCount(attn_config, J.java.util.Date(timestamp)).longValue()


    def get_last_attention(self, attn_config, count=10):
        """Get the count last attentions given an attention configuration object"""
        _assert_type_attn_config(attn_config)
        return self._bridge.getLastAttention(attn_config, count)


    def get_item_count(self, itemType):
        """Gets the count for an item type"""
        if isinstance(itemType, str):
            itemType = J.JClass("com.sun.labs.aura.datastore.Item$ItemType").valueOf(itemType)
        return self._bridge.getItemCount(itemType)



    ####################################
    #       Iterator functions         #
    ####################################
    def get_all_iterator(self, itemType):
        """Gets an iterator over all items of specified type"""
        itemType = _assert_type_itemtype(itemType)
        it_id = self._bridge.allItemsIteratorInit(itemType)
        return self._iterator_loop(it_id)


    def get_items_added_since_iterator(self, itemType, timestamp):
        """Gets an iterator over all items of specified type added since the specified timestamp"""
        itemType = _assert_type_itemtype(itemType)
        timestamp = J.java.util.Date(timestamp)
        it_id = self._bridge.initGetItemsAddedSinceIterator(itemType, timestamp)
        return self._iterator_loop(it_id)


    def _iterator_loop(self, it_id):
        """Actual iterator loop used by all our iterators"""
        try:
            go = True
            while go:
                nextVal = self._bridge.iteratorNext(it_id)
                if not nextVal is None:
                    yield nextVal
                else:
                    go = False
        finally:
            self._bridge.iteratorClose(it_id)


            

####################################
#        Utility functions         #
####################################
def _jarraylist_to_lst(aL):
    return [aL.get(x) for x in xrange(aL.size())]            

def _lst_to_jarraylist(l):
    aL = J.java.util.ArrayList()
    for k in l:
        aL.add(k)
    return aL





####################################
#     Type assertions functions    #
####################################
def _assert_type_attn_config(attn_config):
    if not isinstance(attn_config, J.JClass("com.sun.labs.aura.datastore.AttentionConfig")):
        raise WrongTypeException("Must use a valid attention config object. Use ClassFactory.new_attention_config()")

def _assert_type_itemtype(itemType):
    iT_class = J.JClass("com.sun.labs.aura.datastore.Item$ItemType")
    if isinstance(itemType, str):
        itemType = iT_class.valueOf(itemType)
    elif not isinstance(itemType, iT_class):
        raise WrongTypeException("Must use valid item type.")
    return itemType




class WrongTypeException(Exception):
    def __init__(self, value):
        self.value = value
    
    def __str__(self):
        return repr(self.value)





class ClassFactory():

    @staticmethod
    def new_attention_config():
        """Returns a new AttentionConfig object"""
        return J.JClass("com.sun.labs.aura.datastore.AttentionConfig")()
