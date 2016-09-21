/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.index.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.solr.client.solrj.SolrClient;
import org.roda.core.data.adapter.facet.Facets;
import org.roda.core.data.adapter.filter.Filter;
import org.roda.core.data.adapter.sort.Sorter;
import org.roda.core.data.adapter.sublist.Sublist;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.index.FacetFieldResult;
import org.roda.core.data.v2.index.IndexResult;
import org.roda.core.data.v2.index.IsIndexed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Does search in the index, using the Solr.find() method, and if configured
 * removes duplicate objects (via uuid comparison) thus providing iterator
 * 
 * @author Hélder Silva <hsilva@keep.pt>
 */

public class IterableIndexResult<T extends IsIndexed> implements Iterable<T> {
  private static final Logger LOGGER = LoggerFactory.getLogger(IterableIndexResult.class);

  private static final int PAGE_SIZE = RodaConstants.DEFAULT_PAGINATION_VALUE;

  private SolrClient solrClient;
  private Class<T> returnClass;
  private Filter filter;
  private Sorter sorter;
  private Sublist sublist;
  private Facets facets;

  private boolean removeDuplicates = true;
  private Set<String> uniqueUUIDs = new HashSet<>();
  private IndexResult<T> indexResult = null;
  private List<T> indexResultObjects;
  private int currentObject = 0;
  private int currentObjectInPartialList = 0;
  private long totalObjects = -1;

  public IterableIndexResult(SolrClient solrClient, Class<T> returnClass, Filter filter, Sorter sorter, Facets facets,
    boolean removeDuplicates) {
    this.solrClient = solrClient;
    this.returnClass = returnClass;
    this.filter = filter;
    this.sorter = sorter;
    this.facets = facets;
    this.sublist = new Sublist(0, PAGE_SIZE);
    this.removeDuplicates = removeDuplicates;

    getResults(sublist);
  }

  private void getResults(Sublist sublist) {
    try {
      indexResult = SolrUtils.find(solrClient, returnClass, filter, sorter, sublist, facets);
      if (totalObjects == -1) {
        totalObjects = indexResult.getTotalCount();
      }

      if (removeDuplicates) {
        indexResultObjects = new ArrayList<>();
        for (T obj : indexResult.getResults()) {
          if (!uniqueUUIDs.contains(obj.getUUID())) {
            indexResultObjects.add(obj);
            uniqueUUIDs.add(obj.getUUID());
          } else {
            totalObjects -= 1;
          }
        }
      } else {
        indexResultObjects = indexResult.getResults();
      }
    } catch (GenericException | RequestNotValidException e) {
      // just set index result to null & let iterator return proper values
      indexResult = null;
    }
  }

  public List<FacetFieldResult> getFacetResults() {
    return indexResult != null ? indexResult.getFacetResults() : Collections.emptyList();
  }

  @Override
  public Iterator<T> iterator() {
    return new Iterator<T>() {

      @Override
      public boolean hasNext() {
        return indexResult != null && currentObject < totalObjects;
      }

      @Override
      public T next() {
        T t = indexResultObjects.get(currentObjectInPartialList);
        currentObject += 1;
        currentObjectInPartialList += 1;
        if (LOGGER.isTraceEnabled()) {
          LOGGER.trace("({} of {}) Returning object of class '{}' with id '{}'", currentObject, totalObjects,
            returnClass.getSimpleName(), t.getUUID());
        }

        // see if a new page needs to be obtained
        if (currentObjectInPartialList == indexResultObjects.size()) {
          getResults(sublist.setFirstElementIndex(sublist.getFirstElementIndex() + PAGE_SIZE));
          currentObjectInPartialList = 0;
        }

        return t;
      }
    };
  }

}
