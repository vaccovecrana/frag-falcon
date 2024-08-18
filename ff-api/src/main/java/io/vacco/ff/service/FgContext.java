package io.vacco.ff.service;

import com.google.gson.Gson;
import io.vacco.ff.api.FgApi;
import io.vacco.ff.util.FgIo;

import java.io.Closeable;

import static java.lang.String.join;
import static io.vacco.shax.logging.ShOption.*;
import static io.vacco.ff.service.FgOptions.*;
import static io.vacco.ff.util.FgIo.*;

public class FgContext implements Closeable {

  private FgApi api;

  public void init() {
    setSysProp(IO_VACCO_SHAX_DEVMODE, logFormat == LogFormat.text ? "true" : "false");
    setSysProp(IO_VACCO_SHAX_LOGLEVEL, logLevel.toString());
    var log = org.slf4j.LoggerFactory.getLogger(FgContext.class);
    log.info(
      join("\n", "",
        "   ________    ___  ______",
        "  / __/ __/___/ _ \\/_  __/",
        " / _// _//___/ , _/ / /   ",
        "/_/ /_/     /_/|_| /_/    "
      )
    );
    exists(vmDir);
    exists(krnDir);
    exists(fcPath);
    this.api = new FgApi(new Gson()).open();
  }

  @Override public void close() {
    FgIo.close(api);
  }

}
