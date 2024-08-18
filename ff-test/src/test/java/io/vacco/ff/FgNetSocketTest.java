package io.vacco.ff;

import j8spec.annotation.DefinedOrder;
import j8spec.junit.J8SpecRunner;
import org.junit.runner.RunWith;

import java.io.File;

import static java.lang.Thread.sleep;
import static j8spec.J8Spec.*;
import static org.junit.Assert.*;
import static io.vacco.ff.net.FgJni.*;
import static io.vacco.ff.util.FgIo.*;
import static io.vacco.ff.FgTest.localTest;
import static io.vacco.ff.service.FgFirecracker.fcMachineConfigOf;

@DefinedOrder
@RunWith(J8SpecRunner.class)
public class FgNetSocketTest {
  static {
    it("Forks a detached Linux process", () -> {
      int pid = fork("BEE", "/usr/bin/env", new String[] { }, "./build/bee.log");
      System.out.println("PID: " + pid);
    });
    it("Finds a process by environment variable", () -> {
      int pid = pidOf("./src/test/resources/proc", "1984");
      System.out.println("PID: " + pid);
    });
    it("Sends an API request to a Firecracker UNIX socket", localTest(() -> {
      var fcSock = new File("./build/fctest.sock");
      var args = new String[] { "--api-sock", fcSock.getAbsolutePath() };
      if (fcSock.exists()) {
        delete(fcSock, e -> { throw new IllegalStateException(e); });
      }
      int pid = fork("fcTest", "/usr/local/bin/firecracker", args, "./build/fcTest.log");
      sleep(2000);
      var cfg = fcMachineConfigOf(fcSock);
      System.out.println("VM status: " + cfg.body);
      assertEquals(200, cfg.statusCode);
      terminate(pid);
    }));
    it("Sends a raw message to a VSOCK socket", localTest(() -> {
      var args = new String[] { "vsock-listen:1234,fork", "EXEC:'/bin/cat'" };
      var pid = fork("vSockTest", "/usr/bin/socat", args, "./build/vSockTest.log");
      sleep(2000);
      var buff = new byte[64];
      var sock = vSocketOpen("2:1234");
      vSockSend(sock, "Hello vSocket".getBytes());
      vSockReceive(sock, buff);
      vSocketClose(sock);
      var data = new String(buff).trim();
      System.out.println(data);
      terminate(pid);
    }));
  }
}
