package io.vacco.ff;

import io.vacco.ff.oci.FgOciStore;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;

import static j8spec.J8Spec.it;
import static org.junit.Assert.*;

/**
 * Verifies the OCI store's cache/work split: blobs live on the cache root (may
 * be a slow disk) while extraction temp dirs live on the work root (next to the
 * VM rootfs, on fast storage). Also covers the startup temp sweep.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgOciStoreTest {

  static {
    it("keeps blobs on the cache root and temp on the work root", () -> {
      var cache = FgTest.freshDir("oci-store-cache");
      var work = FgTest.freshDir("oci-store-work");
      var store = new FgOciStore(cache, work);

      var blob = store.blobFile("sha256:deadbeef");
      assertEquals("blobs must live under the cache root", cache.getCanonicalFile(), blob.getParentFile().getParentFile().getCanonicalFile());
      assertEquals("blobs dir", new File(cache, FgOciStore.pBlobs).getCanonicalFile(), blob.getParentFile().getCanonicalFile());

      var tmp = store.newTmpDir("unzipped");
      assertEquals("temp must live under the work root", work.getCanonicalFile(), tmp.getParentFile().getParentFile().getCanonicalFile());
      assertTrue(tmp.isDirectory());
    });

    it("sweeps stale temp dirs but leaves the blob cache intact", () -> {
      var cache = FgTest.freshDir("oci-store-cache2");
      var work = FgTest.freshDir("oci-store-work2");
      var store = new FgOciStore(cache, work);

      var stale = new File(new File(work, FgOciStore.pTmp), "stale-orphan");
      assertTrue(stale.mkdirs());
      assertTrue(new File(stale, "layer.tar").createNewFile());

      var blob = store.blobFile("sha256:keepme");
      assertTrue(blob.createNewFile());

      store.sweepTmp();

      assertFalse("stale temp dir should be swept", Files.exists(stale.toPath()));
      assertTrue("blob cache must survive a sweep", blob.exists());
    });
  }
}
