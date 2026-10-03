package io.vacco.ff.dto;

import io.vacco.ff.schema.FgStack;
import io.vacco.ronove.util.RvResult;

public class FgStackResult extends RvResult {

  public FgStack stack;

  public static FgStackResult of(FgStack stack) {
    var r = new FgStackResult();
    r.stack = stack;
    return r;
  }
}
