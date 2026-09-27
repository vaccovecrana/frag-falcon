# PLAN.md — frag-falcon → libkrun 2.0 migration

Migrate the Firecracker-based micro-VM management framework (see `frag-falcon-main`)
to use **libkrun 2.0** as the VM backplane, so that OCI/Docker container images run
as microVMs with **host directories shared as virtiofs volumes** instead of raw disk
images. Then restructure the UI to follow the UX/element layout of dockge while
keeping our own Java (murmux/ronove/shax) backend.

Incremental migration: keep what is still useful, leave behind what libkrun now
provides, and track progress with the milestones below.

---

## 0. Progress

| Milestone | Status | Notes |
|-----------|--------|-------|
| M0 — Branch & skeleton | ✅ Done | Branch emptied; Gradle 9.2.1 / Java 25 toolchain; smoke test green. |
| M1 — "Hello VM" | ✅ Done | First via Java/FFM; later superseded by the native C launcher (see M3). |
| M2 — Container as microVM | ✅ Done | `ff-oci` (persistent cache) + child-process boot; Alpine prints to console. |
| M3 — Host volumes | ✅ Done | Native C launcher in `ff-jni`; host-dir volumes via bind mounts; `ff-krun`/`ff-vmm` retired. |
| M4 — Networking (TAP + bridge) | ✅ Done | Launcher `--tap`/`--mac` + DHCP; host tap lifecycle via `--tap-up`/`--tap-down`; libkrun DHCP patched to retry (see `CAVEATS.md`). |
| M5 — Supervisor + REST API | 🔜 Next | process-per-VM + `/proc` re-discovery. |
| M6 — dockge-structured UI | ⬜ | |
| M7 — Packaging, tests, docs | ⬜ | |

### Progress log

- **M0** (`f3dd0d0`): repo skeleton, vendored `libkrun.so.2` / `libkrun_init.so` /
  `libkrunfw.so.5`, FFM smoke test.
- **Naming convention** (`6584993`): all Java classes use the `Fg` prefix.
- **M1/M2** (`8ce215c`): `ff-oci`, Java/FFM builder bindings, `ff-vmm`, centralized
  `ff-test`, Alpine boot integration test.
- **M3** (uncommitted): retired `ff-krun`/`ff-vmm`; reinstated the native `ff-jni`
  module; rewrote the launcher in C (`ff-jni/src/vmm/fg_vmm.c`); host-directory
  volumes via bind mounts; `FgProc` spawns/tags/re-discovers VMs.
- **M4**: bridged TAP + in-guest DHCP. Patched libkrun's DHCP client to retry
  (single-shot 100 ms was too aggressive for a TAP/bridge); tab/VM re-discovery
  now matches `/proc/<pid>/comm` because cap'd launchers are non-dumpable. See
  `CAVEATS.md` and `patches/libkrun-dhcp-retry.patch`.

### Key discoveries (folded into implementation)

- **`krun_vmm_run` never returns.** libkrun calls `libc::_exit(exit_code)` when the
  guest shuts down (`src/libkrun/src/vmm/mod.rs:409`, `api/vmm_builder.rs:282`).
  This makes process-per-VM (D1) **mandatory**; a VM must never share the
  hypervisor's (or a test runner's) JVM.
- **libkrun's built-in init only applies `tmpfs` mounts.** It does not mount extra
  virtiofs devices (`init/init-binary/src/config.rs`), so volumes are handled
  host-side (see §5).
- **virtiofs follows host submounts.** libkrun's passthrough tracks `mnt_id`/`dev`
  and emits `ATTR_SUBMOUNT` (`devices/src/virtio/fs/linux/passthrough.rs`), so
  bind-mounting a host dir into the shared rootfs dir makes it visible in the guest.
- **Rootless volumes work via user namespaces.** The launcher uses
  `unshare(CLONE_NEWUSER|CLONE_NEWNS)` when unprivileged, `CLONE_NEWNS` when root.
- **Console wiring.** Guest workload stdio only reaches the host through ports named
  exactly `krun-stdin` / `krun-stdout` / `krun-stderr`, created by
  `krun_console_builder_add_default_console`.
- **Python `fork`/`exec` gotcha (C side):** the `OutputStream` used to extract the
  launcher must be closed before `execve`, or it fails with `ETXTBSY`.

---

## 1. Architecture shift

The old framework treated Firecracker as an external daemon: `fork` a binary, talk
its REST API over a UNIX socket, build a cpio initramfs, boot a host kernel, attach
TAP/block devices. libkrun 2.0 is an **in-process C library with a builder API**.

Consequences:

