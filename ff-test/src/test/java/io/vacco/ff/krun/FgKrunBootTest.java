package io.vacco.ff.krun;

import io.vacco.ff.oci.FgDockerIo;
import io.vacco.ff.oci.FgOciStore;
import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static j8spec.J8Spec.*;
import static org.junit.Assert.*;

/**
 * Integration test: pull an OCI image into a host rootfs directory, then boot it
 * as a microVM in a <b>separate process</b> (the ff-vmm launcher) and assert on
 * the guest console.
 *
 * <p>This mirrors the production architecture: libkrun {@code _exit()}s its host
 * process on guest shutdown, so the VM must never share the hypervisor's JVM.
 */
@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgKrunBootTest {

  private static final File WORK = new File("./build/it");

  static {
    it("boots an Alpine rootfs in a child process and runs a command", () -> {
      Files.createDirectories(WORK.toPath());

      var rootfs = new File(WORK, "rootfs");
      var store = new FgOciStore(new File(WORK, "oci"));

      var img = FgDockerIo.extract("alpine:latest", rootfs, store,
          (entry, err) -> System.out.printf("oci: %s%n", err.getMessage()));
      System.out.printf("libkrun: extracted %s -> %s%n", img.source, img.rootDir);

      var outFile = new File(WORK, "vmm.out");
      var javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
      var classpath = System.getProperty("java.class.path");

      var pb = new ProcessBuilder(
          javaBin,
          "--enable-native-access=ALL-UNNAMED",
          "-cp", classpath,
          "io.vacco.ff.vmm.FgVmmMain",
          "--rootfs", rootfs.getAbsolutePath(),
          "--vcpus", "2",
          "--ram", "256",
          "--workdir", "/",
          "--env", "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
          "--", "/bin/sh", "-c", "echo hi"
      );
      pb.environment().put("FF_VMID", "it-alpine");
      pb.redirectErrorStream(true);
      pb.redirectOutput(outFile);

      var proc = pb.start();
      var exited = proc.waitFor(90, TimeUnit.SECONDS);
      var out = Files.exists(outFile.toPath()) ? Files.readString(outFile.toPath()) : "";

      if (!exited) {
        proc.destroyForcibly();
      }
      System.out.printf("libkrun: ff-vmm exit=%s, console:%n%s%n", exited ? proc.exitValue() : "timeout", out);

      assertTrue("ff-vmm did not exit within the timeout", exited);
      assertEquals("guest exit code", 0, proc.exitValue());
      assertTrue("expected guest output to contain 'hi', got: " + out, out.contains("hi"));
    });
  }
}
