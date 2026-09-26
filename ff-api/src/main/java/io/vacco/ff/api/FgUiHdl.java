package io.vacco.ff.api;

import io.vacco.murmux.http.*;
import io.vacco.murmux.middleware.MxStatic;
import java.io.File;
import java.nio.file.*;

import static java.lang.String.format;
import static io.vacco.ff.api.FgRoute.*;
import static io.vacco.ff.util.FgIo.hostName;

public class FgUiHdl extends MxStatic {

  private static final File pkgJson = new File("../ff-ui/package.json");
  private static final Origin contentOrigin = pkgJson.exists() ? Origin.FileSystem : Origin.Classpath;
  private static final Path contentRoot = pkgJson.exists()
    ? Paths.get("../ff-ui/build/resources/main/ui")
    : Paths.get("/ui");

  private static final String indexHtml = String.join("\n", "",
    "<!DOCTYPE html>",
    "<html>",
    "<head>",
    "  <base href=\"/\" />",
    "  <meta charset=\"utf-8\" />",
    "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">",
    "  <link rel=\"icon\" href=\"/favicon.svg\" type=\"image/svg+xml\">",
    "  <link rel=\"stylesheet\" href=\"/index.css\" />",
    format("  <title>%s</title>", hostName()),
    "</head>",
    "<body class=\"dark\">",
    "  <div id=\"root\"></div>",
    "  <script src=\"/index.js\"></script>",
    "  <noscript><!-- Happiness = Reality - Expectations --></noscript>",
    "</body>",
    "</html>"
    );

  public FgUiHdl() {
    super(contentOrigin, contentRoot);
    this.withNoTypeResolver((p, o) -> p.endsWith(".map") ? MxMime.json.type : MxMime.bin.type);
  }

  @Override public void handle(MxExchange xc) {
    var p = xc.getPath();
    if (p.startsWith(fgUi)) {
      super.handle(xc);
      return;
    }
    switch (p) {
      case indexCss:
      case indexJs:
      case indexJsMap:
      case favicon:
      case version:
        super.handle(xc);
        break;
      default:
        xc.commitHtml(indexHtml);
    }
  }

}
