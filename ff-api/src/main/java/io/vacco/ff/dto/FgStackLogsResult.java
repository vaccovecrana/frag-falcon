package io.vacco.ff.dto;

import io.vacco.ronove.util.RvResult;

import java.util.Map;

public class FgStackLogsResult extends RvResult {

  public Map<String, String> logs;

  public static FgStackLogsResult of(Map<String, String> logs) {
    var r = new FgStackLogsResult();
    r.logs = logs;
    return r;
  }
}
