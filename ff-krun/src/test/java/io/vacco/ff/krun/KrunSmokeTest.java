package io.vacco.ff.krun;

import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class KrunSmokeTest {

  static {
    it("loads the vendored libkrun libraries", () -> {
      assertNotNull(KrunLib.libDir());
      assertTrue(KrunLib.libDir().toFile().isDirectory());
    });

    it("resolves and calls krun_check_nested_virt", () -> {
      var nested = Krun.checkNestedVirt();
      System.out.printf("libkrun: nested virtualization = %s%n", nested);
    });

    it("initializes libkrun logging", () -> {
      var result = Krun.initLog(
          1,
          Krun.KRUN_LOG_LEVEL_INFO,
          Krun.KRUN_LOG_STYLE_ALWAYS,
          0
      );
      System.out.printf("libkrun: krun_init_log -> %d (%s)%n", result, Krun.resultName(result));
      assertEquals(Krun.KRUN_RESULT_SUCCESS, result);
    });
  }
}
