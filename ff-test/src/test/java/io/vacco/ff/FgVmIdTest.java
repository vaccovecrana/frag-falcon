package io.vacco.ff;

import io.vacco.ff.service.FgVmId;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import static j8spec.J8Spec.it;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgVmIdTest {

  static {
    it("derives a deterministic, stable VM id", () -> {
      var a = FgVmId.of("crow", "server");
      var b = FgVmId.of("crow", "server");
      var c = FgVmId.of("crow", "agent-0");
      assertEquals(a, b);
      assertNotEquals(a, c);
      assertTrue(a.length() > 0 && a.length() <= 8);
    });

    it("produces comm/tap names within the 15-char limit", () -> {
      assertTrue(FgVmId.procTag("deadbeef").length() <= 15);
      assertTrue(FgVmId.tapName("deadbeef").length() <= 15);
      assertTrue(FgVmId.procTag("deadbeef").startsWith("ff-"));
      assertTrue(FgVmId.tapName("deadbeef").startsWith("ff"));
    });

    it("derives a locally-administered MAC address", () -> {
      var mac = FgVmId.macString("deadbeef");
      assertTrue(mac.matches("52:54:00:[0-9a-f]{2}:[0-9a-f]{2}:[0-9a-f]{2}"));
      assertEquals(mac, FgVmId.macString("deadbeef"));
    });
  }
}
