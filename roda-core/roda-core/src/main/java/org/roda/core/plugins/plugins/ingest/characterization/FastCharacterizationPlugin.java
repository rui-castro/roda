/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.plugins.ingest.characterization;

import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.roda.core.data.PluginParameter;
import org.roda.core.data.Report;
import org.roda.core.data.common.InvalidParameterException;
import org.roda.core.data.common.RodaConstants;
import org.roda.core.data.v2.FileFormat;
import org.roda.core.data.v2.Representation;
import org.roda.core.index.IndexService;
import org.roda.core.model.AIP;
import org.roda.core.model.File;
import org.roda.core.model.ModelService;
import org.roda.core.model.ModelServiceException;
import org.roda.core.plugins.Plugin;
import org.roda.core.plugins.PluginException;
import org.roda.core.storage.Binary;
import org.roda.core.storage.StorageService;
import org.roda.core.storage.StorageServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FastCharacterizationPlugin implements Plugin<AIP> {
  private static final Logger LOGGER = LoggerFactory.getLogger(FastCharacterizationPlugin.class);

  @Override
  public void init() throws PluginException {
  }

  @Override
  public void shutdown() {
    // do nothing
  }

  @Override
  public String getName() {
    return "Deep characterization action";
  }

  @Override
  public String getDescription() {
    return "Update the premis files with the object characterization";
  }

  @Override
  public String getVersion() {
    return "1.0";
  }

  @Override
  public List<PluginParameter> getParameters() {
    return new ArrayList<>();
  }

  @Override
  public Map<String, String> getParameterValues() {
    return new HashMap<>();
  }

  @Override
  public void setParameterValues(Map<String, String> parameters) throws InvalidParameterException {
    // no params
  }

  @Override
  public Report execute(IndexService index, ModelService model, StorageService storage, List<AIP> list)
    throws PluginException {
    for (AIP aip : list) {
      LOGGER.debug("Processing AIP " + aip.getId());
      try {
        for (String representationID : aip.getRepresentationIds()) {
          LOGGER.debug("Processing representation " + representationID + " from AIP " + aip.getId());
          Representation representation = model.retrieveRepresentation(aip.getId(), representationID);
          for (String fileID : representation.getFileIds()) {
            LOGGER.debug("Processing file " + fileID + " from " + representationID + " of AIP " + aip.getId());
            File file = model.retrieveFile(aip.getId(), representationID, fileID);
            Binary binary = storage.getBinary(file.getStoragePath());
            Path p = Files.createTempFile("temp", ".temp");
            Files.copy(binary.getContent().createInputStream(), p,  StandardCopyOption.REPLACE_EXISTING);
            FileFormat ff = file.getFileFormat();
            String mime = Files.probeContentType(p);
            LOGGER.error("MIME: "+mime);
            ff.setMimeType(mime);
            file.setFileFormat(ff);
            Map<String, Set<String>> metadata = storage.getMetadata(file.getStoragePath());
            metadata.put(RodaConstants.STORAGE_META_FORMAT_MIME, new HashSet<>(Arrays.asList(mime)));
            storage.updateMetadata(file.getStoragePath(), metadata, true);
            model.updateFile(file);
            Files.delete(p);
          }
        }
      } catch (ModelServiceException mse) {
        LOGGER.error("Error processing AIP " + aip.getId() + ": " + mse.getMessage());
      } catch (StorageServiceException sse) {
        LOGGER.error("Error processing AIP " + aip.getId() + ": " + sse.getMessage());
      } catch (Exception e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
    return null;
  }

  @Override
  public Report beforeExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {

    return null;
  }

  @Override
  public Report afterExecute(IndexService index, ModelService model, StorageService storage) throws PluginException {

    return null;
  }

  @Override
  public Plugin<AIP> cloneMe() {
    return new JHOVEPlugin();
  }

}