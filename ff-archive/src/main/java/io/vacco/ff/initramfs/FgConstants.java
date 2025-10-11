package io.vacco.ff.initramfs;

public class FgConstants {

  public static final String fInitRamFs = "initramfs.cpio";

  public static final String
    FF_ENTRYPOINT = "FF_ENTRYPOINT",
    FF_CMD = "FF_CMD",
    FF_WORKINGDIR = "FF_WORKINGDIR";

  public static final String
    dockerOs = "linux", dockerArch = "amd64"; // TODO add support if demand grows.

  public static final String
    pBlobs = "blobs",
    pExtract = "extract",
    pUnzipped = "unzipped";

}
