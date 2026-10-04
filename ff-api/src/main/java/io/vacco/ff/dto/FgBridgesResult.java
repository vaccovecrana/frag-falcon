package io.vacco.ff.dto;

import io.vacco.ronove.util.RvResult;

import java.util.List;

public class FgBridgesResult extends RvResult {

  public List<String> bridges;

  public static FgBridgesResult of(List<String> bridges) {
    var r = new FgBridgesResult();
    r.bridges = bridges;
    return r;
  }
}
