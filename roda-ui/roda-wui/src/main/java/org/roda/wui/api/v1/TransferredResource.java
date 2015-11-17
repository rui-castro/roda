/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE file at the root of the source
 * tree and available online at
 *
 * https://github.com/keeps/roda
 */
package org.roda.wui.api.v1;

import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.roda.core.RodaCoreFactory;
import org.roda.core.common.UserUtility;
import org.roda.core.data.common.RODAException;
import org.roda.core.data.v2.RodaUser;
import org.roda.wui.api.controllers.Browser;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiParam;

@Path(TransferredResource.ENDPOINT)
@Api(value = TransferredResource.SWAGGER_ENDPOINT)
public class TransferredResource {
  public static final String ENDPOINT = "/v1/transferred";
  public static final String SWAGGER_ENDPOINT = "v1 transferred";

  @Context
  private HttpServletRequest request;

  @POST
  @Path("/")
  public Response createResource(
    @ApiParam(value = "The id of the parent", required = true) @QueryParam("parentId") String parentId,
    @ApiParam(value = "The name of the directory to create", required = false) @QueryParam("name") String name,
    @FormDataParam("upl") InputStream inputStream, @FormDataParam("upl") FormDataContentDisposition fileDetail)
      throws RODAException {
    // get user
    RodaUser user = UserUtility.getApiUser(request, RodaCoreFactory.getIndexService());
    // delegate action to controller
    if(name==null){
      try{
        Browser.createTransferredResourceFile(user, parentId, fileDetail.getFileName(), inputStream);
      }catch(FileAlreadyExistsException e){
        return Response.status(Status.CONFLICT).entity("{'status':'error'}").build();
      }
    }else{
      Browser.createTransferredResourcesFolder(user, parentId, name);
    }
    // FIXME give a better answer
    return Response.ok().entity("{'status':'success'}").build();
  }

  @DELETE
  @Path("/")
  public Response deleteResource(
    @ApiParam(value = "The id of the resource", required = true) @QueryParam("path") String path)
      throws RODAException {
    // get user
    RodaUser user = UserUtility.getApiUser(request, RodaCoreFactory.getIndexService());
    // delegate action to controller
    Browser.removeTransferredResource(user, path);
    // FIXME give a better answer
    return Response.ok().entity("{'status':'success'}").build();
  }
}