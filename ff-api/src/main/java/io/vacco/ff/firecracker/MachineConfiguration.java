package io.vacco.ff.firecracker;

import io.vacco.ff.firecracker.machineconfiguration.Huge_pages;
import java.lang.Boolean;
import java.lang.Long;

/**
 * Describes the number of vCPUs, memory size, SMT capabilities, huge page configuration and the CPU template.
 */
public class MachineConfiguration {
  public CpuTemplate cpu_template;

  public Huge_pages huge_pages;

  public Long mem_size_mib;

  public Boolean smt;

  public Boolean track_dirty_pages;

  public Long vcpu_count;

  public MachineConfiguration cpu_template(CpuTemplate cpu_template) {
    this.cpu_template = cpu_template;
    return this;
  }

  /**
   * Which huge pages configuration (if any) should be used to back guest memory.
   */
  public MachineConfiguration huge_pages(Huge_pages huge_pages) {
    this.huge_pages = huge_pages;
    return this;
  }

  /**
   * Memory size of VM
   */
  public MachineConfiguration mem_size_mib(Long mem_size_mib) {
    this.mem_size_mib = mem_size_mib;
    return this;
  }

  /**
   * Flag for enabling/disabling simultaneous multithreading. Can be enabled only on x86.
   */
  public MachineConfiguration smt(Boolean smt) {
    this.smt = smt;
    return this;
  }

  /**
   * Enable dirty page tracking. If this is enabled, then incremental guest memory snapshots can be created. These belong to diff snapshots, which contain, besides the microVM state, only the memory dirtied since a previous snapshot. Full snapshots each contain a full copy of the guest memory.
   */
  public MachineConfiguration track_dirty_pages(Boolean track_dirty_pages) {
    this.track_dirty_pages = track_dirty_pages;
    return this;
  }

  /**
   * Number of vCPUs (either 1 or an even number)
   */
  public MachineConfiguration vcpu_count(Long vcpu_count) {
    this.vcpu_count = vcpu_count;
    return this;
  }

  public static MachineConfiguration machineConfiguration() {
    return new MachineConfiguration();
  }
}
