package io.vacco.ff;

import io.vacco.ff.net.FgProc;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static j8spec.J8Spec.it;
import static org.junit.Assert.*;

/**
 * Boots OCI images as microVMs in a native launcher process (ff-jni/fg_vmm)
 * spawned through FgProc, and verifies host-directory volumes.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgVmBootTest {

  static {
    it("boots Alpine in a native launcher process", () -> {
      var args = FgTest.baseArgs("it-basic");
      args.addAll(List.of("--", "/bin/sh", "-c", "echo hi"));
      var r = FgTest.runVm("it-basic", args);
      assertEquals(0, r.exitCode());
      assertTrue("expected 'hi' in console: " + r.console(), r.console().contains("hi"));
    });

    it("shares a host directory read-write", () -> {
      var host = FgTest.freshDir("vol-rw");
      Files.writeString(new File(host, "host.txt").toPath(), "from-host");

      var args = FgTest.baseArgs("it-rw");
      args.add("--volume");
      args.add(host.getAbsolutePath() + ":/data");
      args.addAll(List.of("--", "/bin/sh", "-c", "cat /data/host.txt; echo guest-wrote > /data/out.txt"));
      var r = FgTest.runVm("it-rw", args);

      assertEquals(0, r.exitCode());
      assertTrue("expected host file contents: " + r.console(), r.console().contains("from-host"));
      assertEquals("guest-wrote", Files.readString(new File(host, "out.txt").toPath()).trim());
    });

    it("enforces read-only volumes", () -> {
      var host = FgTest.freshDir("vol-ro");
      Files.writeString(new File(host, "host.txt").toPath(), "from-host");

      var args = FgTest.baseArgs("it-ro");
      args.add("--volume");
      args.add(host.getAbsolutePath() + ":/data:ro");
      args.addAll(List.of("--", "/bin/sh", "-c", "echo nope > /data/out.txt"));
      var r = FgTest.runVm("it-ro", args);

      assertNotEquals("write to a read-only volume should fail", 0, r.exitCode());
      assertFalse("no file should have been written", new File(host, "out.txt").exists());
    });

    it("re-discovers a running VM by FF_VMID", () -> {
      var args = FgTest.baseArgs("it-disc");
      args.addAll(List.of("--", "/bin/sh", "-c", "sleep 3; echo done"));

      var log = new File(FgTest.WORK, "it-disc.log");
      var pid = FgProc.spawn("it-disc", args, log.toPath());
      try {
        var found = -1;
        for (int i = 0; i < 100 && found < 0; i++) {
          found = FgProc.pidOf("it-disc");
          Thread.sleep(100);
        }
        assertEquals("FF_VMID discovery should find the running VM", pid, found);
      } finally {
        FgProc.waitProcess(pid, 120_000);
      }
    });
  }
}
