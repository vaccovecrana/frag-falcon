# CAVEATS.md — libkrun integration caveats & local patches

Non-obvious behaviours we had to work around while wiring frag-falcon to
libkrun 2.0. Each entry says what the constraint is, why it exists, and what we
do about it.

> The **user-facing guide** lives in `docs/` (published to GitHub Pages). It
> summarizes and links here for depth — keep it in sync when this file or
> `README.md` changes.

---

## 1. libkrun's guest DHCP was single-shot (100 ms) — **locally patched**

**Problem.** The in-guest init (`init/init-binary/src/dhcp.rs` in libkrun) sent
**one** `DHCPDISCOVER`, set `SO_RCVTIMEO = 100 ms`, and on timeout returned
success without an address. With a raw TAP on a bridge, the tap only gains
carrier when libkrun opens it at VM start, and the bridge needs a moment
(~1 s for a plain bridge, up to ~4 s with STP) before it forwards — so that one
packet is reliably lost. Result: no DHCP lease, and any workload that needs the
network fails. (This client is designed for passt/gvproxy, which answer
instantly.)

**Patch.** The retry is carried by
[`libkrun-build`](https://github.com/vaccovecrana/libkrun-build) (patch
`patches/libkrun-dhcp-retry.patch`), applied automatically during that repo's
build. It makes the client retransmit `DISCOVER` every `250 ms` for a total
window of `8000 ms`, and makes the `REQUEST`/`ACK` step ignore duplicate
`OFFER`s (retransmits can leave several queued in the socket buffer) and keep
retrying until the same deadline.

**Apply / rebuild / re-vendor.**

```bash
# 1. Build patched libkrun (the build script fetches upstream and applies the patch)
cd ../libkrun-build
./build-libkrun.sh --skip-fw --no-apt --no-verify

# 2. Copy the rebuilt libraries into this repo
cp out/lib64/libkrun_init.so.0.1.0 \
   /path/to/frag-falcon/ff-jni/src/main/resources/io/vacco/ff/libkrun_init.so
cp out/lib64/libkrun.so.2.0.0 \
   /path/to/frag-falcon/ff-jni/src/main/resources/io/vacco/ff/libkrun.so.2
```

The patch is applied by `build-libkrun.sh` after every fetch, so no manual
`patch` step is needed; a rejected hunk aborts the build.

**Consequence.** If no DHCP server answers, the guest now waits up to 8 s before
booting the workload. Acceptable for interactive/bridged VMs; revisit if boot
latency matters.

---

## 2. `krun_vmm_run` never returns — one process per VM

libkrun calls `libc::_exit(exit_code)` when the guest shuts down
(`src/libkrun/src/vmm/mod.rs`), so the launcher process terminates on guest exit.
A VM must never run in the hypervisor's JVM. `ff-jni`'s native `fg_vmm` hosts
exactly one VM per process; the hypervisor spawns/supervises it.

---

## 3. A TAP can only be attached by one process (`EBUSY`)

libkrun opens the TAP by name with `TUNSETIFF`. If the launcher also holds the
tap open (e.g. to keep carrier up), libkrun gets `EBUSY`. The tap must be
created **persistent** by a separate privileged step, with its fd closed, before
the VM starts. That's why the launcher has `--tap-up`/`--tap-down` utility modes
and production lets the (root) hypervisor own the tap lifecycle.

The launcher also cleans up its own TAP on exit, because it cannot otherwise:
libkrun `_exit()`s the process on guest exit (§2), so no `atexit` handler runs.
Before `setup_namespaces()` (while it still holds host-netns `CAP_NET_ADMIN`)
`fg_vmm` forks a **TAP watcher** that holds a pipe whose write end stays open in
the launcher; when the launcher dies for any reason — guest exit, SIGTERM,
crash — the pipe hits EOF and the watcher deletes **exactly that TAP by ifindex**.
Scoping by ifindex means a TAP recreated for a restarted VM (new ifindex) is
never deleted by a stale watcher. `--tap-up` is idempotent (it clears any stale
device before creating), so a restart never trips over a lingering TAP.

