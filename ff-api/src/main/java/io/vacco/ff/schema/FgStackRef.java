package io.vacco.ff.schema;

public class FgStackRef {

  public String stackId;

  public static FgStackRef of(String stackId) {
    var r = new FgStackRef();
    r.stackId = stackId;
    return r;
  }
}
