package io.vacco.ff.dto;

import io.vacco.ff.schema.FgStackStatus;
import io.vacco.ronove.util.RvResult;

public class FgStackStatusResult extends RvResult {

  public FgStackStatus status;

  public static FgStackStatusResult of(FgStackStatus status) {
    var r = new FgStackStatusResult();
    r.status = status;
    return r;
  }
}
