package io.vacco.ff;

import io.vacco.ff.util.FgMounts;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.util.List;

import static j8spec.J8Spec.it;
import static org.junit.Assert.*;

/**
 * The hypervisor runs unprivileged and only <em>audits</em> mount-flag hardening
 * (the OS applies it), so this is a pure parser test — no privileges required.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgMountsTest {

  static {
    it("selects the longest covering mount and detects all three flags", () -> {
      var mi = FgMounts.parse(String.join("\n",
        "20 1 8:1 / / rw,relatime - ext4 /dev/sda1 rw",
        "30 20 0:50 / /srv rw,relatime - ext4 /dev/sdb1 rw",
        "40 30 0:51 / /srv/ff rw,nosuid,nodev,noexec - ext4 /dev/sdc1 rw"
      ));
      var m = FgMounts.check("/srv/ff/stacks/one/rootfs", mi);
      assertEquals("/srv/ff", m.mountPoint());
      assertTrue(m.hardened());
      assertTrue(m.missing().isEmpty());
    });

    it("reports the missing flags", () -> {
      var mi = FgMounts.parse("40 30 0:51 / /srv/ff rw,nosuid - ext4 /dev/sdc1 rw");
      var m = FgMounts.check("/srv/ff", mi);
      assertFalse(m.hardened());
      assertEquals(List.of("nodev", "noexec"), m.missing());
    });

    it("falls back to / when nothing covers the path", () -> {
      var mi = FgMounts.parse("40 30 0:51 / /srv/ff rw,nosuid,nodev,noexec - ext4 /dev/sdc1 rw");
      var m = FgMounts.check("/other", mi);
      assertEquals("/", m.mountPoint());
      assertFalse(m.hardened());
    });

    it("decodes escaped mount points", () -> {
      var mi = FgMounts.parse("40 30 0:51 / /srv/my\\040ff rw,nosuid,nodev,noexec - ext4 /dev/sdc1 rw");
      var m = FgMounts.check("/srv/my ff", mi);
      assertEquals("/srv/my ff", m.mountPoint());
      assertTrue(m.hardened());
    });

    it("requires an exact prefix boundary, not a sibling name", () -> {
      var mi = FgMounts.parse("40 30 0:51 / /srv/ff rw,nosuid,nodev,noexec - ext4 /dev/sdc1 rw");
      var m = FgMounts.check("/srv/ffx/rootfs", mi);
      assertEquals("/", m.mountPoint());
      assertFalse(m.hardened());
    });
  }
}
