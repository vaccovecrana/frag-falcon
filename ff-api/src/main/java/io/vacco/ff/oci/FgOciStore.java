package io.vacco.ff.oci;

import java.io.File;

/**
 * Persistent, content-addressable cache for OCI layer blobs.
 *
 * <p>Blobs are keyed by their registry digest and reused across VM builds, so an
 * image pulled once is not downloaded again for subsequent VMs.
 */
public class FgOciStore {

  public static final String pBlobs = "blobs", pTmp = "tmp";

  private final File root;

  public FgOciStore(File root) {
    this.root = root;
    FgOciIo.mkDirs(root);
  }

  public File root() {
    return root;
  }

  public File blobsDir() {
    var d = new File(root, pBlobs);
    FgOciIo.mkDirs(d);
    return d;
  }

  public File blobFile(String digest) {
    return new File(blobsDir(), digest);
  }

  public File tmpDir(String name) {
    var d = new File(new File(root, pTmp), name);
    FgOciIo.mkDirs(d);
    return d;
  }
}
