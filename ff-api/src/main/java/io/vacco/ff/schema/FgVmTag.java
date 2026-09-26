package io.vacco.ff.schema;

public class FgVmTag {

  public String id, label, description;

  @Override public String toString() {
    return String.format("[%s, %s, %s]", id, label, description);
  }

}
