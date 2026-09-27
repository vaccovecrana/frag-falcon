package io.vacco.ff.schema;

/** Per-VM compute resources. Defaults to 1 vCPU / 512 MiB when unspecified. */
public class FgVmMachine {

  public static final int DEFAULT_VCPUS = 1;
  public static final int DEFAULT_RAM_MIB = 512;

  public int vcpus = DEFAULT_VCPUS;
  public int ramMib = DEFAULT_RAM_MIB;

  public FgVmMachine effective() {
    if (vcpus <= 0) {
      vcpus = DEFAULT_VCPUS;
    }
    if (ramMib <= 0) {
      ramMib = DEFAULT_RAM_MIB;
    }
    return this;
  }
}
