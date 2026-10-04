package io.vacco.ff.schema;

import java.util.List;

/**
 * Per-service runtime status within a stack.
 */
public class FgServiceStatus {

  public String service;
  public String id;
  public FgVmState state = FgVmState.pending;
  public int pid;
  public List<String> exposedPorts;
  public FgProvision provision;
  public String error;

  public static FgServiceStatus of(String service, String id, FgVmState state, int pid) {
    var s = new FgServiceStatus();
    s.service = service;
    s.id = id;
    s.state = state;
    s.pid = pid;
    return s;
  }

  public FgServiceStatus withError(String error) {
    this.error = error;
    return this;
  }
}
