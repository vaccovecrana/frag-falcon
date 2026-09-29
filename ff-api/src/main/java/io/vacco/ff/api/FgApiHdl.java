package io.vacco.ff.api;

import io.vacco.ff.api.result.*;
import io.vacco.ff.net.FgJni;
import io.vacco.ff.schema.FgStack;
import io.vacco.ff.schema.FgStackRef;
import io.vacco.ff.schema.FgStackStatus;
import io.vacco.ff.service.FgStackSvc;
import io.vacco.ff.service.FgValidationException;
import io.vacco.ff.util.FgIo;
import io.vacco.ronove.RvResponse;
import io.vacco.ronove.RvResult;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * Stack-oriented REST controller. VMs are managed only through stack
 * definitions; there are no per-VM endpoints.
 *
 * <p>Every endpoint returns a {@link RvResult} subclass as the response body —
 * populated on success, and carrying {@link io.vacco.ronove.RvValidation} hints
 * on failure — so browser clients never receive an empty error body.
 */
public class FgApiHdl {

  private static final Logger log = LoggerFactory.getLogger(FgApiHdl.class);

  private final FgStackSvc svc;

  public FgApiHdl(FgStackSvc svc) {
    this.svc = svc;
  }

  private static <R extends RvResult> RvResponse<R> handle(
    Response.Status errorStatus, R body, Function<R, R> op) {
    try {
      return new RvResponse<R>().withStatus(Response.Status.OK).withBody(op.apply(body));
    } catch (Exception e) {
      log.warn("request failed: {}", e.toString());
      if (e instanceof FgValidationException ve) {
        body.withValidations(ve.validations);
      }
      body.withError(e);
      var status = e instanceof FgStackSvc.FgBusyException ? Response.Status.CONFLICT : errorStatus;
      return new RvResponse<R>().withStatus(status).withBody(body);
    }
  }

  @GET
  @Path(FgRoute.apiV1Stack)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStackListResult> apiV1StackGet() {
    var body = new FgStackListResult();
    return handle(Response.Status.INTERNAL_SERVER_ERROR, body, r -> {
      r.stacks = svc.list();
      return r;
    });
  }

  @GET
  @Path(FgRoute.apiV1StackId)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStackResult> apiV1StackIdGet(@PathParam(FgRoute.StackId) String stackId) {
    var body = new FgStackResult();
    return handle(Response.Status.BAD_REQUEST, body, r -> {
      r.stack = svc.load(stackId);
      return r;
    });
  }

  @POST
  @Path(FgRoute.apiV1Stack)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStackResult> apiV1StackPost(@BeanParam FgStack stack) {
    var body = new FgStackResult();
    return handle(Response.Status.BAD_REQUEST, body, r -> {
      r.stack = svc.save(stack);
      return r;
    });
  }

  @DELETE
  @Path(FgRoute.apiV1StackId)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStackStatusResult> apiV1StackIdDelete(@PathParam(FgRoute.StackId) String stackId) {
    var body = new FgStackStatusResult();
    return handle(Response.Status.BAD_REQUEST, body, r -> {
      svc.delete(stackId);
      r.status = FgStackStatus.of(stackId);
      return r;
    });
  }

  @PATCH
  @Path(FgRoute.apiV1StackId)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStackStatusResult> apiV1StackIdPatch(@PathParam(FgRoute.StackId) String stackId) {
    var body = new FgStackStatusResult();
    return handle(Response.Status.BAD_REQUEST, body, r -> {
      r.status = svc.update(stackId);
      return r;
    });
  }

  @POST
  @Path(FgRoute.apiV1StackStart)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStackStatusResult> apiV1StackStartPost(@BeanParam FgStackRef ref) {
    var body = new FgStackStatusResult();
    return handle(Response.Status.BAD_REQUEST, body, r -> {
      r.status = svc.start(ref.stackId);
      return r;
    });
  }

  @POST
  @Path(FgRoute.apiV1StackStop)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStackStatusResult> apiV1StackStopPost(@BeanParam FgStackRef ref) {
    var body = new FgStackStatusResult();
    return handle(Response.Status.BAD_REQUEST, body, r -> {
      r.status = svc.stop(ref.stackId);
      return r;
    });
  }

  @POST
  @Path(FgRoute.apiV1StackLogs)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStackLogsResult> apiV1StackLogsPost(@BeanParam FgStackRef ref) {
    var body = new FgStackLogsResult();
    return handle(Response.Status.BAD_REQUEST, body, r -> {
      r.logs = svc.logs(ref.stackId);
      return r;
    });
  }

  @GET
  @Path(FgRoute.apiV1Br)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgBridgesResult> apiV1BrGet() {
    var body = new FgBridgesResult();
    return handle(Response.Status.INTERNAL_SERVER_ERROR, body, r -> {
      r.bridges = FgJni.getLinuxBridgeInterfaces();
      return r;
    });
  }

  @GET
  @Path(FgRoute.apiV1Host)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgHostResult> apiV1HostGet() {
    var body = new FgHostResult();
    return handle(Response.Status.INTERNAL_SERVER_ERROR, body, r -> {
      r.name = FgIo.hostName();
      return r;
    });
  }
}