- HTTP-over-UNIX transport, `fc.sock`, `FgFirecracker`, and the entire
  `io.vacco.ff.firecracker.*` schema tree are deleted; a **native C launcher**
  (`ff-jni`) drives the libkrun builder API instead.
- The guest kernel is embedded in `libkrunfw.so` (`krun_payload_load_krunfw`) —
  no `--krn-dir`, no vmlinux.
- Storage flips **block-drive → virtiofs**: the rootfs is a host directory shared
  via a device tagged `/dev/root`; extra host dirs are additional virtiofs mounts.
  This is the whole point of the migration.
- libkrun ships its **own guest init** (`libkrun_init.so`): hand it an OCI
  `config.json`, and it injects `/init.krun` + `/.krun_config.json` as an in-memory
  overlay and appends `init=/init.krun` to the kernel cmdline. The Go `ffrt` init
  and the cpio pipeline go away.

---

## 2. Locked decisions

| # | Decision |
|---|----------|
| D1 | **Process-per-VM with `/proc` re-discovery, no IPC.** The hypervisor forks a child process per VM tagged with metadata in `/proc`; on restart it re-adopts running VMs by scanning `/proc`. Root privileges are guaranteed on the bare-metal host. |
| D2 | **virtio-net + TAP + bridge** networking. Keep the existing native TAP code and DHCP stack. |
| D3 | **Use libkrun's built-in init; drop the Go `ffrt` runtime and `FgCpio`.** |
| D4 | **Keep the Preact UI**; restructure it to dockge's element layout; adopt the `vf-*` design system from `vgmusic-restoration/vgr-funding/vf-ui/res/main.scss` (+ `fonts.scss`). |
| D5 | **Empty the feature branch** (commit the current deletions), then populate; **vendor the native `.so` files into the repo**. |

---

## 3. Keep / drop / rewrite

**Keep (retarget)**
- `FgDockerIo`, `FgTarIo`, `FgTarEntry` — OCI pull/extract, now into a persistent
  **rootfs dir** instead of a cpio.
- `ff-jni` native host primitives that still apply: `fg_tap.c` (TAP
  create/attach/delete/MAC), `fg_proc.c` (detached fork + env tagging), `fg_raw.c`
  plus the Java DHCP classes (`FgDhcp*`, `FgEthFrame`) for the bridge and lease
  acquisition.
- `FgIo` (`toJson`, `pidOf` — the core of re-discovery), the `FgOptions`/`FgContext`
  shape, `FgValid` (yavi), murmux/ronove/shax, and the Preact app shell.

**Drop**
- `io.vacco.ff.firecracker.**` (~40 classes), `FgFirecracker`, `FcApiResponse`,
  `FgNetIo`, `FgJni.httpRequest`, `FgVmFiles.fc.sock`.
- `ff-api/src/main/go/**` (`ffrt`), `FgCpio`, `FgConstants` initramfs bits.
- `fg_vsock.c` (libkrun owns vsock/TSI), `fg_unix.c` (was Firecracker transport).
- `--fc-path`, `--krn-dir`, `BootSource`, `Drive`, and all disk/block config.

**Rewrite**
- VM lifecycle (`FgVmSvcBuild/Control/Status`), the VM spec model (`FgVm`/`FgConfig`),
  `FgApiHdl`/`FgRoute`, the `FgVmFiles` layout, and the `ff-ui` screens.

---

## 4. Target module layout

```
ff-jni    ✅ Host primitives (JNI) + the native C VM launcher.
          ├─ src/fg/{fg_proc,fg_tap,fg_raw}.c/.h   process spawn/tag, TAP/bridge, raw sockets
          ├─ src/jni/fg_jni.c + io/vacco/ff/net/{FgJni,FgProc}.java
          ├─ src/vmm/fg_vmm.c   native launcher (links libkrun + libkrun_init)
          └─ vendored libkrun.so.2 / libkrun_init.so / libkrunfw.so.5
ff-oci    ✅ OCI registry client + tar extraction to a host rootfs, with a
          persistent content-addressable blob cache. (gson + slf4j-api)
ff-api    ⬜ New domain model, lifecycle services, murmux/ronove REST, shax.
ff-app    ⬜ Hypervisor main (FgMain): owns the REST API, spawns/supervises ff-jni launchers.
ff-ui     ⬜ Preact SPA, dockge-structured, vf-* styled.
ff-test   ✅ Centralized j8spec tests (depends on ff-jni, ff-oci).
```

