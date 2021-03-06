/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.client.common.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.index.IsIndexed;
import org.roda.core.data.v2.index.facet.FacetParameter;
import org.roda.core.data.v2.index.facet.Facets;
import org.roda.core.data.v2.index.filter.BasicSearchFilterParameter;
import org.roda.core.data.v2.index.filter.Filter;
import org.roda.core.data.v2.index.filter.FilterParameter;
import org.roda.core.data.v2.index.filter.OrFiltersParameters;
import org.roda.core.data.v2.index.filter.SimpleFilterParameter;
import org.roda.core.data.v2.index.select.SelectedItems;
import org.roda.core.data.v2.index.select.SelectedItemsList;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.AIPState;
import org.roda.core.data.v2.ip.File;
import org.roda.core.data.v2.ip.Representation;
import org.roda.wui.client.common.LastSelectedItemsSingleton;
import org.roda.wui.client.common.actions.AipActions;
import org.roda.wui.client.common.actions.FileActions;
import org.roda.wui.client.common.actions.RepresentationActions;
import org.roda.wui.client.common.lists.AIPList;
import org.roda.wui.client.common.lists.RepresentationList;
import org.roda.wui.client.common.lists.SearchFileList;
import org.roda.wui.client.common.lists.pagination.ListSelectionUtils;
import org.roda.wui.common.client.tools.FacetUtils;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import config.i18n.client.ClientMessages;

public class MainSearch extends Composite {

  private static final ClientMessages messages = GWT.create(ClientMessages.class);

  interface MyUiBinder extends UiBinder<Widget, MainSearch> {
  }

  private static MyUiBinder uiBinder = GWT.create(MyUiBinder.class);

  private static final Filter DEFAULT_FILTER_AIP = new Filter(
    new BasicSearchFilterParameter(RodaConstants.AIP_SEARCH, "*"));
  private static final Filter DEFAULT_FILTER_REPRESENTATIONS = new Filter(
    new BasicSearchFilterParameter(RodaConstants.REPRESENTATION_SEARCH, "*"));
  private static final Filter DEFAULT_FILTER_FILES = new Filter(
    new BasicSearchFilterParameter(RodaConstants.FILE_SEARCH, "*"));

  Filter filterAips;
  Filter filterRepresentations;
  Filter filterFiles;

  @UiField(provided = true)
  SearchPanel searchPanel;

  @UiField
  FlowPanel searchResultPanel;

  AIPList itemsSearchResultPanel;
  RepresentationList representationsSearchResultPanel;
  SearchFileList filesSearchResultPanel;

  AdvancedSearchFieldsPanel itemsSearchAdvancedFieldsPanel;
  AdvancedSearchFieldsPanel filesSearchAdvancedFieldsPanel;
  AdvancedSearchFieldsPanel representationsSearchAdvancedFieldsPanel;

  boolean justActive = true;
  boolean itemsSelectable = true;
  boolean representationsSelectable = true;
  boolean filesSelectable = true;
  String selectedItem = AIP.class.getName();

  FlowPanel itemsFacets;
  Map<FacetParameter, FlowPanel> itemsFacetsMap;
  FlowPanel representationsFacets;
  Map<FacetParameter, FlowPanel> representationsFacetsMap;
  FlowPanel filesFacets;
  Map<FacetParameter, FlowPanel> filesFacetsMap;

  // state
  final String parentAipId;
  final AIPState parentAipState;

