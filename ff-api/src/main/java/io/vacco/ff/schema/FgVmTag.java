package io.vacco.ff.schema;

public class FgVmTag {

  public String id;
  public String label;
  public String description;

  public static FgVmTag of(String id) {
    var t = new FgVmTag();
    t.id = id;
    return t;
  }
}
