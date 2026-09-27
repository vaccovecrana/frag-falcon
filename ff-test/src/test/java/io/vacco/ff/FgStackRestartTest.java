package io.vacco.ff;

import com.google.gson.Gson;
import io.vacco.ff.schema.FgService;
import io.vacco.ff.schema.FgStack;
import io.vacco.ff.schema.FgStackTag;
import io.vacco.ff.service.FgStackSvc;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;

import static j8spec.J8Spec.it;
import static org.junit.Assert.assertTrue;

/**
 * M5 supervisor: a service with {@code restart: always} is restarted by the
 * polling supervisor after its guest exits.
 *
 * <p>Runs with no bridge (no NIC) so it needs no capabilities and boots fast.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgStackRestartTest {

  static {
    it("restarts a service per its restart policy", () -> {
      var vmDir = FgTest.freshDir("m5restart");
      var stackId = "m5restart";
      var svc = new FgStackSvc(vmDir, null, new Gson());
      try {
        var stack = new FgStack();
        stack.tag = FgStackTag.of(stackId);
        var s = new FgService();
        s.image = "alpine:latest";
        s.restart = "always";
        s.command = List.of("/bin/sh", "-c", "echo tick; sleep 1");
        stack.services.put("svc", s);
        svc.save(stack);

        svc.start(stackId);

        var pids = new HashSet<Integer>();
        for (int i = 0; i < 120 && pids.size() < 2; i++) {
          var st = svc.status(stackId);
          var ss = st.services.get("svc");
          if (ss != null && ss.pid > 0) {
            pids.add(ss.pid);
          }
          Thread.sleep(100);
        }
        System.out.printf("libkrun: observed restart pids=%s%n", pids);
        assertTrue("expected the service to be restarted (distinct pids): " + pids, pids.size() >= 2);
      } finally {
        svc.close();
      }
    });
  }
}
