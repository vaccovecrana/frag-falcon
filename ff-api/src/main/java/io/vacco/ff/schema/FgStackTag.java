package io.vacco.ff.schema;

public class FgStackTag {

  public String id;
  public String label;
  public String description;

  public static FgStackTag of(String id) {
    var t = new FgStackTag();
    t.id = id;
    return t;
  }
}
