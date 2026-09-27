package io.vacco.ff.api;

import io.vacco.murmux.http.MxExchange;
import io.vacco.murmux.http.MxMime;
import io.vacco.murmux.middleware.MxStatic;

import java.nio.file.Paths;

/**
 * Serves the Preact SPA from the classpath ({@code /ui}), with an index.html
 * fallback for client-side routes.
 */
public class FgUiHdl extends MxStatic {

  private static final String INDEX = "index.html";

  @SuppressWarnings("this-escape")
  public FgUiHdl() {
    super(Origin.Classpath, Paths.get("/ui"));
    withNoTypeResolver((p, o) -> p.getFileName().toString().endsWith(".map") ? MxMime.json.type : MxMime.bin.type);
  }

  @Override
  public void handle(MxExchange xc) {
    var path = xc.getPath();
    if (path.equals("/favicon.svg") || path.equals("/index.css") || path.equals("/index.js")
      || path.equals("/index.js.map") || path.equals("/index.css.map") || path.equals("/version")) {
      handleWithPath(xc, path);
      return;
    }
    handleWithPath(xc, "/" + INDEX);
  }
}
