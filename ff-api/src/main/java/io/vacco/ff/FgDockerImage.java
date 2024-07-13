package io.vacco.ff;

import java.io.File;
import java.util.Objects;

public class FgDockerImage {

  public File rootDir;
  public String[] entryPoint;
  public String[] cmd;

  public static FgDockerImage of(File rootDir, String[] entryPoint, String[] cmd) {
    var d = new FgDockerImage();
    d.rootDir = Objects.requireNonNull(rootDir);
    d.entryPoint = entryPoint;
    d.cmd = cmd;
    return d;
  }
}
