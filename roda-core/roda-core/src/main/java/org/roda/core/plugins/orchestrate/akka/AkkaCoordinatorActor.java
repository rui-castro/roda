/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.core.plugins.orchestrate.akka;

import org.apache.log4j.Logger;

import akka.actor.UntypedActor;

public class AkkaCoordinatorActor extends UntypedActor {
  private final Logger logger = Logger.getLogger(getClass());

  public AkkaCoordinatorActor() {

  }

  @Override
  public void onReceive(Object msg) throws Exception {
    logger.info("message: " + msg);
  }

}