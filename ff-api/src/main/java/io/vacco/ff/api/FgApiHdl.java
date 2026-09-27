package io.vacco.ff.api;

import io.vacco.ff.net.FgJni;
import io.vacco.ff.schema.FgStack;
import io.vacco.ff.schema.FgStackRef;
import io.vacco.ff.schema.FgStackStatus;
import io.vacco.ff.service.FgStackSvc;
import io.vacco.ronove.RvResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Stack-oriented REST controller. VMs are managed only through stack
 * definitions; there are no per-VM endpoints.
 */
public class FgApiHdl {

  private static final Logger log = LoggerFactory.getLogger(FgApiHdl.class);

  private final FgStackSvc svc;

  public FgApiHdl(FgStackSvc svc) {
    this.svc = svc;
  }

  private static <T> RvResponse<T> ok(T body) {
    return new RvResponse<T>().withStatus(Response.Status.OK).withBody(body);
  }

  private static <T> RvResponse<T> fail(Response.Status status, Exception e) {
    log.warn("request failed: {}", e.toString());
    return new RvResponse<T>().withStatus(status);
  }

  @GET
  @Path(FgRoute.apiV1Stack)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<List<FgStackStatus>> apiV1StackGet() {
    try {
      return ok(svc.list());
    } catch (Exception e) {
      return fail(Response.Status.INTERNAL_SERVER_ERROR, e);
    }
  }

  @GET
  @Path(FgRoute.apiV1StackId)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStack> apiV1StackIdGet(@PathParam(FgRoute.StackId) String stackId) {
    try {
      return ok(svc.load(stackId));
    } catch (Exception e) {
      return fail(Response.Status.BAD_REQUEST, e);
    }
  }

  @POST
  @Path(FgRoute.apiV1Stack)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStack> apiV1StackPost(@BeanParam FgStack stack) {
    try {
      return ok(svc.save(stack));
    } catch (Exception e) {
      return fail(Response.Status.BAD_REQUEST, e);
    }
  }

  @DELETE
  @Path(FgRoute.apiV1StackId)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStackStatus> apiV1StackIdDelete(@PathParam(FgRoute.StackId) String stackId) {
    try {
      svc.delete(stackId);
      return ok(FgStackStatus.of(stackId));
    } catch (Exception e) {
      return fail(Response.Status.BAD_REQUEST, e);
    }
  }

  @POST
  @Path(FgRoute.apiV1StackStart)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStackStatus> apiV1StackStartPost(@BeanParam FgStackRef ref) {
    try {
      return ok(svc.start(ref.stackId));
    } catch (Exception e) {
      return fail(Response.Status.BAD_REQUEST, e);
    }
  }

  @POST
  @Path(FgRoute.apiV1StackStop)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<FgStackStatus> apiV1StackStopPost(@BeanParam FgStackRef ref) {
    try {
      return ok(svc.stop(ref.stackId));
    } catch (Exception e) {
      return fail(Response.Status.BAD_REQUEST, e);
    }
  }

  @POST
  @Path(FgRoute.apiV1StackLogs)
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<Map<String, String>> apiV1StackLogsPost(@BeanParam FgStackRef ref) {
    try {
      return ok(svc.logs(ref.stackId));
    } catch (Exception e) {
      return fail(Response.Status.BAD_REQUEST, e);
    }
  }

  @GET
  @Path(FgRoute.apiV1Br)
  @Produces(MediaType.APPLICATION_JSON)
  public RvResponse<List<String>> apiV1BrGet() {
    try {
      return ok(FgJni.getLinuxBridgeInterfaces());
    } catch (Exception e) {
      return fail(Response.Status.INTERNAL_SERVER_ERROR, e);
    }
  }
}
