package io.vacco.ff.krun;

import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgKrunSmokeTest {

  static {
    it("loads the vendored libkrun libraries", () -> {
      assertNotNull(FgKrunLib.libDir());
      assertTrue(FgKrunLib.libDir().toFile().isDirectory());
    });

    it("resolves and calls krun_check_nested_virt", () -> {
      var nested = FgKrun.checkNestedVirt();
      System.out.printf("libkrun: nested virtualization = %s%n", nested);
    });

    it("initializes libkrun logging", () -> {
      var result = FgKrun.initLog(
          1,
          FgKrun.KRUN_LOG_LEVEL_INFO,
          FgKrun.KRUN_LOG_STYLE_ALWAYS,
          0
      );
      System.out.printf("libkrun: krun_init_log -> %d (%s)%n", result, FgKrun.resultName(result));
      assertEquals(FgKrun.KRUN_RESULT_SUCCESS, result);
    });
  }
}
