package io.vacco.ff;

import io.vacco.ff.oci.FgDockerIo;
import io.vacco.ff.oci.FgOciStore;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static j8spec.J8Spec.it;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Regression: two extractions of the same image, sharing one blob store,
 * running concurrently must not clobber each other's expanded layers. This
 * mirrors the real race between the stack-op worker and the supervisor
 * restarting the same service (previously surfaced as a
 * {@code NoSuchFileException} deleting the shared {@code tmp/unzipped} dir).
 *
 * <p>Network-only; no KVM or capabilities required.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgOciConcurrencyTest {

  private static final String IMAGE = "docker.io/hashicorp/http-echo:latest";

  static {
    it("extracts the same image concurrently into two rootfs dirs", () -> {
      var store = new FgOciStore(new File(FgTest.WORK, "oci"), new File(FgTest.WORK, "oci"));
      var rootA = FgTest.freshDir("conc-a");
      var rootB = FgTest.freshDir("conc-b");

      var start = new CountDownLatch(1);
      var pool = Executors.newFixedThreadPool(2);
      var failures = new ArrayList<Throwable>();
      try {
        var futures = new ArrayList<Future<?>>();
        for (var root : new File[]{rootA, rootB}) {
          futures.add(pool.submit(() -> {
            try {
              start.await(10, TimeUnit.SECONDS);
              FgDockerIo.extract(IMAGE, root, store);
            } catch (Throwable t) {
              synchronized (failures) {
                failures.add(t);
              }
            }
          }));
        }
        start.countDown();
        for (var f : futures) {
          f.get(120, TimeUnit.SECONDS);
        }
      } finally {
        pool.shutdownNow();
      }

      if (!failures.isEmpty()) {
        fail("concurrent extraction failed: " + failures.get(0));
      }
      for (var root : new File[]{rootA, rootB}) {
        assertTrue("[" + root + "] expected extracted content",
          new File(root, "bin").exists() || new File(root, "usr").exists());
      }
    });
  }
}
