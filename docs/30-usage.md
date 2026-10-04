# Using `flc`

Everything `flc` does is **stack-oriented**: a stack is a compose-style set of
services, stored as JSON on disk. The bundled web UI edits it as YAML (converted
to JSON client-side), and the same operations are available over the REST API.

## Build from source

Toolchain: **Java 25** (with GraalVM native-image) and **Gradle** on `PATH`.

```bash
gradle build                 # compile + tests
gradle :flc:distNativeTar    # build the release distribution
```

The distribution lands in `ff-app/build/distributions/frag-falcon-<version>.tar.gz`.

## CLI

```
--vm-dir=PATH        VM storage directory (required)
--oci-dir=PATH       OCI blob cache directory (required)
--api-host=HOST      API bind address (default 127.0.0.1)
--api-port=PORT      API port (default 7070)
--log-format=FORMAT  text|json (default text)
--log-level=LEVEL    error|warning|info|debug|trace (default info)
```

`--vm-dir` holds the working set (extracted rootfs, stack definitions, logs);
`--oci-dir` holds the downloaded layer blob cache. They can live on different
disks — the blob cache is read-mostly bulk storage and may sit on slower media.
The transient extraction temp lives under `<vm-dir>/oci-tmp`.

## REST API

```
GET    /api/v1/stack            list stacks with derived state
GET    /api/v1/stack/{id}       fetch a stack definition
POST   /api/v1/stack            create/update a stack
DELETE /api/v1/stack/{id}       stop and delete a stack
POST   /api/v1/stack/start      start a stack (topological order)
POST   /api/v1/stack/stop       stop a stack (reverse order)
POST   /api/v1/stack/logs       per-service log tails
GET    /api/v1/br               list Linux bridges
GET    /api/v1/host             hypervisor host name (browser tab title)
```

## Service semantics

- **Resources**: `resources: { vcpus, ramMib }` is a frag-falcon extension. When
  omitted, a service gets 1 vCPU / 512 MiB.
- **Volumes** are host-directory bind mounts — no disk images. The launcher
  bind-mounts each host dir into the rootfs dir at its guest path.
- **Environment** follows Docker semantics: image `ENV` is inherited and service
  entries override per key (service wins). A bare `KEY` is emitted as `KEY=`
  (empty value); it is **not** a host-environment passthrough.
- **entrypoint / command** follow Docker semantics: a service `entrypoint`
  replaces the image `Entrypoint`; a service `command` replaces the image `Cmd`
  while keeping the image `Entrypoint`.
- **restart**: `always | unless-stopped | on-failure | no` (default
  `unless-stopped`). `on-failure` is treated as `always` — the supervisor has no
  exit code, only process liveness.

`ports` and `networks` are ignored: each VM gets its own bridge IP, so it owns its
port space. See [CAVEATS §11](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md)
for the exact compose subset.

## VM lifecycle & restart survival

Each service runs as an independent launcher process (one process per VM). The
launcher is **daemonized** (double-forked, reparented to init), so VMs are **not**
killed when the hypervisor stops — a restarted `flc` re-adopts running VMs and
reports them as `running`.

> Development note: when the backend is launched via `gradle run`, Ctrl-C tears
> down the app JVM's process tree. Use the installed `flc` (or systemd) to
> exercise restart survival. See
> [CAVEATS §19](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md).

Next: [Networking & Logs](40-networking-and-logs.md).
