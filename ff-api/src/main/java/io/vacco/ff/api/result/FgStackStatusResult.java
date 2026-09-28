package io.vacco.ff.api.result;

import io.vacco.ff.schema.FgStackStatus;
import io.vacco.ronove.RvResult;

public class FgStackStatusResult extends RvResult {

  public FgStackStatus status;

  public static FgStackStatusResult of(FgStackStatus status) {
    var r = new FgStackStatusResult();
    r.status = status;
    return r;
  }
}