  public MainSearch(boolean justActive, boolean itemsSelectable, boolean representationsSelectable,
    boolean filesSelectable, FlowPanel itemsFacets, Map<FacetParameter, FlowPanel> itemsFacetsMap,
    FlowPanel representationsFacets, Map<FacetParameter, FlowPanel> representationsFacetsMap, FlowPanel filesFacets,
    Map<FacetParameter, FlowPanel> filesFacetsMap, String parentAipId, AIPState parentAipState) {
    this.justActive = justActive;
    this.itemsSelectable = itemsSelectable;
    this.representationsSelectable = representationsSelectable;
    this.filesSelectable = filesSelectable;

    this.itemsFacets = itemsFacets;
    this.itemsFacetsMap = itemsFacetsMap;

    this.representationsFacets = representationsFacets;
    this.representationsFacetsMap = representationsFacetsMap;

    this.filesFacets = filesFacets;
    this.filesFacetsMap = filesFacetsMap;

    this.parentAipId = parentAipId;
    this.parentAipState = parentAipState;

    ValueChangeHandler<Integer> searchAdvancedFieldsPanelHandler = new ValueChangeHandler<Integer>() {
      @Override
      public void onValueChange(ValueChangeEvent<Integer> event) {
        searchPanel.setSearchAdvancedGoEnabled(event.getValue() == 0 ? false : true);
      }
    };

    itemsSearchAdvancedFieldsPanel = new AdvancedSearchFieldsPanel(RodaConstants.SEARCH_ITEMS);
    representationsSearchAdvancedFieldsPanel = new AdvancedSearchFieldsPanel(RodaConstants.SEARCH_REPRESENTATIONS);
    filesSearchAdvancedFieldsPanel = new AdvancedSearchFieldsPanel(RodaConstants.SEARCH_FILES);

    itemsSearchAdvancedFieldsPanel.addValueChangeHandler(searchAdvancedFieldsPanelHandler);
    representationsSearchAdvancedFieldsPanel.addValueChangeHandler(searchAdvancedFieldsPanelHandler);
    filesSearchAdvancedFieldsPanel.addValueChangeHandler(searchAdvancedFieldsPanelHandler);

    defaultFilters();

    searchPanel = new SearchPanel(filterAips, RodaConstants.AIP_SEARCH, true, messages.searchPlaceHolder(), true, true,
      false);

    initWidget(uiBinder.createAndBindUi(this));

    searchPanel.setDropdownLabel(messages.searchListBoxItems());
    searchPanel.addDropdownItem(messages.searchListBoxItems(), RodaConstants.SEARCH_ITEMS);
    searchPanel.addDropdownItem(messages.searchListBoxRepresentations(), RodaConstants.SEARCH_REPRESENTATIONS);
    searchPanel.addDropdownItem(messages.searchListBoxFiles(), RodaConstants.SEARCH_FILES);

    searchPanel.addValueChangeHandler(new ValueChangeHandler<String>() {

      @Override
      public void onValueChange(ValueChangeEvent<String> event) {
        search();
      }
    });

    searchPanel.addDropdownPopupStyleName("searchInputListBoxPopup");

    // handler aqui
    searchPanel.addSearchAdvancedFieldAddHandler(new ClickHandler() {

      @Override
      public void onClick(ClickEvent event) {
        if (selectedItem.equals(Representation.class.getName())) {
          representationsSearchAdvancedFieldsPanel.addSearchFieldPanel();
        } else if (selectedItem.equals(File.class.getName())) {
          filesSearchAdvancedFieldsPanel.addSearchFieldPanel();
        } else {
          itemsSearchAdvancedFieldsPanel.addSearchFieldPanel();
        }
      }
    });
  }

  public boolean isJustActive() {
    return justActive;
  }

  public void showSearchAdvancedFieldsPanel() {
    if (itemsSearchResultPanel == null) {
      createItemsSearchResultPanel();
    }

    searchPanel.setVariables(filterAips, RodaConstants.AIP_SEARCH, true, itemsSearchResultPanel,
      itemsSearchAdvancedFieldsPanel);
    searchPanel.setSearchAdvancedFieldOptionsAddVisible(true);
    searchPanel.setSearchAdvancedGoEnabled(itemsSearchAdvancedFieldsPanel.getWidgetCount() == 0 ? false : true);

    searchResultPanel.clear();
    searchResultPanel.add(itemsSearchResultPanel);
    selectedItem = AIP.class.getName();

    itemsFacets.setVisible(true);
    representationsFacets.setVisible(false);
    filesFacets.setVisible(false);
  }

