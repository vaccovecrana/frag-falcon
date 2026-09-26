package io.vacco.ff;

import io.vacco.shax.logging.ShOption;
import j8spec.UnsafeBlock;
import java.awt.*;

public class FgTest {

  public static void initLog() {
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_DEVMODE, "true");
    ShOption.setSysProp(ShOption.IO_VACCO_SHAX_LOGLEVEL, "debug");
  }

  public static UnsafeBlock localTest(UnsafeBlock test) {
    if (!GraphicsEnvironment.isHeadless()) {
      return test;
    }
    return () -> System.out.println("CI/CD, nothing to do");
  }

}
