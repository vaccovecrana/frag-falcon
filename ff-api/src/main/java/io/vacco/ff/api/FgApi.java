package io.vacco.ff.api;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import io.vacco.ff.service.FgStackSvc;
import io.vacco.murmux.Murmux;
import io.vacco.murmux.http.MxErrorHandler;
import io.vacco.murmux.http.MxExchange;
import io.vacco.murmux.http.MxStatus;
import io.vacco.murmux.middleware.MxRouter;
import io.vacco.ronove.RvJsonInput;
import io.vacco.ronove.RvJsonOutput;
import io.vacco.ronove.murmux.RvMxAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.Executors;

/**
 * HTTP server exposing the stack-oriented REST API.
 */
public class FgApi implements Closeable {

  private static final Logger log = LoggerFactory.getLogger(FgApi.class);

  private final Murmux mx;

  public FgApi(FgStackSvc svc, Gson g, String host, int port) {
    var pool = Executors.newCachedThreadPool(r -> new Thread(r, "ff-api"));
    this.mx = new Murmux(host, pool);

    var errorHdl = (java.util.function.BiConsumer<io.vacco.murmux.http.MxExchange, Exception>) (xc, e) -> {
      log.error("Unhandled API error on {}", xc.getPath(), e);
      xc.withStatus(MxStatus._500).commitText("internal error");
    };

    var rpc = new RvMxAdapter<>(new FgApiHdl(svc), errorHdl, g::fromJson, g::toJson).build();
    var uiHdl = new FgUiHdl();
    var router = new MxRouter().prefix(FgRoute.apiRoot, rpc).noMatch(uiHdl);

    mx.rootHandler(router).listen(port);
    log.info("frag-falcon API listening on http://{}:{}", host, port);
  }

  @Override
  public void close() {
    mx.stop();
  }
}
