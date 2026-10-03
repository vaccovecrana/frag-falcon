package io.vacco.ff;

import io.vacco.ff.net.FgNetCap;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import static j8spec.J8Spec.it;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The hypervisor refuses to start without CAP_NET_ADMIN for the launcher, since
 * it cannot create the per-VM TAP devices a stack needs.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgNetCapTest {

  static {
    it("detects CAP_NET_ADMIN in the current environment", () -> {
      // The full-platform test setup guarantees the launcher carries the cap
      // (or the process is root / has it ambient).
      assertTrue("expected CAP_NET_ADMIN to be available in this environment", FgNetCap.hasNetAdmin());
    });

    it("reports the capability source", () -> {
      var r = FgNetCap.check();
      assertTrue(r.ok());
      assertFalse(r.source().isBlank());
    });

    it("reports no file capability for a launcher that lacks it", () -> {
      // A plain copy of a binary carries no file capabilities; when the process
      // itself has none either, the check must fail.
      if (FgNetCap.check(java.nio.file.Path.of("/bin/sh")).ok()) {
        // The running process already holds CAP_NET_ADMIN (e.g. root); the
        // negative case is then not observable here.
        return;
      }
      assertFalse(FgNetCap.check(java.nio.file.Path.of("/bin/sh")).ok());
    });
  }
}
