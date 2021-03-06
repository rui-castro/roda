/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.characterization;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.roda.core.common.iterables.CloseableIterable;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.common.RodaConstants.PreservationEventType;
import org.roda.core.data.exceptions.AlreadyExistsException;
import org.roda.core.data.exceptions.AuthorizationDeniedException;
import org.roda.core.data.exceptions.GenericException;
import org.roda.core.data.exceptions.NotFoundException;
import org.roda.core.data.exceptions.RODAException;
import org.roda.core.data.exceptions.RequestNotValidException;
import org.roda.core.data.v2.LiteOptionalWithCause;
import org.roda.core.data.v2.common.OptionalWithCause;
import org.roda.core.data.v2.ip.AIP;
import org.roda.core.data.v2.ip.AIPState;
import org.roda.core.data.v2.ip.File;
import org.roda.core.data.v2.ip.Representation;
import org.roda.core.data.v2.ip.StoragePath;
import org.roda.core.data.v2.ip.metadata.LinkingIdentifier;
import org.roda.core.data.v2.jobs.Job;
import org.roda.core.data.v2.jobs.PluginType;
import org.roda.core.data.v2.jobs.Report;
import org.roda.core.data.v2.jobs.Report.PluginState;
import org.roda.core.data.v2.validation.ValidationException;
import org.roda.core.data.v2.validation.ValidationIssue;
import org.roda.core.data.v2.validation.ValidationReport;
import org.roda.core.index.IndexService;
import org.roda.core.model.ModelService;
import org.roda.core.model.utils.ModelUtils;
import org.roda.core.plugins.AbstractPlugin;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.plugins.RODAObjectProcessingLogic;
import org.roda.core.plugins.orchestrate.SimpleJobPluginInfo;
import org.roda.core.plugins.plugins.PluginHelper;
import org.roda.core.storage.Binary;
import org.roda.core.storage.ContentPayload;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.StringContentPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JpylyzerPlugin extends AbstractPlugin<AIP> {
  private static final Logger LOGGER = LoggerFactory.getLogger(JpylyzerPlugin.class);

  @Override
  public void init() throws PluginException {
    // do nothing
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public String getName() {
    return "JP2 feature extraction (Jpylyzer)";
  }

  @Override
  public String getDescription() {
    return "Jpylyzer is a validator and feature extractor for JP2 images. JP2 is the still image format that is defined by Part 1 of the JPEG 2000 "
      + "image compression standard (ISO/IEC 15444-1).\nJpylyzer tells you if a JP2 image really conforms to the format’s specifications (validation). "
      + "It also reports the image’s technical characteristics (feature extraction).\nThe task stores the output of the tool and stores it under the"
      + " metadata/other folder of the Archival Information Package. \nNOTE: This task doesn’t create PREMIS events. This is still an experimental "
      + "task.\nFor more information on this tool, please visit http://jpylyzer.openpreservation.org";
  }

  @Override
  public String getVersionImpl() {
    return "0.1";
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage,
    List<LiteOptionalWithCause> liteList) throws PluginException {
    return PluginHelper.processObjects(this, new RODAObjectProcessingLogic<AIP>() {
      @Override
      public void process(IndexService index, ModelService model, StorageService storage, Report report, Job cachedJob,
        SimpleJobPluginInfo jobPluginInfo, Plugin<AIP> plugin, AIP object) {
        processAIP(index, model, storage, report, jobPluginInfo, cachedJob, object);
      }
    }, index, model, storage, liteList);
  }

  private void processAIP(IndexService index, ModelService model, StorageService storage, Report report,
    SimpleJobPluginInfo jobPluginInfo, Job job, AIP aip) {
    LOGGER.debug("Processing AIP {}", aip.getId());
    boolean inotify = false;
    Report reportItem = PluginHelper.initPluginReportItem(this, aip.getId(), AIP.class, AIPState.INGEST_PROCESSING);
    PluginHelper.updatePartialJobReport(this, model, reportItem, false, job);
    PluginState reportState = PluginState.SUCCESS;
    ValidationReport validationReport = new ValidationReport();
    List<LinkingIdentifier> sources = new ArrayList<>();

    for (Representation representation : aip.getRepresentations()) {
      LOGGER.debug("Processing representation {} from AIP {}", representation.getId(), aip.getId());
      try {
        boolean recursive = true;
        CloseableIterable<OptionalWithCause<File>> allFiles = model.listFilesUnder(aip.getId(), representation.getId(),
          recursive);
        for (OptionalWithCause<File> oFile : allFiles) {
          if (oFile.isPresent()) {
            File file = oFile.get();
            if (!file.isDirectory()) {
              // TODO check if file is JPEG2000
              try {
                LOGGER.debug("Processing file: {}", file);
                StoragePath storagePath = ModelUtils.getFileStoragePath(file);
                Binary binary = storage.getBinary(storagePath);

                String jpylyzerResults = JpylyzerPluginUtils.runJpylyzer(storage, binary);
                ContentPayload payload = new StringContentPayload(jpylyzerResults);
                model.createOrUpdateOtherMetadata(aip.getId(), representation.getId(), file.getPath(), file.getId(),
                  ".xml", "jpylyzer", payload, inotify);

                sources.add(PluginHelper.getLinkingIdentifier(aip.getId(), representation.getId(), file.getPath(),
                  file.getId(), RodaConstants.PRESERVATION_LINKING_OBJECT_SOURCE));
              } catch (RODAException | IOException sse) {
                LOGGER.error("Error processing AIP {}: {}", aip.getId(), sse.getMessage());
              }
            }
          } else {
            LOGGER.error("Cannot process AIP representation file", oFile.getCause());
          }
        }
        IOUtils.closeQuietly(allFiles);
      } catch (RODAException e) {
        LOGGER.error("Error processing AIP {}: {}", aip.getId(), e.getMessage());
        reportState = PluginState.FAILURE;
        validationReport.addIssue(new ValidationIssue(e.getMessage()));
      }
    }

    try {
      model.notifyAipUpdated(aip.getId());
    } catch (RequestNotValidException | GenericException | NotFoundException | AuthorizationDeniedException e) {
      LOGGER.error("Error notifying of AIP update", e);
    }

    if (reportState.equals(PluginState.SUCCESS)) {
      jobPluginInfo.incrementObjectsProcessedWithSuccess();
      reportItem.setPluginState(PluginState.SUCCESS);
    } else {
      jobPluginInfo.incrementObjectsProcessedWithFailure();
      reportItem.setHtmlPluginDetails(true).setPluginState(PluginState.FAILURE);
      reportItem.setPluginDetails(validationReport.toHtml(false, false, false, "Error list"));
    }

    try {
      PluginHelper.createPluginEvent(this, aip.getId(), model, index, sources, null, reportItem.getPluginState(), "",
        true);
    } catch (ValidationException | RequestNotValidException | NotFoundException | GenericException
      | AuthorizationDeniedException | AlreadyExistsException e) {
      LOGGER.error("Error creating event: {}", e.getMessage(), e);
    }

    report.addReport(reportItem);
    PluginHelper.updatePartialJobReport(this, model, reportItem, true, job);
  }

  @Override
  public Plugin<AIP> cloneMe() {
    return new JpylyzerPlugin();
  }

  @Override
  public PluginType getType() {
    return PluginType.AIP_TO_AIP;
  }

  @Override
  public boolean areParameterValuesValid() {
    return true;
  }

  @Override
  public PreservationEventType getPreservationEventType() {
    return PreservationEventType.METADATA_EXTRACTION;
  }

  @Override
  public String getPreservationEventDescription() {
    return "Extracted metadata using Jpylyzer";
  }

  @Override
  public String getPreservationEventSuccessMessage() {
    return "Extracted metadata using Jpylyzer successfully";
  }

  @Override
  public String getPreservationEventFailureMessage() {
    return "Extracted metadata using Jpylyzer with failures";
  }

  @Override
  public Report beforeAllExecute(IndexService index, ModelService model, StorageService storage)
    throws PluginException {
    // do nothing
    return null;
  }

  @Override
  public Report afterAllExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {
    // do nothing
    return null;
  }

  @Override
  public List<String> getCategories() {
    return Arrays.asList(RodaConstants.PLUGIN_CATEGORY_VALIDATION, RodaConstants.PLUGIN_CATEGORY_FEATURE_EXTRACTION,
      RodaConstants.PLUGIN_CATEGORY_CHARACTERIZATION, RodaConstants.PLUGIN_CATEGORY_EXPERIMENTAL);
  }

  @Override
  public List<Class<AIP>> getObjectClasses() {
    return Arrays.asList(AIP.class);
  }

}
