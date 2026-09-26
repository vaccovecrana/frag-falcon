package io.vacco.ff.firecracker;

import java.lang.String;

/**
 * Boot source descriptor.
 */
public class BootSource {
  public String boot_args;

  public String initrd_path;

  public String kernel_image_path;

  /**
   * Kernel boot arguments
   */
  public BootSource boot_args(String boot_args) {
    this.boot_args = boot_args;
    return this;
  }

  /**
   * Host level path to the initrd image used to boot the guest
   */
  public BootSource initrd_path(String initrd_path) {
    this.initrd_path = initrd_path;
    return this;
  }

  /**
   * Host level path to the kernel image used to boot the guest
   */
  public BootSource kernel_image_path(String kernel_image_path) {
    this.kernel_image_path = kernel_image_path;
    return this;
  }

  public static BootSource bootSource() {
    return new BootSource();
  }
}
