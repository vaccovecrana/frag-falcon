package io.vacco.ff.oci;

import io.vacco.ff.util.FgIo;

import java.io.File;

/**
 * Persistent, content-addressable cache for OCI layer blobs.
 *
 * <p>Blobs are keyed by their registry digest and reused across VM builds, so an
 * image pulled once is not downloaded again for subsequent VMs.
 *
 * <p>The cache root ({@code blobs/}) and the extraction work root ({@code tmp/})
 * are deliberately separate directories: the blob cache is read-mostly bulk
 * storage and may live on a slower disk, while the transient expanded layers are
 * written and read on the hot path and belong next to the VM rootfs (fast disk).
 */
public class FgOciStore {

  public static final String pBlobs = "blobs", pTmp = "tmp";

  private final File cacheRoot;
  private final File workRoot;

  public FgOciStore(File cacheRoot, File workRoot) {
    this.cacheRoot = cacheRoot;
    this.workRoot = workRoot;
    FgIo.mkDirs(cacheRoot);
    FgIo.mkDirs(workRoot);
  }

  public File cacheRoot() {
    return cacheRoot;
  }

  public File workRoot() {
    return workRoot;
  }

  public File blobsDir() {
    var d = new File(cacheRoot, pBlobs);
    FgIo.mkDirs(d);
    return d;
  }

  public File blobFile(String digest) {
    return new File(blobsDir(), digest);
  }

  private File tmpBase() {
    var d = new File(workRoot, pTmp);
    FgIo.mkDirs(d);
    return d;
  }

  public File tmpDir(String name) {
    var d = new File(tmpBase(), name);
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
    var base = tmpBase();
    final java.util.concurrent.atomic.AtomicInteger seq = new java.util.concurrent.atomic.AtomicInteger();
    for (; ; ) {
      var d = new File(base, prefix + "-" + ProcessHandle.current().pid() + "-" + System.nanoTime() + "-" + seq.getAndIncrement());
      if (d.mkdirs()) {
        return d;
      }
    }
  }

  /**
   * Deletes every entry under the work {@code tmp/} area. Temp dirs are removed
   * in a {@code finally} during normal extraction, but a crash can orphan them;
   * callers invoke this once at startup to reclaim space.
   */
  public void sweepTmp() {
    var base = new File(workRoot, pTmp);
    var entries = base.listFiles();
    if (entries != null) {
      for (var e : entries) {
        FgIo.delete(e, err -> {
        });
      }
    }
  }
}
