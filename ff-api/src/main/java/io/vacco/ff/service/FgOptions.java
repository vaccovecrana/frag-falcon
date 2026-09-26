package io.vacco.ff.service;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

public class FgOptions {

  public enum LogLevel { error, warning, info, debug, trace }
  public enum LogFormat { text, json }

  public static final String
    kVmDir      = "--vm-dir",
    kKrnDir     = "--krn-dir",
    kFcPath     = "--fc-path",
    kApiHost    = "--api-host",   kApiPort = "--api-port",
    kLogFormat  = "--log-format", kLogLevel = "--log-level";

  public static File      vmDir, krnDir, fcPath;
  public static LogFormat logFormat = LogFormat.text;
  public static LogLevel  logLevel  = LogLevel.info;
  public static String    host = "127.0.0.1";
  public static int       port = 7070;

  public static String usage() {
    // - TODO - clarify in documentation that external VM storage is not managed by the app itself, you configure it.
    return String.join("\n",
      "Usage:",
      "  flc [options]",
      "Options:",
      "  --vm-dir=string      VM metadata directory. Required.",
      "                         - Stores VM configs, Firecracker metadata and initramfs images",
      "  --krn-dir=string     Linux Kernel storage directory. Required.",
      "                         - Stores Kernel images used to boot VMs.",
      "  --fc-path=string     Path to the firecracker binary. Required.",
      "  --api-host=string    API/UI IP address. Default: " + host,
      "  --api-port=number    API/UI port. Default: " + port,
      "  --log-format=string  Log output format ('text' or 'json'). Default: " + logFormat,
      "  --log-level=string   Log level ('error', 'warning', 'info', 'debug', 'trace'). Default: " + logLevel,
      "  --help               Prints this help message."
    );
  }

  public static void setFrom(String[] args) {
    var argIdx = Arrays.stream(args)
      .filter(arg -> arg.startsWith("--"))
      .map(arg -> arg.split("="))
      .filter(pair -> pair.length == 2)
      .filter(pair -> pair[0] != null && pair[1] != null)
      .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1]));

    vmDir   = new File(argIdx.get(kVmDir));
    krnDir  = new File(argIdx.get(kKrnDir));
    fcPath  = new File(argIdx.get(kFcPath));

    var vHost = argIdx.get(kApiHost);
    var vPort = argIdx.get(kApiPort);
    var vLogFormat = argIdx.get(kLogFormat);
    var vLogLevel = argIdx.get(kLogLevel);

    host = vHost != null ? vHost : host;
    port = vPort != null ? Integer.parseInt(vPort) : port;
    logFormat = vLogFormat != null ? LogFormat.valueOf(vLogFormat) : logFormat;
    logLevel = vLogLevel != null ? LogLevel.valueOf(vLogLevel) : logLevel;
  }

}
