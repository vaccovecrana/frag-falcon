package io.vacco.ff;

import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.util.List;

import static j8spec.J8Spec.it;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression: libkrun terminates the launcher with {@code _exit()}, which
 * bypasses our log-ring flush. A fast-exiting workload used to leave
 * {@code vm.log} with only the launcher's own startup lines. The launcher now
 * interposes {@code _exit} to drain and flush before terminating.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgLogFlushTest {

  static {
    it("captures the console output of a fast-exiting workload", () -> {
      var args = FgTest.baseArgs("it-flush");
      args.addAll(List.of("--", "/bin/sh", "-c", "echo flush-marker"));

      var r = FgTest.runVm("it-flush", args);
      assertEquals("expected the guest command to succeed: " + r.console(), 0, r.exitCode());
      assertTrue("expected the launcher startup line: " + r.console(),
        r.console().contains("[fg-vmm] starting vm=it-flush"));
      assertTrue("expected the fast-exit workload output to be flushed: " + r.console(),
        r.console().contains("flush-marker"));
    });
  }
}
