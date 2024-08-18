package io.vacco.ff.schema;

public class FgVmStop extends FgRequest {

  public int fcPid;
  public String vmId;

  public FgVmStop withFcPid(int fcPid) {
    this.fcPid = fcPid;
    return this;
  }

}
