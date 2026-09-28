package io.vacco.ff;

import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.util.List;

import static j8spec.J8Spec.it;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Boots an Alpine microVM with a bridged TAP device and verifies the guest
 * obtains an address via DHCP (libkrun's in-guest DHCP client) and can reach
 * the bridge gateway.
 *
 * <p>Hard-requires the full platform: {@code virbr0}, {@code /dev/kvm}, and
 * {@code cap_net_admin} on the launcher ({@code ff-jni/setup-caps.sh}). The
 * test fails (does not skip) when the platform is missing; build-only CI can
 * exclude it with {@code -PskipPrivilegedTests}.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgNetBootTest {

  private static final String BRIDGE = "virbr0";
  private static final String GATEWAY = "192.168.122.1";

  static {
    it("boots with a bridged TAP and obtains an address via DHCP", () -> {
      assertTrue(
        "this test requires cap_net_admin on the launcher; run ff-jni/setup-caps.sh",
        FgTest.hasNetCap()
      );
      var tap = "fftap" + ProcessHandle.current().pid();

      try {
        FgTest.runLauncherTool(List.of("--tap-up", "--tap", tap, "--bridge", BRIDGE));

        var args = FgTest.baseArgs("it-net");
        args.add("--tap");
        args.add(tap);
        args.addAll(List.of("--", "/bin/sh", "-c",
          "ok=0; for i in 1 2 3 4 5; do "
            + "if ping -c1 -W2 " + GATEWAY + " >/dev/null 2>&1; then ok=1; break; fi; "
            + "sleep 1; done; "
            + "ip -4 addr show eth0; "
            + "[ $ok -eq 1 ] && echo NET-OK"));

        var r = FgTest.runVm("it-net", args);
        assertEquals("expected the guest command to succeed: " + r.console(), 0, r.exitCode());
        assertTrue("expected NET-OK in console: " + r.console(), r.console().contains("NET-OK"));
        assertTrue("expected a DHCP address on eth0: " + r.console(), r.console().contains("192.168.122."));
      } finally {
        FgTest.runLauncherTool(List.of("--tap-down", "--tap", tap));
      }
    });
  }
}
