package io.vacco.ff;

import io.vacco.ff.service.FgVmId;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.util.List;

import static j8spec.J8Spec.it;
import static org.junit.Assert.*;

/**
 * Per-image boot test: boots {@code grycap/cowsay} as a microVM attached to the
 * {@code virbr0} bridge, over DHCP, and reproduces the output of
 * {@code docker run --rm grycap/cowsay /usr/games/cowsay "Hello from Docker!"}.
 *
 * <p>This is hard-required: it needs {@code cap_net_admin} on the launcher and
 * fails (not skips) when it is missing. Run {@code ff-jni/setup-caps.sh}.
 *
 * <p>The {@code sleep} after cowsay is deliberate: libkrun {@code _exit()}s the
 * launcher when the guest exits, and the launcher's bounded log ring
 * (CAVEATS §12) only flushes periodically, so a fast-exiting workload can lose
 * its whole console output. Keeping the process alive lets the art land in
 * {@code vm.log}.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgCowsayBootTest {

  private static final String BRIDGE = "virbr0";
  private static final String VM_ID = "it-cowsay";

  static {
    it("boots grycap/cowsay on virbr0 and prints the cow", () -> {
      assertTrue(
        "this test requires cap_net_admin on the launcher; run ff-jni/setup-caps.sh",
        FgTest.hasNetCap()
      );

      var tap = "ffcow" + Integer.toHexString(VM_ID.hashCode() & 0xffff)
        + Integer.toHexString((int) (System.nanoTime() & 0xffff));

      try {
        FgTest.runLauncherTool(List.of("--tap-up", "--tap", tap, "--bridge", BRIDGE));

        var args = FgTest.baseArgs(VM_ID, FgTest.cowsayRootfs());
        args.add("--tap");
        args.add(tap);
        args.add("--mac");
        args.add(FgVmId.macString(VM_ID));
        args.addAll(List.of("--", "/bin/sh", "-c",
          "/usr/games/cowsay \"Hello from Docker!\"; "
            + "ip -4 addr show eth0; "
            + "sleep 10"));

        var r = FgTest.runVm(VM_ID, args);
        var console = r.console();

        assertEquals("expected the guest command to succeed: " + console, 0, r.exitCode());
        assertTrue("expected the cowsay banner: " + console,
          console.contains("Hello from Docker!"));
        assertTrue("expected the cow art: " + console, console.contains("^__^"));
        assertTrue("expected the cow art: " + console, console.contains("(oo)\\_______"));
        assertTrue("expected a DHCP address on eth0: " + console,
          console.contains("192.168.122."));
      } finally {
        try {
          FgTest.runLauncherTool(List.of("--tap-down", "--tap", tap));
        } catch (Exception ignored) {
        }
      }
    });
  }
}