---

## 4. File capabilities break `LD_LIBRARY_PATH` and `$ORIGIN`

A binary with file capabilities (e.g. `cap_net_admin+ep` on `fg_vmm`) runs in the
loader's **secure-execution mode**, where `LD_LIBRARY_PATH`/`LD_PRELOAD` are
ignored **and `$ORIGIN` in `RPATH`/`RUNPATH` is ignored too**. The launcher:

- links `libkrunfw.so.5` **directly** (a `DT_NEEDED` entry, via
  `-Wl,--no-as-needed`) so libkrun's `dlopen("libkrunfw.so.5")` finds the
  already-loaded soname, and
- builds with **both** an absolute `RUNPATH` to the vendored lib dir
  (`$(abspath $(LIBDIR))`) **and** `$ORIGIN`.

The absolute entry only helps where that path exists — i.e. the build machine, for
the dev/test flow. It does **not** make a file-capped binary relocatable:

- **Production** (systemd, rootless): the unit grants the capability via
  `AmbientCapabilities=CAP_NET_ADMIN`, which does **not** trigger secure-execution
  mode. `$ORIGIN` then resolves the sibling `libkrun*.so` files, so the flat
  distribution works from any directory. **Never `setcap` the deployed `fg_vmm`**:
  the file cap forces secure mode and the launcher fails to load `libkrun.so.2`.
  (Running as root also works: no capabilities are involved, so `$ORIGIN` is used.)
- **Development/tests** (`gradle run`, `gradle :ff-test:test`, `npm run
  test:e2e`): the launcher is `setcap`'d and run from the build tree, so secure
  mode ignores `$ORIGIN` and the absolute vendored-lib `RUNPATH` is what resolves
  the libs.

**Gradle copies silently drop the capability.** Any `Sync`/`copy` (e.g.
`installNative`, `processResources`) overwrites `fg_vmm` and strips its caps, so
unprivileged runs then fail with `Operation not permitted` on tap creation.
Re-apply with `sudo bash ff-jni/setup-caps.sh` after every launcher rebuild (it
caps every known copy); `gradle :ff-jni:setupCaps` does the same when the
invoking shell already has root/passwordless sudo.


---

## 5. File capabilities make the launcher non-dumpable (`/proc/environ`)

A process that gains file capabilities has `dumpable=0`, so
`/proc/<pid>/environ` is root-only. Our `/proc` VM re-discovery can't read it as
a normal user. The launcher sets its process name to the VM id
(`prctl(PR_SET_NAME)`), and `FgProc.pidOf` matches on `/proc/<pid>/comm`
(no environment fallback).
(falling back to `environ`, which works for the root hypervisor).

---

## 6. Volume mounts require a mount namespace

Host-directory volumes are bind-mounted into the rootfs dir inside a private
mount namespace (`CLONE_NEWNS` as root, `CLONE_NEWUSER|CLONE_NEWNS`
unprivileged). libkrun's virtiofs passthrough follows host submounts, so the
guest sees them with no guest-side mount. Notes:

- `unshare(CLONE_NEWUSER)` discards host-namespace capabilities, so if a VM
  needs **both** a managed TAP and volumes while unprivileged, the tap must be
  set up before the user namespace is created (not yet exercised).
- Read-only volumes are remounted RO at the top level; nested submounts inside a
  volume stay writable (would need `mount_setattr(2)`/`AT_RECURSIVE`).
- libkrun's virtiofs switches its effective uid/gid to the guest's per request,
  so an image that `chown`s to a non-root uid (most Docker images) needs a
  **subuid/subgid range** mapped into the launcher's user namespace. The launcher
  does this with `fg_usermap` — a small helper carrying `cap_setuid,cap_setgid`
  (installed `root:<service-user>`, mode `0750`) that reads the user's range from
  `/etc/subuid`/`/etc/subgid` and writes the launcher's `uid_map`/`gid_map`. An
  unprivileged process cannot write a *range* itself (the kernel requires
  `CAP_SETUID` in the parent user namespace), which is why the helper exists.
  Without a configured range the launcher falls back to a **single-uid map**
  (guest uid 0 only) and logs a warning; images running as non-root uids then
  fail with `chown: Invalid argument`. See `deploy/setup.sh`.

