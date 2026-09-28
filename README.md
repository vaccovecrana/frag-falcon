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
libraries beside itself.

### Rootless deployment

frag-falcon is designed to run **unprivileged**. Privilege is delegated to the
OS once, not reimplemented in the hypervisor:

- a dedicated service user owns the VM storage dir (`--vm-dir`);
- the `fg_vmm` launcher carries `cap_net_admin` (for TAP devices) — `setcap`, or
  systemd `AmbientCapabilities=CAP_NET_ADMIN`;
- the service user is in the **`kvm`** group (for `/dev/kvm`);
- the VM storage dir is mounted **`nosuid,nodev,noexec`** (host-wide, e.g. via
  fstab) so files a guest plants in its rootfs (setuid binaries, device nodes,
  executables) are inert to host-side processes. The hypervisor audits this at
  startup and logs a warning if it is missing.

`deploy/setup.sh <user> <vm-dir> [install-dir]` performs the one-time setup, and
`deploy/flc.service` is a sample unit. The hypervisor never needs root.

```
--vm-dir=PATH        VM storage directory (required)
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

A rootless sample unit ships in `deploy/flc.service` (service user, `kvm` group,
`AmbientCapabilities=CAP_NET_ADMIN`). Provision it once with
`sudo bash deploy/setup.sh <user> <vm-dir> [install-dir]`, which also prints the
`nosuid,nodev,noexec` mount for the vm-dir.

Note: VMs are **not** killed when the service stops — they are independent
launcher processes and are re-adopted on the next start.

## UI

The Preact SPA is bundled into `flc` (see `ff-ui/`). Browser E2E tests and a
visual-audit capture run against a live hypervisor:

```bash
# with a hypervisor running on 127.0.0.1:7070
npm --prefix ff-ui run test:e2e   # assertions (or: gradle :ff-ui:e2eTest)
npm --prefix ff-ui run visual     # per-screen screenshots (or: gradle :ff-ui:visual)
```

The visual capture walks each screen/state at desktop (1440×950) and mobile
(390×844) viewports and writes full-page PNGs to
`ff-ui/build/test-artifacts/visual/<state>-<viewport>.png`. For a deterministic
*empty* landing shot, run the hypervisor with a fresh `--vm-dir`. `FF_UI_URL`
overrides the target.

Build-only CI can skip the KVM/caps/network tests:
`gradle :ff-test:test -PskipPrivilegedTests` (or `FF_SKIP_PRIVILEGED_TESTS=1`).

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
