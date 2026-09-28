package io.vacco.ff.api.result;

import io.vacco.ff.schema.FgStack;
import io.vacco.ronove.RvResult;

public class FgStackResult extends RvResult {

  public FgStack stack;

  public static FgStackResult of(FgStack stack) {
    var r = new FgStackResult();
    r.stack = stack;
    return r;
  }
}
