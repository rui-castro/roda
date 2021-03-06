/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.data.v2.ip;

import java.io.Serializable;
import java.util.List;

public interface StoragePath extends Serializable {

  public String getContainerName();

  public boolean isFromAContainer();

  public List<String> getDirectoryPath();

  public String getName();

  public List<String> asList();

  // Force re-declaration of #hashCode() and #equals(Object)

  @Override
  public int hashCode();

  @Override
  public boolean equals(Object obj);

  public String asString(String separator, String replaceAllRegex, String replaceAllReplacement, boolean skipContainer);

}
