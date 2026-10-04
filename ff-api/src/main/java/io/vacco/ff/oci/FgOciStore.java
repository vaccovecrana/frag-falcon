package io.vacco.ff.oci;

import io.vacco.ff.util.FgIo;

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
    FgIo.mkDirs(root);
  }

  public File root() {
    return root;
  }

  public File blobsDir() {
    var d = new File(root, pBlobs);
    FgIo.mkDirs(d);
    return d;
  }

  public File blobFile(String digest) {
    return new File(blobsDir(), digest);
  }

  public File tmpDir(String name) {
    var d = new File(new File(root, pTmp), name);
    FgIo.mkDirs(d);
    return d;
  }

  /**
   * Creates a fresh, uniquely-named temporary directory under the shared store's
   * {@code tmp/} area. Callers must delete it when done. Uniqueness lets
   * concurrent extractions (e.g. two services pulling the same image) not
   * clobber each other's expanded layers.
   */
  public File newTmpDir(String prefix) {
    var base = new File(root, pTmp);
    FgIo.mkDirs(base);
    final java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger();
    for (; ; ) {
      var d = new File(base, prefix + "-" + ProcessHandle.current().pid() + "-" + System.nanoTime() + "-" + seq.getAndIncrement());
      if (d.mkdirs()) {
        return d;
      }
    }
  }
}
