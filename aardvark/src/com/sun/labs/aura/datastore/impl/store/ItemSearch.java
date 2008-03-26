
package com.sun.labs.aura.datastore.impl.store;

import com.sun.kt.search.FieldFrequency;
import com.sun.kt.search.ResultsFilter;
import com.sun.kt.search.WeightedField;
import com.sun.labs.aura.cluster.Cluster;
import com.sun.labs.aura.datastore.Item;
import com.sun.labs.aura.util.AuraException;
import com.sun.labs.aura.util.Scored;
import java.rmi.RemoteException;
import java.util.List;

/**
 * ItemSearch describes the search methods available for use in the data store.
 */
public interface ItemSearch {
    
    /**
     * Clusters a set of items into k clusters based on the data in the given
     * field.
     * @param keys the keys of the items to cluster
     * @param field the field holding the data that we'll cluster on
     * @param k the number of clusters to return
     * @return a list of <code>k</code> or fewer clusters
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public List<Cluster> cluster(List<String> keys, String field, int k) throws AuraException, RemoteException;

    /**
     * Gets the most frequent values for the named field.
     * @param field the field for which we want the most frequent values
     * @param n the number of most-frequent values to return
     * @param ignoreCase if <code>true</code>, ignore the case of string values
     * when computing the frequencies
     * @return a list of the most frequent values and their associated frequencies.
     * @throws com.sun.labs.aura.util.AuraException
     * @throws java.rmi.RemoteException
     */
    public List<FieldFrequency> getTopValues(String field, int n,
            boolean ignoreCase) throws AuraException, RemoteException;
    
    /**
     * Finds a the n most similar items to the given item.
     * @param key the item that we want to find similar items for
     * @param n the number of similar items to return
     * @param rf a (possibly <code>null</code>) filter to apply to the results
     * retrieved from the data store
     * @return a list of items most similar to the given item, in order from most
     * to least similar.  The similarity of the items is based on 
     * all of the indexed text associated with the item in the data store.
     */
    public List<Scored<Item>> findSimilar(String key, int n, ResultsFilter rf)
            throws AuraException, RemoteException;

    /**
     * Finds the n most-similar items to the given item, based on the data in the 
     * provided field.
     * @param key the item for which we want similar items
     * @param field the name of the field that should be used to find similar
     * items
     * @param n the number of similar items to return
     * @param rf a (possibly <code>null</code>) filter to apply to the results
     * retrieved from the data store
     * @return a list of the items most similar to the given item, in order from
     * most to least similar, based on the 
     * data indexed into the given field.  Note that the returned set may be
     * smaller than the number of items requested!
     */
    public List<Scored<Item>> findSimilar(String key, String field, int n, ResultsFilter rf)
            throws AuraException, RemoteException;
    
    /**
     * Finds a the n most similar items to the given items.
     * @param keys the items that we want to find similar items for
     * @param n the number of similar items to return
     * @return a list of items most similar to the given item, in order from most
     * @param rf a (possibly <code>null</code>) filter to apply to the results
     * retrieved from the data store
     * to least similar.  The similarity of the items is based on 
     * all of the indexed text associated with the item in the data store.
     */
    public List<Scored<Item>> findSimilar(List<String> keys, int n, ResultsFilter rf)
            throws AuraException, RemoteException;

    /**
     * Finds the n most-similar items to the given item, based on the data in the 
     * provided field.
     * @param key the item for which we want similar items
     * @param field the name of the field that should be used to find similar
     * items
     * @param n the number of similar items to return
     * @param rf a (possibly <code>null</code>) filter to apply to the results
     * retrieved from the data store
     * @return a list of the items most similar to the given item, in order from
     * most to least similar, based on the 
     * data indexed into the given field.  Note that the returned set may be
     * smaller than the number of items requested!
     */
    public List<Scored<Item>> findSimilar(List<String> keys, String field, int n, ResultsFilter rf)
            throws AuraException, RemoteException;
    
    /**
     * Finds the n most-similar items to the given items, based on a combination
     * of the data held in the provided fields.
     * @param key the item for which we want similar items
     * @param fields the fields (and associated weights) that we should use to 
     * compute the similarity between items.
     * @param n the number of similar items to return
     * @param rf a (possibly <code>null</code>) filter to apply to the results
     * retrieved from the data store
     * @return a list of the items most similar to the given item, in order from 
     * most to least similar, based on the data
     * in the provided fields.   Note that the returned set may be
     * smaller than the number of items requested!
     */
    public List<Scored<Item>> findSimilar(String key, WeightedField[] fields, int n, ResultsFilter rf)
            throws AuraException, RemoteException;
    
    /**
     * Runs a query against the map data and returns the top n results.
     * @param query the query to run
     * @param n the number of results to return
     * @param rf a (possibly <code>null</code>) filter to apply to the results
     * retrieved from the data store
     * @return the top results for the query, orderd by score
     */
    public List<Scored<Item>> query(String query, int n, ResultsFilter rf)
            throws AuraException, RemoteException;

    /**
     * Runs a query against the map data and returns the top n results.
     * @param query the query to run
     * @param sort the sorting specification to use to sort the results
     * @param n the number of results to return
     * @param rf a (possibly <code>null</code>) filter to apply to the results
     * retrieved from the data store
     * @return the top results for the query, orderd by the given sort
     * criteria.
     */
    public List<Scored<Item>> query(String query, String sort, int n, ResultsFilter rf)
            throws AuraException, RemoteException;
    
}
