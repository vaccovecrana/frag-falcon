package io.vacco.ff.schema;

import java.util.Objects;

public class FgVmStatus {

  public int fcPid;
  public FgVm vm;
  public FgNetConfig network;

  public FgVmStatus withNetwork(FgNetConfig network) {
    this.network = Objects.requireNonNull(network);
    return this;
  }

  public FgVmStatus withFcPid(int fcPid) {
    this.fcPid = fcPid;
    return this;
  }

  public FgVmStatus withVm(FgVm vm) {
    this.vm = Objects.requireNonNull(vm);
    return this;
  }

}
