Move these tests over to ff-api, when Cloud hypervisor is ready to test:

```
it("Sends an API request to a Firecracker UNIX socket", localTest(() -> {
  var fcSock = new File("./build/fctest.sock");
  var args = new String[] { "--api-sock", fcSock.getAbsolutePath() };
  if (fcSock.exists()) {
    delete(fcSock, e -> { throw new IllegalStateException(e); });
  }
  var shm = new String[1];
  int pid = fork("fcTest", "/usr/local/bin/firecracker", args, 512, shm);
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
```