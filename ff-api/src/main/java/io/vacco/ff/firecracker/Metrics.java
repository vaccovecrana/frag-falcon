package io.vacco.ff.firecracker;

import java.lang.String;

/**
 * Describes the configuration option for the metrics capability.
 */
public class Metrics {
  public String metrics_path;

  /**
   * Path to the named pipe or file where the JSON-formatted metrics are flushed.
   */
  public Metrics metrics_path(String metrics_path) {
    this.metrics_path = metrics_path;
    return this;
  }

  public static Metrics metrics() {
    return new Metrics();
  }
}
