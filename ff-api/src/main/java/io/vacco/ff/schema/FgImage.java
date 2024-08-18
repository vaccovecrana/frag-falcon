package io.vacco.ff.schema;

import java.util.List;
import java.util.Objects;

public class FgImage {

  public String source;
  public String rootDir;
  public String workingDir;
  public String[] entryPoint;
  public String[] cmd;

  public List<FgEnvVar> env;
  public List<FgEnvVar> envUsr;

  public FgImage withSource(String source) {
    this.source = Objects.requireNonNull(source);
    return this;
  }

  public FgImage withEnvUsr(List<FgEnvVar> envUsr) {
    this.envUsr = envUsr;
    return this;
  }

  public String[] entryPointList() {
    return entryPoint;
  }

  public String[] cmdList() {
    return cmd;
  }

  public List<FgEnvVar> envList() {
    return env;
  }

  public List<FgEnvVar> envUsrList() {
    return envUsr;
  }

  public static FgImage of(String rootDir, String[] entryPoint, String[] cmd,
                           List<FgEnvVar> env, String workingDir) {
    var d = new FgImage();
    d.rootDir = Objects.requireNonNull(rootDir);
    d.entryPoint = entryPoint;
    d.cmd = cmd;
    d.env = Objects.requireNonNull(env);
    d.workingDir = workingDir;
    return d;
  }

  @Override public String toString() {
    return source;
  }

}