  public void showRepresentationsSearchAdvancedFieldsPanel() {
    if (representationsSearchResultPanel == null) {
      createRepresentationsSearchResultPanel();
    }

    searchPanel.setVariables(filterRepresentations, RodaConstants.REPRESENTATION_SEARCH, true,
      representationsSearchResultPanel, representationsSearchAdvancedFieldsPanel);
    searchPanel.setSearchAdvancedFieldOptionsAddVisible(true);
    searchPanel
      .setSearchAdvancedGoEnabled(representationsSearchAdvancedFieldsPanel.getWidgetCount() == 0 ? false : true);

    searchResultPanel.clear();
    searchResultPanel.add(representationsSearchResultPanel);
    selectedItem = Representation.class.getName();

    itemsFacets.setVisible(false);
    representationsFacets.setVisible(true);
    filesFacets.setVisible(false);
  }

  public void showFilesSearchAdvancedFieldsPanel() {
    if (filesSearchResultPanel == null) {
      createFilesSearchResultPanel();
    }

    searchPanel.setVariables(filterFiles, RodaConstants.FILE_SEARCH, true, filesSearchResultPanel,
      filesSearchAdvancedFieldsPanel);
    searchPanel.setSearchAdvancedFieldOptionsAddVisible(true);
    searchPanel.setSearchAdvancedGoEnabled(filesSearchAdvancedFieldsPanel.getWidgetCount() == 0 ? false : true);

    searchResultPanel.clear();
    searchResultPanel.add(filesSearchResultPanel);
    selectedItem = File.class.getName();

    itemsFacets.setVisible(false);
    representationsFacets.setVisible(false);
    filesFacets.setVisible(true);
  }

  private void createItemsSearchResultPanel() {
    Facets facets = new Facets(itemsFacetsMap.keySet());
    itemsSearchResultPanel = new AIPList(filterAips, justActive, facets, messages.searchResults(), itemsSelectable);

    Map<String, FlowPanel> facetPanels = new HashMap<>();
    for (Entry<FacetParameter, FlowPanel> entry : itemsFacetsMap.entrySet()) {
      facetPanels.put(entry.getKey().getName(), entry.getValue());
    }

    FacetUtils.bindFacets(itemsSearchResultPanel, facetPanels);
    ListSelectionUtils.bindBrowseOpener(itemsSearchResultPanel);
    LastSelectedItemsSingleton.getInstance().setSelectedJustActive(justActive);
    itemsSearchResultPanel.setActionable(AipActions.get(parentAipId, parentAipState));
  }

  private void createRepresentationsSearchResultPanel() {
    Facets facets = new Facets(representationsFacetsMap.keySet());
    representationsSearchResultPanel = new RepresentationList(filterRepresentations, justActive, facets,
      messages.searchResults(), representationsSelectable);

    Map<String, FlowPanel> facetPanels = new HashMap<>();
    for (Entry<FacetParameter, FlowPanel> entry : representationsFacetsMap.entrySet()) {
      facetPanels.put(entry.getKey().getName(), entry.getValue());
    }

    FacetUtils.bindFacets(representationsSearchResultPanel, facetPanels);
    ListSelectionUtils.bindBrowseOpener(representationsSearchResultPanel);
    representationsSearchResultPanel.setActionable(RepresentationActions.get());
  }

  private void createFilesSearchResultPanel() {
    Facets facets = new Facets(filesFacetsMap.keySet());
    boolean showFilesPath = false;
    filesSearchResultPanel = new SearchFileList(filterFiles, justActive, facets, messages.searchResults(),
      filesSelectable, showFilesPath);

    Map<String, FlowPanel> facetPanels = new HashMap<>();
    for (Entry<FacetParameter, FlowPanel> entry : filesFacetsMap.entrySet()) {
      facetPanels.put(entry.getKey().getName(), entry.getValue());
    }

    FacetUtils.bindFacets(filesSearchResultPanel, facetPanels);
    ListSelectionUtils.bindBrowseOpener(filesSearchResultPanel);
    LastSelectedItemsSingleton.getInstance().setSelectedJustActive(justActive);
    filesSearchResultPanel.setActionable(FileActions.get());
  }

