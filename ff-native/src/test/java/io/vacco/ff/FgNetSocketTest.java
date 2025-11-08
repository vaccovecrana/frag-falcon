package io.vacco.ff;

import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import static j8spec.J8Spec.*;
import static io.vacco.ff.net.FgJni.*;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgNetSocketTest {
  static {
    it("Forks a detached Linux process", () -> {
      var shm = new String[1];
      int pid = fork("BEE", "/usr/bin/env", new String[] { }, 512, shm);
      System.out.println("PID: " + pid);
    });
    it("Finds a process by environment variable", () -> {
      int pid = pidOf("./src/test/resources/proc", "1984");
      System.out.println("PID: " + pid);
    });

  }
}
