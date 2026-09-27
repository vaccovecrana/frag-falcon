package io.vacco.ff.net;

import java.io.File;

/**
 * Kernel-confined root-directory operations (openat2 RESOLVE_IN_ROOT), used by
 * the OCI extractor so entry paths can never escape the target rootfs.
 */
public class FgRoot {

  public static void extractTar(File rootDir, File tar) {
    int result = FgJni.extractTar(tar.getAbsolutePath(), rootDir.getAbsolutePath());
    if (result != 0) {
      throw new IllegalStateException("Secure tar extraction failed (" + result + "): "
        + FgJni.strerror(-result));
    }
  }
}
