package io.vacco.ff.dto;

import io.vacco.ronove.util.RvResult;

public class FgHostResult extends RvResult {

  public String name;

  public static FgHostResult of(String name) {
    var r = new FgHostResult();
    r.name = name;
    return r;
  }
}
