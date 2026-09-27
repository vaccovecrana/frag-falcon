package io.vacco.ff.api;

import io.vacco.murmux.http.MxExchange;
import io.vacco.murmux.http.MxMime;
import io.vacco.murmux.middleware.MxStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.util.Objects.requireNonNull;

/**
 * Serves the Preact SPA from the classpath ({@code /ui}), with an index.html
 * fallback for client-side routes.
 */
public class FgUiHdl extends MxStatic {

  private static final Logger log = LoggerFactory.getLogger(FgUiHdl.class);
  private static final String index = "/index.html";

  private static final File projectRoot = resolveCommonPath(new File("."), "frag-falcon-libkrun"); // TODO rename back to plain frag-falcon later
  private static final File pkgJson = projectRoot != null && projectRoot.exists()
    ? new File(projectRoot, "./ff-ui/package.json")
    : null;
  private static final Origin origin = pkgJson != null ? Origin.FileSystem : Origin.Classpath;
  private static final Path root = pkgJson != null
    ? Paths.get(requireNonNull(projectRoot).toPath().toString(), "./ff-ui/build/resources/main/ui")
    : Paths.get("/ui");

  @SuppressWarnings("this-escape")
  public FgUiHdl() {
    super(origin, root);
    withNoTypeResolver((p, _) -> p.getFileName().toString().endsWith(".map") ? MxMime.json.type : MxMime.bin.type);
    log.info("Resources: ({}) - {}", origin, root);
  }

  @Override
  public void handle(MxExchange xc) {
    var path = xc.getPath();
    if (path.equals("/favicon.svg") || path.equals("/index.css") || path.equals("/index.js")
      || path.equals("/index.js.map") || path.equals("/index.css.map") || path.equals("/version")) {
      super.handle(xc);
      return;
    }
    handleWithPath(xc, index);
  }
}