  public SelectedItems<? extends IsIndexed> getSelected() {
    SelectedItems<? extends IsIndexed> selected = null;

    if (itemsSearchResultPanel != null && itemsSearchResultPanel.hasElementsSelected()) {
      selected = itemsSearchResultPanel.getSelected();
    } else if (representationsSearchResultPanel != null && representationsSearchResultPanel.hasElementsSelected()) {
      selected = representationsSearchResultPanel.getSelected();
    } else if (filesSearchResultPanel != null && filesSearchResultPanel.hasElementsSelected()) {
      selected = filesSearchResultPanel.getSelected();
    }

    if (selected == null) {
      selected = new SelectedItemsList<>();
    }

    return selected;
  }

  public void clearSelected() {

    if (itemsSearchResultPanel != null) {
      itemsSearchResultPanel.clearSelected();
    }

    if (representationsSearchResultPanel != null) {
      representationsSearchResultPanel.clearSelected();
    }

    if (filesSearchResultPanel != null) {
      filesSearchResultPanel.clearSelected();
    }
  }

  public void refresh() {
    if (itemsSearchResultPanel != null && itemsSearchResultPanel.hasElementsSelected()) {
      itemsSearchResultPanel.refresh();
    } else if (representationsSearchResultPanel != null && representationsSearchResultPanel.hasElementsSelected()) {
      representationsSearchResultPanel.refresh();
    } else if (filesSearchResultPanel != null && filesSearchResultPanel.hasElementsSelected()) {
      filesSearchResultPanel.refresh();
    }
  }

  public boolean setSearch(List<String> historyTokens) {
    // #search/TYPE/key/value/key/value
    String type = historyTokens.get(0);

    boolean successful = searchPanel.setDropdownSelectedValue(type, false);
    if (successful) {
      List<FilterParameter> params = new ArrayList<>();
      for (int i = 1; i < historyTokens.size() - 1; i += 2) {
        String key = historyTokens.get(i);
        String value = historyTokens.get(i + 1);

        if (RodaConstants.INGEST_JOB_ID.equals(key)) {
          List<FilterParameter> orParameter = new ArrayList<>();
          orParameter.add(new SimpleFilterParameter(key, value));
          orParameter.add(new SimpleFilterParameter(RodaConstants.INGEST_UPDATE_JOB_IDS, value));
          params.add(new OrFiltersParameters(orParameter));
        } else {
          params.add(new SimpleFilterParameter(key, value));
        }
      }

      if (!params.isEmpty()) {
        if (searchPanel.isDefaultFilterIncremental()) {
          filterAips.add(params);
          filterRepresentations.add(params);
          filterFiles.add(params);
        } else {
          Filter filter = new Filter(params);
          filterAips = filter;
          filterRepresentations = filter;
          filterFiles = filter;
          searchPanel.setDefaultFilter(filter, true);
        }
      }
    }

    return successful;
  }

  public void search() {
    if (searchPanel.getDropdownSelectedValue().equals(RodaConstants.SEARCH_ITEMS)) {
      showSearchAdvancedFieldsPanel();
    } else if (searchPanel.getDropdownSelectedValue().equals(RodaConstants.SEARCH_REPRESENTATIONS)) {
      showRepresentationsSearchAdvancedFieldsPanel();
    } else {
      showFilesSearchAdvancedFieldsPanel();
    }
    searchPanel.doSearch();
  }

  public void defaultFilters() {
    filterAips = new Filter(DEFAULT_FILTER_AIP);
    filterRepresentations = new Filter(DEFAULT_FILTER_REPRESENTATIONS);
    filterFiles = new Filter(DEFAULT_FILTER_FILES);
    if (searchPanel != null) {
      searchPanel.setDefaultFilterIncremental(false);
    }
  }

  public void setDefaultFilters(Filter defaultFilter) {
    filterAips = new Filter(defaultFilter);
    filterRepresentations = new Filter(defaultFilter);
    filterFiles = new Filter(defaultFilter);
    if (searchPanel != null) {
      searchPanel.setDefaultFilterIncremental(true);
    }
  }

  public void setAipFilter(Filter filter) {
    filterAips = filter;
  }
}
