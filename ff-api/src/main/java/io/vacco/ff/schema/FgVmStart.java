package io.vacco.ff.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FgVmStart extends FgRequest {

  // input parameters
  public String vmId;

  // output parameters
  public FgVmStatus status;
  public FcApiResponse machineConfig, bootConfig, init;
  public List<FcApiResponse> drives;

  public FgVmStart withStatus(FgVmStatus status) {
    this.status = Objects.requireNonNull(status);
    return this;
  }

  public FgVmStart withMachineConfig(FcApiResponse machineConfig) {
    this.machineConfig = Objects.requireNonNull(machineConfig);
    return this;
  }

  public FgVmStart withInit(FcApiResponse init) {
    this.init = Objects.requireNonNull(init);
    return this;
  }

  public FgVmStart withDrive(FcApiResponse drive) {
    if (this.drives == null) {
      this.drives = new ArrayList<>();
    }
    this.drives.add(drive);
    return this;
  }

}
