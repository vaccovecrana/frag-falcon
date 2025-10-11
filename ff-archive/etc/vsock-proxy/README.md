## Overview

This is experimental code that can proxy a UNIX socket in the guest VM to a UNIX socket in the host machine.

It could be used to grant access to a Docker socket on the host machine.

- On the Guest side, a goroutine would need to implement `vsock-proxy.c`.
- On the Host side, a Java thread would need to implement `unix-proxy.c`.