---

## 7. `ETXTBSY` when spawning the extracted launcher

Writing the launcher resource to disk with an unclosed `OutputStream` leaves the
file open for writing, so `execve` fails with `Text file busy`. Always close the
output stream before spawning (`FgProc`/`FgJni` do).

---

## 8. Test-bridge specifics (`virbr0`)

`virbr0` (libvirt's default network) has **STP enabled**, adding ~4 s before a
newly attached port forwards — covered by the 8 s DHCP retry window above. It
runs `dnsmasq` on `:67`, so no extra DHCP server is needed. `docker0` has **no**
DHCP server and is unsuitable. Tests require `cap_net_admin` on the launcher
(`ff-jni/setup-caps.sh`); otherwise the network test no-ops.

---

## 9. VM discovery is by process name; the spawn intermediate is reaped

The hypervisor spawns each VM launcher via `posix_spawn`; the launcher then
**detaches itself** (see §19), so it is reparented to init/subreaper and is
**never** a child of the JVM. Because it is reparented, the host cannot
`waitpid()` on the launcher and needs no reaping loop for it.

There is, however, a short-lived **posix_spawn intermediate** that *is* a child
of the JVM: the process that execs `fg_vmm`, then double-forks and `_exit()`s.
The JDK sets `SIGCHLD` to `SIG_DFL` and only `waitpid()`s the pids it tracks via
`ProcessHandleImpl`, so this intermediate would otherwise linger as a zombie for
the JVM's lifetime. `spawn_process` reaps it on a **detached native thread**
(`waitpid(pid)`), leaving the JDK's own children untouched. (The old
`waitpid(-1)`-based `FgProc.reap()`/`reapChildren()` was removed because it could
steal the JDK's children.)

The child also closes every inherited fd ≥ 3
(`posix_spawn_file_actions_addclosefrom_np`), so a detached launcher never holds
host sockets — notably the API listening port — after the JVM exits.

VM discovery uses **only** the process name: the launcher sets
`/proc/<pid>/comm` to exactly the VM id (passed as `--vm-id`); there is no
environment-based fallback. `FgProc.pidOf` still filters zombie entries
defensively (reads `/proc/<pid>/stat`), so a transient zombie of *any* origin
is not mistaken for a running VM.

---

## 10. Not intended to be a public service

frag-falcon has **no authentication** and manages privileged VM networking. It
must never be exposed publicly; run it behind a private LAN segment or a VPN.

---

## 11. Stack model is a compose subset (with extensions)

- `resources: { vcpus, ramMib }` is a **frag-falcon extension**, not standard
  docker compose. When omitted, a service gets **1 vCPU / 512 MiB**.
- `ports` and `networks` are **ignored**. Each VM gets its own bridge IP, so it
  owns its port space; the UI renders the ports the OCI image declares
  (`ExposedPorts`). Publishing/NAT is the operator's concern.
- `restart: on-failure` is treated as `always`: the supervisor polls `/proc`
  (single mechanism for fresh and re-adopted VMs) and has no exit code.
- A VM's id is `toHex((stackId + serviceId).hashCode())` (no bookkeeping);
  unlike a random id, a cross-service collision is theoretically possible.
- **`entrypoint`/`command` follow Docker semantics**, resolved in one place
  (`FgVmLaunch.command`): a service `entrypoint` replaces the image
  `Entrypoint`; a service `command` replaces the image `Cmd` **while keeping the
  image `Entrypoint`**. Both unset → image `Entrypoint` + image `Cmd`; image
  `Cmd` only (no entrypoint) → the command is `argv[0]`. The stack service
  overrides are carried through `FgVm` unmerged so the resolver has both the
  image metadata (`image.json`)   and the overrides. Without this, a compose file
  that sets only `command` (as dockge exports) would drop the image entrypoint
  and fail to run the workload.
- **`environment` also follows Docker semantics**, resolved in `FgVmLaunch.env`:
  image `ENV` is inherited and service `environment:` entries override per key.
  The merged list is **de-duplicated** (service wins) because the guest init
  keeps the *first* occurrence of a key — without dedupe a pre-baked `PATH`/
  `LD_LIBRARY_PATH` would silently ignore the user's override. A bare key
  (no `=`, e.g. `- FOO`) is emitted as `FOO=` (empty value, present) with a
  warning; it is **not** a host-environment passthrough.
- **Stack definitions are validated before they are saved or started**
  (`FgValid`, yavi-based rules). The rules enforce: a valid stack id; a non-empty
  service map; per service a non-blank syntactically-valid image reference, a
  known `restart` policy, well-formed volumes (`HOST:GUEST[:ro]`, absolute guest
  path, existing host dir, unique guest paths), well-formed environment entries
  (unique keys), non-blank `entrypoint`/`command`/`depends_on`, and sane
  `resources` (vCPUs ≥ 1, RAM ≥ 128 MiB); plus cross-service rules (every
  `depends_on` target exists; no dependency cycles). Failures are bridged to
  `RvValidation`s (our `ff.stack.*` keys, positional `params`) and returned as a
  **400** with the `RvResult` envelope, so the UI renders them via its i18n
  templates. This catches malformed definitions at the edge, before any
  provisioning/boot work.

---

## 13. Image extraction is kernel-confined (host-escape defense)

OCI layers arrive as tar archives from untrusted registries. The old cpio
model did **not** make extraction safer — the risk is host-side extraction, not
the image format. libkrun's virtiofs is already symlink-safe at runtime
(`readlinkat` returns the target string; `lookup` is `O_NOFOLLOW`), so the
boundary is **extraction**.

`ff-jni`'s `fg_extract.c` extracts each layer with **`openat2(RESOLVE_IN_ROOT |
RESOLVE_NO_MAGICLINKS)`**: every path is resolved as if chrooted into the rootfs,
so `..` and absolute targets can never leave it. Rules:

- Entry paths: relative, no `..`, no leading `/` — otherwise provisioning fails.
- Symlink **targets** are stored verbatim (e.g. Alpine's `etc/mtab -> ../proc/mounts`
  and `/sbin/blkid -> /bin/busybox`), because a target is just data the guest
  resolves inside its own root.
- Hardlink sources must resolve inside the root.
- Device/FIFO entries are skipped (no `mknod`).
- Any violation fails provisioning (no silent skip).
- Volume mount points created by the launcher use the same confined `mkdir`
  (no traversal through a host symlinked ancestor).

---

## 12. Bounded log ring (launcher-owned)

The launcher redirects the guest console to a pipe; a reader thread keeps the
last `--log-lines` (default 4096) lines in memory and rewrites `vm.log`
**atomically** (temp + rename) every ~500 ms and every 64 new lines. The file is
therefore bounded, and it survives hypervisor restarts (the launcher owns it).
`FgVmLaunch` passes `--log-file <service>/vm.log --log-lines 4096`.

**Flush on exit.** libkrun ends the launcher with `libc::_exit()` (`vmm/mod.rs`),
which runs no atexit handlers/destructors, so the ring used to miss the final
lines — a fast-exiting workload (e.g. `cowsay` printing and quitting) could log
nothing at all. The launcher now **interposes `_exit`** (its own definition,
linked with `-rdynamic` so it wins over libkrun's `_exit@GLIBC_2.2.5`
reference): the hook drains whatever is still buffered in the console pipe,
folds the last partial line into the ring, flushes it, then calls
`exit_group(2)`. Only messages still inside libkrun's virtqueue at shutdown can
be lost.

Trade-off: messages beyond the last N are dropped (acceptable given the intent —
containers ship their own telemetry).



---

## 14. Packaging: flat layout, rootless by design

The release is a flat `tar.gz`: the GraalVM native `ff-app` plus `fg_vmm` and the
libkrun shared objects in the same directory. At runtime the executable resolves
that directory via `/proc/self/exe` (override with `FF_NATIVE_DIR`); there is no
resource extraction.

The hypervisor runs **unprivileged**. Privilege is delegated to the OS once
(see `deploy/setup.sh` and `deploy/flc.service`):

- the `fg_vmm` launcher receives `cap_net_admin` for TAP create/attach/open from
  the systemd unit's `AmbientCapabilities=CAP_NET_ADMIN` — never `setcap`, which
  forces the loader's secure-execution mode and breaks `$ORIGIN` (see §4);
- the `fg_usermap` helper carries `cap_setuid,cap_setgid` (root-owned, mode
  `0750`) and maps the service user's `/etc/subuid`/`/etc/subgid` range into each
  launcher's user namespace so guest images can run as arbitrary uids (§6);
- the service user is in the **`kvm`** group (`/dev/kvm`);
- the VM storage dir is mounted `nosuid,nodev,noexec` host-wide (fstab/.mount).

**The hypervisor refuses to start without it.** `FgContext.init()` checks
`CAP_NET_ADMIN` up front (via `FgNetCap`: the process effective set, or the
`cap_net_admin` file capability on `fg_vmm`) and aborts with an actionable error
if absent — a launcher that cannot create TAPs cannot run any stack. This
replaces the previous behavior where a missing capability caused the supervisor
to retry a doomed start in a loop. A failed start now also clears the service's
monitor, so `restart:` governs post-success exits only.

**Volumes + namespaces + KVM.** The launcher isolates per-VM volume bind mounts
by entering a user+mount namespace (`unshare(CLONE_NEWUSER|CLONE_NEWNS)`) when
unprivileged, and immediately maps the service user's subuid/subgid range via
`fg_usermap` so the guest can run as arbitrary uids (see §6). This works with KVM
and with pre-created TAP devices (verified), provided the namespace is created
**before any threads** — the log-ring thread starts after `setup_namespaces()`
for exactly this reason (previously `--volume` together with `--log-file` failed
with `EPERM`).

**Development** uses the same unprivileged model: run
`sudo bash ff-jni/setup-caps.sh` after each launcher rebuild to re-apply
`cap_net_admin` to `fg_vmm` and `cap_setuid,cap_setgid` to `fg_usermap` (Gradle
copies strip file capabilities).

## 15. Container rootfs is writable but "eventually ephemeral"

The guest's rootfs is a host directory shared over virtiofs. Guests can write it
(creation, overwrite, system paths) — but writes land in the shared directory and
**persist across restarts**; they are discarded only when the image reference is
upgraded (`update` re-extracts). Only explicitly mounted volumes are durable.
Treat image-relative state as "eventually ephemeral".

Two consequences drove the model:

- **Read-only files.** The launcher is unprivileged, so guest-root writes are
  checked against the service user's host DAC; files an image ships `0444`
  (e.g. `/etc/resolv.conf`) could not be overwritten. The extractor now adds
  owner bits (`S_IWUSR` on files, `S_IRWXU` on dirs) so containers can rewrite
  read-only files and initialize system paths at boot. Ownership and group/other
  bits are unchanged.
- **Persistence threat model.** A guest can plant setuid binaries, device nodes
  (via `mknod`) and executables in the shared dir; because the virtiofs
  passthrough runs as the service user, host-side processes touching that dir
  could weaponize them. Mounting the vm dir `nosuid,nodev,noexec` (host-wide)
  neutralizes that. The hypervisor only *audits* the flags at startup and warns;
  it never mounts. Planted **symlinks** are not affected by mount flags —
  treat the vm dir as hostile (don't run symlink-following tools over it).

## 16. API error contract: `RvResult` envelopes

Every REST endpoint returns a **JSON body on both success and failure** — a
`RvResult` subclass defined under `ff-api/.../api/result/` (e.g.
`FgStackListResult { List<FgStackStatus> stacks }`). On failure the same DTO is
returned with `error` set and, for validation failures, `validations` populated
(`RvValidation`: `key` + `params` + optional `name`). This is why there is no
`fail(status, e)` helper that returns an empty body.

Services signal validation failures with `FgValidationException` (carrying
`RvValidation`s); the API layer maps them onto the result DTO. The frontend
bundle carries sentence templates per `key` (`ff-ui/src/i18n.ts`), so the
backend stays locale-agnostic. Note: the generated TypeScript types `params` as
a `Map`, but the wire format is a plain JSON object — read it defensively
(`ff-ui/src/i18n.ts`).

The frontend unwraps envelopes in `ff-ui/src/api.ts` and raises any `error`/
`validations` as an error **toast** (background polls stay silent).

## 17. Test platform requirements (reproducing the suite)

The E2E (`ff-ui/test/*.test.mjs`) and integration (`ff-test`) suites now
**hard-require** the full platform — they fail rather than skip when it is
missing:

- a Linux bridge, default **`virbr0`** (libvirt's default network; `docker0` has
  no DHCP server). Override with `FF_E2E_BRIDGE`;
- **`/dev/kvm`** access (be in the `kvm` group);
- **`cap_net_admin`** on `fg_vmm` (`sudo bash ff-jni/setup-caps.sh`, re-run after
  any launcher rebuild);
- network access (image pulls).

Build-only CI (no KVM/caps/bridge) can exclude the privileged tests with
`gradle :ff-test:test -PskipPrivilegedTests` (or `FF_SKIP_PRIVILEGED_TESTS=1`).

`ff-test/e2e.sh` automates the browser suite: it builds the bundle + app,
applies the launcher capability, starts a backend on a throwaway `--vm-dir`,
runs `npm run test:e2e`, and tears the backend down on exit. Override the port,
vm-dir or bridge via `FF_E2E_PORT`, `FF_E2E_VM_DIR`, `FF_E2E_BRIDGE`; set
`SUDOPW` to auto-apply caps, `SHOW_LOG=1` to follow the backend log, and
`KEEP_VM_DIR=1` to inspect the run's storage.

## 18. Operation locking and concurrent provisioning

Mutating a stack service is serialized by a **per stack-service lock**
(`FgStackSvc.active`, a `ConcurrentHashMap.newKeySet()`): `start`, `stop`,
`update`, `delete`, and the supervisor's restart all claim the key
`stackId/service` before doing work. A second concurrent request is rejected
with **HTTP 409** (`FgStackSvc.FgBusyException` → `RvResult` body), so UI
double-clicks and rapid API calls can't race. `status()` reflects an in-flight
operation as `provisioning` so the UI can disable its actions.

Provisioning is additionally serialized **per image reference**
(`FgVmSvc.provisioningLocks`): two services pulling the same image share the
blob store, so their extractions must not overlap. Each extraction also uses a
**unique** temp dir under the shared `oci/tmp` (`FgOciStore.newTmpDir`), removed
in a `finally`; `FgIo.delete` is race-tolerant (`deleteIfExists`), so a
concurrent delete never logs a stack trace.

Before this, `start()` (stack-op worker) and the supervisor's `tick()` could
both launch the same service, and the two extractions clobbered the shared
`tmp/unzipped` dir (`NoSuchFileException`). See
`FgOciConcurrencyTest`, `FgImageExpandTest`.

## 19. VM launchers are daemonized (survive a hypervisor restart)

The launcher **daemonizes itself inside `fg_vmm`'s `main()`**: it `setsid()`s and
**double-forks** there, while the process is still **single-threaded**. The
intermediate and original processes `_exit(0)`, so the real launcher is
reparented to init/subreaper and is **not** a descendant of the hypervisor. This
is what lets VMs survive the hypervisor going away: a restarted `flc` re-adopts
running VMs from `/proc` by process name (`/proc/<pid>/comm` == the VM id; see
`FgProc.pidOf`) in `FgStackSvc.reconcile()`, and reports them `running`.

The host JVM only **spawns** the launcher (`posix_spawn` via
`FgProc.spawn`/`FgProcess.spawnProcess`); it never forks and never daemonizes.
It does not learn the launcher pid from the spawn call (there is no pid
handshake) — discovery is always `/proc` by name. `FgVmSvc.start` waits (bounded
poll) for `FgProc.pidOf(vmid)` to appear, since `/proc` is not populated until
the launcher has entered its namespaces and set its `comm`.

Because the launcher is reparented, the host **cannot `waitpid()` on it** — guest
exit is observed only through `comm` discovery. There is no `waitProcess` for the
launcher; a supervisor tick sees the process gone and restarts per `restart:`
policy. The posix_spawn *intermediate* is a JVM child and is reaped on a detached
native thread — see §9.

**Never `fork()` the JVM.** A raw `fork()` in a multithreaded process may only be
followed by async-signal-safe calls before `exec()`, and forking a running JVM
(JIT/GC/JVMCI) can corrupt JVM-internal state and crash **unrelated** threads.
This was observed for real: a former `spawn_process` double-forked from the JVM;
a concurrent launcher spawn during OCI layer expansion produced a `SIGSEGV` in
`FgJni.reapChildren` on the `ff-supervisor` thread (`RIP` in low memory, no
native frame — JVM native-entry corruption). The fix is architectural: the JVM
uses `posix_spawn` (`vfork`/`clone`-based, safe) and the daemonizing fork lives
in single-threaded `fg_vmm`.

**Development caveat — `gradle run`.** When the backend is launched via
`gradle run` and stopped with Ctrl-C, the **Gradle daemon tears down the app
JVM's process tree** (`ProcessHandle.descendants()`-style reaping on
cancellation) rather than just the JVM. The launcher's self-daemonization
reparents it out of that tree, so it **should** survive — but a build tool is not
a VM supervisor and its teardown semantics are not a supported VM lifecycle.
**Use `flc` (the installed launcher) or systemd to verify restart survival**, not
`gradle run`. Treat child VMs under `gradle run` as tied to the build session.

The Java boot tests (`FgTest.runVm`, `FgVmBootTest`) launch `fg_vmm` directly via
`ProcessBuilder` with `--foreground`, so it does **not** daemonize: the test
process stays the parent and can `waitFor()` the guest exit code.

## 20. OCI blob cache is split from the VM storage dir

The OCI layer blob cache (`blobs/`, keyed by registry digest, reused across VM
builds) is a **read-mostly bulk store** and can sit on slower media, while the
working set — the extracted rootfs, stack definitions, logs, and the transient
extraction temp — belongs on fast storage. `flc` therefore takes two required
paths: `--oci-dir` (the `FgOciStore` cache root, holding `blobs/`) and `--vm-dir`
(the working set, holding `<vm-dir>/oci-tmp/` for transient expanded layers).

Both are required and validated at startup. Only `--vm-dir` needs the
`nosuid,nodev,noexec` hardening (§14); the blob cache holds nothing executable and
is never exposed to guests. Extraction temp dirs are removed in a `finally`, and
`FgOciStore.sweepTmp()` clears any orphaned ones at startup.


## 20. OCI blob cache is split from the VM storage dir

The OCI layer blob cache (`blobs/`, keyed by registry digest, reused across VM
builds) is a **read-mostly bulk store** and can sit on slower media, while the
working set — the extracted rootfs, stack definitions, logs, and the transient
extraction temp — belongs on fast storage. `flc` therefore takes two required
paths: `--oci-dir` (the `FgOciStore` cache root, holding `blobs/`) and `--vm-dir`
(the working set, holding `<vm-dir>/oci-tmp/` for transient expanded layers).

Both are required and validated at startup. Only `--vm-dir` needs the
`nosuid,nodev,noexec` hardening (§14); the blob cache holds nothing executable and
is never exposed to guests. Extraction temp dirs are removed in a `finally`, and
`FgOciStore.sweepTmp()` clears any orphaned ones at startup.


