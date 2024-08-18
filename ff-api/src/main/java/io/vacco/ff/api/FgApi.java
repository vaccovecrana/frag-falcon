package io.vacco.ff.api;

import com.google.gson.Gson;
import io.vacco.ff.service.FgVmSvc;
import io.vacco.ff.util.FgIo;
import io.vacco.murmux.Murmux;
import io.vacco.murmux.http.MxStatus;
import io.vacco.murmux.middleware.MxRouter;
import io.vacco.ronove.murmux.RvMxAdapter;
import org.slf4j.*;
import java.io.*;
import java.util.concurrent.ThreadFactory;

import static java.lang.String.format;
import static java.lang.Integer.toHexString;
import static io.vacco.ff.service.FgOptions.*;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static io.vacco.ff.service.FgLogging.onError;
import static io.vacco.ff.api.FgRoute.*;

public class FgApi implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(FgApi.class);

  private final Murmux mx;
  private final FgVmSvc vmSvc;

  public FgApi(Gson g) {
    var tf = (ThreadFactory) r -> new Thread(r, format("ff-api-%s", toHexString(r.hashCode())));
    this.mx = new Murmux(host, newCachedThreadPool(tf));
    this.vmSvc = new FgVmSvc();
    var apiHdl = new FgApiHdl(this.vmSvc);
    var uiHdl = new FgUiHdl();
    var rpc = new RvMxAdapter<>(apiHdl, (xc, e) -> {
      onError(log, "api - request handling error - {}", e, xc.getPath());
      xc.withStatus(MxStatus._500);
      xc.commit();
    }, g::fromJson, g::toJson).build();
    mx.rootHandler(new MxRouter().prefix(apiRoot, rpc).noMatch(uiHdl));
  }

  public FgApi open() {
    mx.listen(port);
    vmSvc.start();
    log.info("ui - ready at http://{}:{}", host, port);
    return this;
  }

  @Override public void close() {
    mx.stop();
    FgIo.close(vmSvc);
    log.info("ui - stopped");
  }

}