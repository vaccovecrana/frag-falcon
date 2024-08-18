package io.vacco.ff.schema;

import java.util.List;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class FgVmCreate extends FgRequest {

  // input parameters
  public FgVm vm;
  public FgNetConfig network;
  public boolean rebuildInitRamFs;

  // output parameters
  public List<String> warnings;

  public FgVmCreate withWarnings(List<String> warnings) {
    this.warnings = requireNonNull(warnings);
    return this;
  }

  public FgVmCreate withVm(FgVm vm) {
    this.vm = requireNonNull(vm);
    return this;
  }

  public FgVmCreate withNetwork(FgNetConfig network) {
    this.network = network;
    return this;
  }

  @Override public String toString() {
    return format(
      "[%s, %s%s]",
      vm != null && vm.image != null && vm.image.source != null
        ? vm.image.source
        : "",
      vm != null && vm.config != null && vm.config.bootsource != null
        ? vm.config.bootsource.kernel_image_path
        : "",
      vm != null && vm.config != null && vm.config.machineconfig != null
        ? format(" %svCpu, %sMiB",vm.config.machineconfig.vcpu_count, vm.config.machineconfig.mem_size_mib)
        : ""
    );
  }
}
