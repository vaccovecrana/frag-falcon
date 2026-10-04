package io.vacco.ff.service;

import java.io.File;
import java.util.HashMap;

public class FgOptions {

  public enum LogLevel {error, warning, info, debug, trace}

  public enum LogFormat {text, json}

  public static File vmDir;
  public static String host = "127.0.0.1";
  public static int port = 7070;
  public static LogFormat logFormat = LogFormat.text;
  public static LogLevel logLevel = LogLevel.info;

  public static String usage() {
    return String.join("\n",
      "frag-falcon - libkrun microVM hypervisor",
      "",
      "  --vm-dir=PATH        VM storage directory (required)",
      "  --api-host=HOST      API bind address (default 127.0.0.1)",
      "  --api-port=PORT      API port (default 7070)",
      "  --log-format=FORMAT  text|json (default text)",
      "  --log-level=LEVEL    error|warning|info|debug|trace (default info)"
    );
  }

  public static void setFrom(String[] args) {
    var m = new HashMap<String, String>();
    for (var a : args) {
      if (!a.startsWith("--")) {
        continue;
      }
      var kv = a.substring(2).split("=", 2);
      if (kv.length == 2) {
        m.put(kv[0], kv[1]);
      }
    }
    var vmDirPath = m.get("vm-dir");
    if (vmDirPath == null) {
      throw new IllegalArgumentException("--vm-dir is required");
    }
    vmDir = new File(vmDirPath);
    host = m.getOrDefault("api-host", host);
    port = Integer.parseInt(m.getOrDefault("api-port", Integer.toString(port)));
    if (m.containsKey("log-format")) {
      logFormat = LogFormat.valueOf(m.get("log-format"));
    }
    if (m.containsKey("log-level")) {
      logLevel = LogLevel.valueOf(m.get("log-level"));
    }
  }
}
