# frag-falcon

A libkrun-based microVM hypervisor. It pulls OCI/Docker images and runs them as
microVMs whose root filesystem is a host directory shared over virtiofs, so
containers can be given host directories as volumes without raw disk images.

- **Frameworks**: `murmux` (HTTP), `ronove` (REST + TS codegen), `shax` (logging).
- **VM backplane**: libkrun 2.0 (with a bounded-log launcher and kernel-confined
  OCI layer extraction).
- **Packaging**: a single GraalVM native executable plus the native launcher and
  libkrun shared objects, shipped as a flat `tar.gz`.

## Build

Toolchain: **Java 25** (with GraalVM native-image) and **Gradle** on `PATH`.

```bash
gradle build                 # compile + tests
gradle :flc:distNativeTar    # build the release distribution
```

The distribution lands in `ff-app/build/distributions/frag-falcon-<version>.tar.gz`
and unpacks to a flat directory:

```
frag-falcon-<version>/
  ff-app              # the hypervisor (GraalVM native executable)
  fg_vmm              # per-VM native launcher
  fg_jni.so           # host primitives (JNI)
  libkrun.so.2
  libkrun_init.so
  libkrunfw.so.5
```

Run it from that directory: the executable finds `fg_vmm` and the libkrun
libraries beside itself. **Run as root** — the hypervisor manages TAP devices and
bind mounts, and root needs no extra capabilities.

## Options

```
--vm-dir=PATH        VM storage directory (required)
--bridge=NAME        Linux bridge for VM TAPs (optional)
--api-host=HOST      API bind address (default 127.0.0.1)
--api-port=PORT      API port (default 7070)
--log-format=FORMAT  text|json (default text)
--log-level=LEVEL    error|warning|info|debug|trace (default info)
```

The hypervisor is **not meant to be exposed publicly**: it has no authentication
and manages privileged networking. Keep it on a private LAN segment or behind a
VPN.

## API

All operations are stack-oriented (a stack is a compose-style set of services,
stored as JSON). The UI converts YAML to JSON client-side.

```
GET    /api/v1/stack            list stacks with derived state
GET    /api/v1/stack/{id}       fetch a stack definition
POST   /api/v1/stack            create/update a stack
DELETE /api/v1/stack/{id}       stop and delete a stack
POST   /api/v1/stack/start      start a stack (topological order)
POST   /api/v1/stack/stop       stop a stack (reverse order)
POST   /api/v1/stack/logs       per-service log tails
GET    /api/v1/br               list Linux bridges
```

## systemd

`/etc/systemd/system/frag-falcon.service`:

```ini
[Unit]
Description=frag-falcon microVM hypervisor
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/frag-falcon
ExecStart=/opt/frag-falcon/ff-app --vm-dir=/var/lib/frag-falcon --bridge=br0 --api-host=0.0.0.0 --api-port=7070
Restart=on-failure
RestartSec=2

[Install]
WantedBy=multi-user.target
```

Note: VMs are **not** killed when the service stops — they are independent
launcher processes and are re-adopted on the next start.

## Layout

```
ff-jni    Host primitives (JNI): process spawn, kernel-confined tar extraction,
          bridge discovery; the C VM launcher; vendored libkrun libs.
ff-api    Domain model, VM lifecycle, stack model + supervisor, the ronove REST
          API, and the OCI client.
ff-app    Packaging: the GraalVM native executable and the release distribution.
ff-test   Integration and security tests.
```

See `PLAN.md` for the migration roadmap and `CAVEATS.md` for integration caveats
(host-directory volumes, bounded logs, kernel-confined extraction, and more).
