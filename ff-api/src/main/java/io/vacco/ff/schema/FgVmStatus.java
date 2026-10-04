package io.vacco.ff.schema;

/**
 * Runtime status of a VM, including optional provisioning progress.
 */
public class FgVmStatus {

  public int pid;
  public FgVmState state = FgVmState.pending;
  public FgVm vm;
  public FgProvision provision;
  public String error;

  public static FgVmStatus of(FgVm vm, int pid, FgVmState state) {
    var s = new FgVmStatus();
    s.vm = vm;
    s.pid = pid;
    s.state = state;
    return s;
  }
}
