package io.vacco.ff;

import io.vacco.ff.oci.FgImage;
import io.vacco.ff.oci.FgOciProgress;
import io.vacco.ff.oci.FgOciStore;
import io.vacco.ff.schema.FgVm;
import io.vacco.ff.schema.FgVmState;
import io.vacco.ff.service.FgVmId;
import io.vacco.ff.service.FgVmSvc;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;

import static io.vacco.ff.FgTest.log;
import static j8spec.J8Spec.it;
import static org.junit.Assert.assertTrue;

/**
 * M5a: verifies the low-level VM lifecycle (build / start / running / stop)
 * through the service layer, independent of stacks and REST.
 *
 * <p>Requires the launcher's {@code cap_net_admin} (see ff-jni/setup-caps.sh) and
 * the {@code virbr0} bridge; otherwise it no-ops.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgVmLifecycleTest {

  private static final String BRIDGE = "virbr0";

  static {
    it("provisions, starts, reports running, and stops a VM", () -> {
      if (!FgTest.hasNetCap()) {
        log.info("libkrun: skipping VM lifecycle test (no cap_net_admin)");
        return;
      }
      var stackId = "m5a";
      var svcId = "svc";
      var id = FgVmId.of(stackId, svcId);
      var vmRoot = new File(FgTest.WORK, "m5a/" + id);

      var vm = new FgVm();
      vm.tag.id = id;
      vm.image = new FgImage();
      vm.image.source = "alpine:latest";
      vm.command = List.of("/bin/sh", "-c", "echo m5a-ok; sleep 5");
      vm.network = null;

      var store = new FgOciStore(new File(FgTest.WORK, "m5a-oci"));
      try {
        var pid = FgVmSvc.start(vm, vmRoot, store, FgOciProgress.NOOP);
        assertTrue("expected a launcher pid", pid > 0);

        var running = false;
        for (int i = 0; i < 50 && !running; i++) {
          running = FgVmSvc.statusOf(vm).state == FgVmState.running;
          Thread.sleep(100);
        }
        assertTrue("expected the VM to be reported running", running);

        var stopped = false;
        for (int i = 0; i < 100 && !stopped; i++) {
          stopped = FgVmSvc.statusOf(vm).state == FgVmState.stopped;
          Thread.sleep(100);
        }
        assertTrue("expected the guest to exit", stopped);

        var logs = FgVmSvc.logs(vmRoot);
        log.info("libkrun: m5a vm logs:\n{}", logs);
        assertTrue("expected guest output in vm.log", logs.contains("m5a-ok"));
      } finally {
        FgVmSvc.stop(vm);
      }
    });
  }
}
