package io.vacco.ff;

import io.vacco.ff.service.FgOptions;
import io.vacco.ff.service.*;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;
import java.io.File;

import static j8spec.J8Spec.*;
import static io.vacco.ff.FgTest.localTest;
import static io.vacco.ff.util.FgIo.mkDirs;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgContextTest {

  public static UnsafeBlock localTest(UnsafeBlock test) {
    if (!GraphicsEnvironment.isHeadless()) {
      return test;
    }
    return () -> System.out.println("CI/CD, nothing to do");
  }

  static {
    it("Starts a test app context", localTest(() -> {
      FgOptions.setFrom(new String[]{
        "--vm-dir=./build/vms",
        "--krn-dir=./src/test/resources/kernel",
        "--fc-path=/usr/local/bin/firecracker",
        "--log-level=debug"
      });
      var vmd = new File("./build/vms");
      mkDirs(vmd);
      try (var ctx = new FgContext()) {
        ctx.init();
        // Thread.sleep(Integer.MAX_VALUE);
      }
    }));
  }
}
