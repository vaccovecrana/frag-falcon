package io.vacco.ff.firecracker;

import io.vacco.ff.firecracker.cpuconfig.Cpuid_modifiers;
import io.vacco.ff.firecracker.cpuconfig.Msr_modifiers;
import io.vacco.ff.firecracker.cpuconfig.Reg_modifiers;

/**
 * The CPU configuration template defines a set of bit maps as modifiers of flags accessed by register to be disabled/enabled for the microvm.
 */
public class CpuConfig {
  public Cpuid_modifiers cpuid_modifiers;

  public Msr_modifiers msr_modifiers;

  public Reg_modifiers reg_modifiers;

  /**
   * A collection of CPUIDs to be modified. (x86_64)
   */
  public CpuConfig cpuid_modifiers(Cpuid_modifiers cpuid_modifiers) {
    this.cpuid_modifiers = cpuid_modifiers;
    return this;
  }

  /**
   * A collection of model specific registers to be modified. (x86_64)
   */
  public CpuConfig msr_modifiers(Msr_modifiers msr_modifiers) {
    this.msr_modifiers = msr_modifiers;
    return this;
  }

  /**
   * A collection of registers to be modified. (aarch64)
   */
  public CpuConfig reg_modifiers(Reg_modifiers reg_modifiers) {
    this.reg_modifiers = reg_modifiers;
    return this;
  }

  public static CpuConfig cpuConfig() {
    return new CpuConfig();
  }
}
