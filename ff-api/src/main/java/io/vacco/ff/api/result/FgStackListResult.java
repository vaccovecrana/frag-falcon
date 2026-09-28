package io.vacco.ff.api.result;

import io.vacco.ff.schema.FgStackStatus;
import io.vacco.ronove.RvResult;

import java.util.List;

public class FgStackListResult extends RvResult {

  public List<FgStackStatus> stacks;

  public static FgStackListResult of(List<FgStackStatus> stacks) {
    var r = new FgStackListResult();
    r.stacks = stacks;
    return r;
  }
}
