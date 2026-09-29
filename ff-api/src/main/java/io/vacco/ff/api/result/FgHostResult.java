package io.vacco.ff.api.result;

import io.vacco.ronove.RvResult;

public class FgHostResult extends RvResult {

  public String name;

  public static FgHostResult of(String name) {
    var r = new FgHostResult();
    r.name = name;
    return r;
  }
}
