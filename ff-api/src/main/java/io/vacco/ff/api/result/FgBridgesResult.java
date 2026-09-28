package io.vacco.ff.api.result;

import io.vacco.ronove.RvResult;

import java.util.List;

public class FgBridgesResult extends RvResult {

  public List<String> bridges;

  public static FgBridgesResult of(List<String> bridges) {
    var r = new FgBridgesResult();
    r.bridges = bridges;
    return r;
  }
}
