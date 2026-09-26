package io.vacco.ff.api;

import io.vacco.ff.schema.*;
import io.vacco.ff.service.FgVmSvc;
import io.vacco.ronove.RvResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import static java.util.Objects.requireNonNull;
import static io.vacco.ff.api.FgRoute.*;

public class FgApiHdl {

  private final FgVmSvc vmSvc;

  public FgApiHdl(FgVmSvc vmSvc) {
    this.vmSvc = requireNonNull(vmSvc);
  }

  @GET @Path(apiV1Vm)
  public RvResponse<FgVmList> apiV1VmGet() {
    var r = new RvResponse<FgVmList>().withBody(vmSvc.vmList());
    return r.body.errors == null || r.body.errors.isEmpty()
      ? r.withStatus(Response.Status.OK)
      : r.withStatus(Response.Status.BAD_REQUEST);
  }

  @GET @Path(apiV1VmId)
  public RvResponse<FgVmCreate> apiV1VmIdGet(@PathParam(VmId) String vmId) {
    var r = new RvResponse<FgVmCreate>().withBody(vmSvc.vmGet(vmId));
    var noErrors = r.body.errors == null || r.body.errors.isEmpty();
    var noWarnings = r.body.warnings == null || r.body.warnings.isEmpty();
    return noErrors && noWarnings
      ? r.withStatus(Response.Status.OK)
      : r.withStatus(Response.Status.BAD_REQUEST);
  }

  @POST @Path(apiV1Vm)
  public RvResponse<FgVmCreate> apiV1VmPost(@BeanParam FgVmCreate req) {
    var r = new RvResponse<FgVmCreate>().withBody(vmSvc.vmBuild(req));
    return req.errors == null || req.errors.isEmpty()
      ? r.withStatus(Response.Status.OK)
      : r.withStatus(Response.Status.BAD_REQUEST);
  }

  @POST @Path(apiV1VmStart)
  public RvResponse<FgVmStart> apiV1VmStartPost(@BeanParam FgVmStart req) {
    var r = new RvResponse<FgVmStart>().withBody(vmSvc.vmStart(req));
    return req.errors == null || req.errors.isEmpty()
      ? r.withStatus(Response.Status.OK)
      : r.withStatus(Response.Status.BAD_REQUEST);
  }

  @POST @Path(apiV1VmStop)
  public RvResponse<FgVmStop> apiV1VmStopPost(@BeanParam FgVmStop req) {
    var r = new RvResponse<FgVmStop>().withBody(vmSvc.vmStop(req));
    return req.errors == null || req.errors.isEmpty()
      ? r.withStatus(Response.Status.OK)
      : r.withStatus(Response.Status.BAD_REQUEST);
  }

  @POST @Path(apiV1VmLogs)
  public RvResponse<FgVmLogs> apiV1VmLogsPost(@BeanParam FgVmLogs req) {
    var r = new RvResponse<FgVmLogs>().withBody(vmSvc.vmLogs(req));
    return req.errors == null || req.errors.isEmpty()
      ? r.withStatus(Response.Status.OK)
      : r.withStatus(Response.Status.BAD_REQUEST);
  }

  @DELETE @Path(apiV1VmLogs)
  public RvResponse<FgVmLogs> apiV1VmLogsDelete(@QueryParam(VmId) String vmId) {
    var r = new RvResponse<FgVmLogs>().withBody(vmSvc.vmLogsDelete(vmId));
    return r.body.errors == null || r.body.errors.isEmpty()
      ? r.withStatus(Response.Status.OK)
      : r.withStatus(Response.Status.BAD_REQUEST);
  }

  @GET @Path(apiV1Br)
  public RvResponse<FgVmResourceList> apiV1BrGet() {
    var r = new RvResponse<FgVmResourceList>().withBody(vmSvc.brIfList());
    return r.body.errors == null || r.body.errors.isEmpty()
      ? r.withStatus(Response.Status.OK)
      : r.withStatus(Response.Status.BAD_REQUEST);
  }

  @GET @Path(apiV1Krn)
  public RvResponse<FgVmResourceList> apiV1KrnGet() {
    var r = new RvResponse<FgVmResourceList>().withBody(vmSvc.krnList());
    return r.body.errors == null || r.body.errors.isEmpty()
      ? r.withStatus(Response.Status.OK)
      : r.withStatus(Response.Status.BAD_REQUEST);
  }

}