Dependency direction: `ff-oci` is standalone; `ff-test` → `ff-jni`, `ff-oci`.
`ff-krun` and `ff-vmm` were **retired** in M3 (the C launcher owns libkrun).
`ff-api`/`ff-app`/`ff-ui` are not started yet.

---

## 5. Runtime architecture — process-per-VM + `/proc` re-discovery

- **Spawn:** the hypervisor spawns one native launcher (`ff-jni`'s `fg_vmm`) per VM
  via `FgProc.spawn` → `fg_proc.c` (`fork` + `setsid`, stdin `/dev/null`, stdout/stderr
  → `vm.log`, `LD_LIBRARY_PATH` set to the extracted libs). The launcher builds the
  libkrun VMM and calls `krun_vmm_run`, which **never returns** — libkrun `_exit()`s
  the process when the guest exits (see §0).
- **Volumes (M3):** the launcher `unshare`s a mount namespace and **bind-mounts each
  host directory into the rootfs directory** at its guest path, then shares that one
  rootfs directory via virtiofs. Because libkrun's passthrough follows submounts, the
  guest sees the volumes with no guest-side mount. Unprivileged runs use
  `CLONE_NEWUSER|CLONE_NEWNS`; root uses `CLONE_NEWNS`. Mounts vanish with the namespace.
- **Metadata:** the child env carries `FF_VMID=<id>`. Env is visible at
  `/proc/<pid>/environ` — the tagging mechanism, retained deliberately.
- **Re-discovery:** `FgProc.pidOf(vmId)` scans `/proc/[0-9]+/environ` for `FF_VMID=`.
  On hypervisor start, `vmList` combines that with the on-disk specs.
- **Control without IPC:** status = pid liveness + on-disk spec; logs = tail
  `vm.log`; **stop = SIGTERM → SIGKILL**. Because the rootfs is a host directory
  (virtiofs), an abrupt kill does not corrupt a block device, so hard-stop is
  acceptable. Graceful guest shutdown is unavailable on Linux
  (`krun_vmm_handle_shutdown` is macOS-only).
- **Limitation:** re-adopted VMs cannot later attach an interactive console (libkrun
  console needs an fd at build time). v1 = log streaming; an optional per-VM console
  socket can be added later if required.

---

## 6. New VM spec (replaces `FgConfig`)

```
FgVm: id, label, description, state
  image:   { ref, rootfsDir (extracted), entrypoint[], cmd[], env[], workingDir }
  machine: { vcpus, ramMib }
  volumes: [ { hostPath, guestPath, readOnly } ]   // host dirs bind-mounted into the rootfs
  net:     { mode: tap, bridge, tapName, guestMac, ipConfig }
  log:     { path }
```

No boot source, no drives, no kernel path, no socket path.

---

## 7. Milestones

### M0 — Empty the branch & stand up the skeleton
- Commit the existing working-tree deletions so `feature/libkrun` is truly empty.
- Copy the module skeleton + Gradle 9 / Java 25 toolchain; port `settings.gradle.kts`,
  gitflow/ronove plugins.
- Verify `io.vacco.oss.gitflow` v1.0.1 and ronove 1.2.6 resolve/run under Gradle 9
  (rebuild/publish the local siblings `murmux`/`ronove`/`shax` if not).
- Vendor `libkrun.so.2`, `libkrun_init.so`, `libkrunfw.so.5` into `ff-krun` resources.
- **Exit:** `gradle build` succeeds; a trivial FFM test calls `krun_check_nested_virt()`
  and `krun_init_log()`.

### M1 — "Hello VM"  (superseded by M3's C launcher)
- First implemented with Java/FFM bindings; retired in M3 in favour of C.
- **Exit (met):** a guest ran `/bin/sh -c 'echo hi'` over a rootfs dir.

### M2 — Container as microVM
- `FgDockerIo` extracts OCI images into a host rootfs dir (persistent blob cache).
- libkrun's init is driven with the image's `Entrypoint`/`Cmd`/`Env`/`WorkingDir`.
- **Exit (met):** a real image (`alpine`) boots and prints its output.

### M3 — Host volumes  ✅
- Native C launcher (`ff-jni/src/vmm/fg_vmm.c`) using the libkrun builder API.
- Volumes are host directories **bind-mounted into the rootfs dir** inside a private
  mount namespace, then shared via the single rootfs virtiofs device (§5).
- `FgProc` spawns/tags (`FF_VMID`) the launcher and re-discovers it via `/proc`.
- Retired `ff-krun` and `ff-vmm`; vendored libs moved into `ff-jni`.
- **Exit (met):** RW volume is readable/writable from the guest and reflected on the
  host; RO volume rejects writes; `FF_VMID` discovery finds a running VM.

### M4 — Networking (TAP + bridge)
- `krun_net_device_new_tap(id, tapName, mac, features)`; reuse `fg_tap.c` for
  create/attach/teardown and `fg_raw.c` DHCP.
- Pass `ip=`/`dns=` via `krun_payload_append_cmdline` (or enable
  `krun_init_builder_dhcp` if the bridge runs a DHCP server — see open decisions).
- **Exit:** the guest gets a LAN IP and reaches the network.

### M5 — Process supervisor + API
- Hypervisor (`ff-app`) spawns/supervises `ff-jni` launchers via `FgProc`; re-adopts
  running VMs via `FgProc.pidOf` (`/proc`).
- New `FgApiHdl`: list/create/start/stop/status/logs (+ console tail), backed by
  the new lifecycle. Keep ronove TS codegen.
- **Exit:** create/start a VM via REST; kill the hypervisor, restart it, and the VM
  is re-discovered and still stoppable.

### M6 — dockge-structured UI
- Keep Preact + the generated `rpc.ts`. Restructure to `vf-*`: `vf-detail`
  master/detail (stack list + detail), `vf-pill` status badges, `vf-card` VM cards,
  `vf-panel`, `vf-progress`, `vf-empty` (FgNoData), `vf-lock-overlay` (FgLock),
  `vf-toast` (FgToast), `vf-error`, `vf-stats`.
- Copy `main.scss`/`fonts.scss` from `vf-ui/res`; add widget classes as needed.
- **Exit:** VM list + detail/edit + logs resemble dockge's layout on our backend.

### M7 — Packaging, tests, docs
- Package `ff-app` (GraalVM native-image or jlink); ship the `ff-jni` launcher and
  vendored libs as resources.
- Tests: image pull, rootfs build, boot, volumes, TAP/DHCP, process re-discovery.
- Rewrite README/docs; CI via `.github/workflows`.

---

## 8. Risks & gotchas

1. `krun_vmm_run` **never returns**: libkrun `_exit()`s its host process on guest
   shutdown, so there is no in-process stop. This is why VMs run process-per-VM;
   plan for SIGKILL.
2. `libkrunfw.so.5` is `dlopen`'d **by soname** inside libkrun. The launcher relies
   on `LD_LIBRARY_PATH` (set by `FgProc` to the extracted libs dir) to resolve it.
3. **Volume mounts need a mount namespace**: `CLONE_NEWNS` as root,
   `CLONE_NEWUSER|CLONE_NEWNS` unprivileged. Some hosts disable unprivileged user
   namespaces; the hypervisor runs as root so this is a test-only concern.
4. **Which filesystem the guest sees**: bind mounts only expose *directories* on the
   host; file-level volume targets are not supported yet. Recursive read-only needs
   `mount_setattr(2)`.
5. Gradle 9 / Java 25 vs. JDK21-era gitflow/ronove tooling — verified in M0.
6. `/proc/<pid>/environ` discovery assumes the tag env survives; also stamp
   `cmdline`/`comm` as a secondary marker if needed.
7. **`ETXTBSY`**: extracting the launcher with an unclosed `OutputStream` makes
   `execve` fail with "Text file busy"; always close the output stream first.

---

## 9. Open sub-decisions (not blocking M3)

- **M4 DHCP:** keep the host-side DHCP-client trick (reuse `FgDhcp*`, pass `ip=` via
  cmdline) **vs.** run a DHCP server on the bridge and use
  `krun_init_builder_dhcp(true)`. Recommend the former (reuses existing code, matches
  old behavior).
- **Recursive RO volumes:** use `mount_setattr(2)`/`AT_RECURSIVE` if nested submounts
  within a volume must also be read-only.
- **Vendored `.so` size:** ~27.5 MB total (`libkrunfw.so.5.6.2` is ~21 MB). Plain git
  is fine; Git LFS if repo size becomes a concern.

---

## 10. Reference artifacts

- libkrun headers: `libkrun-build/out/include/{libkrun.h,libkrun_init.h}`
- libkrun libs: `libkrun-build/out/lib64/{libkrun.so.2,libkrun_init.so}`,
  `libkrun-build/out/lib/x86_64-linux-gnu/libkrunfw.so.5`
- libkrun examples: `libkrun-build/src/libkrun/examples/chroot_vm.c`,
  `.../tests/test_cases/src/{common.rs,rootfs.rs}`
- Old sources: `frag-falcon-main/`
- UI reference: `dockge/frontend/src/`, `vgmusic-restoration/vgr-funding/vf-ui/res/main.scss`
