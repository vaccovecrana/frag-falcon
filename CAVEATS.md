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

**Patch.** `patches/libkrun-dhcp-retry.patch` makes the client retransmit
`DISCOVER` every `250 ms` for a total window of `8000 ms`, and makes the
`REQUEST`/`ACK` step ignore duplicate `OFFER`s (retransmits can leave several
queued in the socket buffer) and keep retrying until the same deadline.

**Apply / rebuild / re-vendor.**

```bash
# 1. Apply to the fetched libkrun source tree
cd ../libkrun-build/src/libkrun
patch -p1 < /path/to/frag-falcon-libkrun/patches/libkrun-dhcp-retry.patch

# 2. Rebuild (libkrun only; skip the huge kernel build) and re-vendor
cd ../../
./build-libkrun.sh --no-fetch --skip-fw --no-apt --no-verify

# 3. Copy the rebuilt libraries into this repo
cp out/lib64/libkrun_init.so.0.1.0 \
   /path/to/frag-falcon-libkrun/ff-jni/src/main/resources/io/vacco/ff/libkrun_init.so
cp out/lib64/libkrun.so.2.0.0 \
   /path/to/frag-falcon-libkrun/ff-jni/src/main/resources/io/vacco/ff/libkrun.so.2
```

A fresh `build-libkrun.sh` (without `--no-fetch`) re-downloads `main` and loses
the patch, so re-apply it.

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

---

## 4. File capabilities break `LD_LIBRARY_PATH` and `$ORIGIN`

A binary with file capabilities (e.g. `cap_net_admin+ep` on `fg_vmm`) runs in the
loader's **secure-execution mode**, where `LD_LIBRARY_PATH`/`LD_PRELOAD` are
ignored **and `$ORIGIN` in `RPATH`/`RUNPATH` is ignored too**. To make the
launcher work in both modes, it:

- links `libkrunfw.so.5` **directly** (a `DT_NEEDED` entry, via
  `-Wl,--no-as-needed`) so libkrun's `dlopen("libkrunfw.so.5")` finds the
  already-loaded soname, and
- builds with **both** an absolute `RUNPATH` to the vendored lib dir
  (`$(abspath $(LIBDIR))`, honoured in secure mode) **and** `$ORIGIN` (for the
  flat distribution run as root).

This split exists because there are two supported modes:

- **Production**: operators untar a flat distribution and run `flc` as root. No
  capabilities are involved, so `$ORIGIN` resolves the sibling libs.
- **Development/tests** (`gradle run`, `gradle :ff-test:test`, `npm run
  test:e2e`): the hypervisor runs as the developer's user and the launcher needs
  `cap_net_admin`. Secure-execution mode then makes `$ORIGIN` useless, which is
  why the launcher carries the absolute vendored-lib `RUNPATH`.

`FgProc` also honours `FF_VMM_BIN` / `FF_VMM_LIBDIR` so dev/tests run the
setcap'd built launcher instead of the temp extraction.

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

## 9. Zombie children must be reaped

The hypervisor spawns each VM launcher as a child of its JVM. When a launcher
exits, the JVM does not reap it automatically, so `/proc/<pid>` lingers as a
zombie and naive pid discovery would keep reporting the VM as "running".
`FgProc.pidOf` therefore ignores zombie processes (reads `/proc/<pid>/stat`),
and `FgProc.reap()` (native `waitpid(-1, WNOHANG)` loop) is available for the
supervisor to drain exited children. libkrun `_exit()`s the launcher on guest
shutdown, so this matters for every VM exit.

VM discovery uses **only** the process name: the launcher sets
`/proc/<pid>/comm` to exactly the VM id (passed as `--vm-id`); there is no
environment-based fallback.

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

- the `fg_vmm` launcher carries `cap_net_admin` — via `setcap` or systemd
  `AmbientCapabilities=CAP_NET_ADMIN` — for TAP create/attach/open;
- the service user is in the **`kvm`** group (`/dev/kvm`);
- the VM storage dir is mounted `nosuid,nodev,noexec` host-wide (fstab/.mount).

`cap_net_admin` is a secure-execution context, so the launcher links an absolute
vendored-lib `RUNPATH` (a cap'd binary ignores `$ORIGIN`/`LD_LIBRARY_PATH`; see
§4). File capabilities are silently ignored on `nosuid` filesystems such as
`/tmp`.

**The hypervisor refuses to start without it.** `FgContext.init()` checks
`CAP_NET_ADMIN` up front (via `FgNetCap`: the process effective set, or the
`cap_net_admin` file capability on `fg_vmm`) and aborts with an actionable error
if absent — a launcher that cannot create TAPs cannot run any stack. This
replaces the previous behavior where a missing capability caused the supervisor
to retry a doomed start in a loop. A failed start now also clears the service's
monitor, so `restart:` governs post-success exits only.

**Volumes + namespaces + KVM.** The launcher isolates per-VM volume bind mounts
by entering a user+mount namespace (`unshare(CLONE_NEWUSER|CLONE_NEWNS)`) when
unprivileged. This works with KVM and with pre-created TAP devices (verified),
provided the namespace is created **before any threads** — the log-ring thread
starts after `setup_namespaces()` for exactly this reason (previously
`--volume` together with `--log-file` failed with `EPERM`).

**Development** uses the same unprivileged model: run
`sudo bash ff-jni/setup-caps.sh` after each launcher rebuild to re-apply
`cap_net_admin` (Gradle copies strip file capabilities).

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

`scripts/e2e.sh` automates the browser suite: it builds the bundle + app,
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

`FgProc.spawn` → `spawn_process` **double-forks** the launcher: an intermediate
child `setsid()`s, forks the real launcher, reports its pid back over a pipe,
and exits at once. The launcher is therefore reparented to init/subreaper and is
**not** a descendant of the hypervisor process. This is what lets VMs survive the
hypervisor going away: a restarted `flc` re-adopts running VMs from `/proc` by
process name (`/proc/<pid>/comm` == the VM id; see `FgProc.pidOf`) in
`FgStackSvc.reconcile()`, and reports them `running`.

Because the launcher is reparented, the host **cannot `waitpid()` on it** — guest
exit is observed only through `comm` discovery. There is no `waitProcess`; a
supervisor tick sees the process gone and restarts per `restart:` policy.

**Development caveat — `gradle run`.** When the backend is launched via
`gradle run` and stopped with Ctrl-C, the **Gradle daemon tears down the app
JVM's process tree** (`ProcessHandle.descendants()`-style reaping on
cancellation) rather than just the JVM. The double fork reparents the launcher
out of that tree, so it **should** survive — but a build tool is not a VM
supervisor and its teardown semantics are not a supported VM lifecycle. **Use
`flc` (the installed launcher) or systemd to verify restart survival**, not
`gradle run`. Treat child VMs under `gradle run` as tied to the build session.

The Java boot tests (`FgTest.runVm`, `FgVmBootTest`) deliberately **do not** use
the daemonizing `spawn`: they launch `fg_vmm` directly via `ProcessBuilder` so
the test process stays the parent and can `waitFor()` the guest exit code.

