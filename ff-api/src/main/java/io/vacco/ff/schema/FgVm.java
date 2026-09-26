package io.vacco.ff.schema;

public class FgVm {

  public FgVmTag  tag;
  public FgImage  image;
  public FgConfig config;

  @Override public String toString() {
    return String.format("%s %s", tag, image);
  }

}
