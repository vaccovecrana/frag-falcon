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
| M0 — Branch & skeleton | ✅ Done | Branch emptied; Gradle 9.2.1 / Java 25 toolchain; `ff-krun` module + vendored libs; smoke test green. |
| M1 — "Hello VM" | ✅ Done | Merged into M2: FFM builder bindings validated against `jextract`; `FgKrunVm` fluent wrapper. |
| M2 — Container as microVM | ✅ Done | `ff-oci` (persistent cache) + `ff-vmm` child process; Alpine boots and prints to console. |
| M3 — Host volumes | 🔜 Next | `FgKrunVm.volume(...)` and `--volume HOST:GUEST[:ro]` already plumbed; need a boot test. |
| M4 — Networking (TAP + bridge) | ⬜ | |
| M5 — Supervisor + REST API | ⬜ | process-per-VM + `/proc` re-discovery. |
| M6 — dockge-structured UI | ⬜ | |
| M7 — Packaging, tests, docs | ⬜ | |

### Progress log

- **M0** (`f3dd0d0`): repo skeleton, `FgKrun`/`FgKrunLib`, vendored `libkrun.so.2` /
  `libkrun_init.so` / `libkrunfw.so.5`, smoke test.
- **Naming convention** (`6584993`): all Java classes use the `Fg` prefix.
- **M1/M2** (`8ce215c`): `ff-oci`, `ff-krun` builder bindings + `FgKrunVm`,
  `ff-vmm`, centralized `ff-test`, Alpine boot integration test.

### Key discoveries (folded into implementation)

- **`krun_vmm_run` never returns.** libkrun calls `libc::_exit(exit_code)` when the
  guest shuts down (`src/libkrun/src/vmm/mod.rs:409`, `api/vmm_builder.rs:282`).
  This makes process-per-VM (D1) **mandatory**; a VM must never share the
  hypervisor's (or a test runner's) JVM. `ff-test` forks `ff-vmm` accordingly.
- **init symbol resolution.** `krun_init_config_apply` resolves libkrun symbols via
  weak `dlsym(RTLD_DEFAULT)`, so `libkrun.so.2` must be `dlopen`'d
  `RTLD_NOW | RTLD_GLOBAL` (`System.load` is `RTLD_LOCAL` → `APPLY_SYMBOL_NOT_FOUND`).
- **Console wiring.** Guest workload stdio only reaches the host through ports named
  exactly `krun-stdin` / `krun-stdout` / `krun-stderr`, created by
  `krun_console_builder_add_default_console`.
- **`jextract` as oracle.** The curated FFM bindings were validated against a
  throwaway `jextract 25` run (hybrid approach C). `KrunStr`/`KrunBytes` are passed
  **by value** via `StructLayout`, not as pointers.

---

## 1. Architecture shift

The old framework treated Firecracker as an external daemon: `fork` a binary, talk
its REST API over a UNIX socket, build a cpio initramfs, boot a host kernel, attach
TAP/block devices. libkrun 2.0 is an **in-process C library with a builder API**.

Consequences:

- HTTP-over-UNIX transport, `fc.sock`, `FgFirecracker`, and the entire
  `io.vacco.ff.firecracker.*` schema tree are deleted, replaced by **Java 25 FFM
  (Panama)** downcalls.
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
ff-krun   ✅ Java 25 FFM bindings to libkrun 2.0 + libkrun_init.
          Vendors libkrun.so.2, libkrun_init.so, libkrunfw.so.5 as resources.
ff-oci    ✅ OCI registry client + tar extraction to a host rootfs, with a
          persistent content-addressable blob cache. (gson + slf4j-api)
ff-vmm    ✅ Runnable per-VM launcher (main class). Builds + run()s exactly one VM
          using ff-krun; tagged in /proc; writes vm.json/net.json/vm.pid/vm.log.
ff-host   ⬜ Retained host primitives (TAP/raw/proc fork) — kept on JNI initially,
          FFM-ified later if desired. (was ff-jni)
ff-api    ⬜ New domain model, lifecycle services, murmux/ronove REST, shax.
ff-app    ⬜ Hypervisor main (FgMain): owns the REST API, forks/supervises ff-vmm.
ff-ui     ⬜ Preact SPA, dockge-structured, vf-* styled.
ff-test   ✅ Centralized j8spec tests (depends on ff-krun, ff-oci, ff-vmm).
```

Dependency direction: `ff-oci` and `ff-krun` are standalone; `ff-vmm` → `ff-krun`;
`ff-test` → all three. `ff-host`/`ff-api`/`ff-app` are not started yet.

---

## 5. Runtime architecture — process-per-VM + `/proc` re-discovery

- **Fork:** `FgMain` starts one detached `ff-vmm` child per VM (Java `ProcessBuilder`
  with a custom environment, stdout/stderr redirected to `vmDir/<id>/vm.log`, wrapped
  in `setsid`). Guarded by root. The child builds the libkrun VMM and calls
  `krun_vmm_run`, which **never returns** — libkrun `_exit()`s the process when the
  guest exits (see §0 discoveries).
- **Metadata:** the child env carries `FF_VMID=<id>`, `FF_VM_DIR`, `FF_KIND=libkrun`,
  `FF_LOG`. Env is visible at `/proc/<pid>/environ` (root-readable) — the old
  tagging mechanism, retained deliberately.
- **Re-discovery:** on hypervisor start, `vmList` scans `vmDir`, then scans
  `/proc/[0-9]+/environ` for `FF_VMID=` matching each spec id. A VM is "running"
  iff a live pid matches. `FgIo.pidOf` is generalized into an `FgVmReconciler`.
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
  volumes: [ { hostPath, guestPath, readOnly } ]   // extra virtiofs devices
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

### M1 — "Hello VM"
- FFM bindings for `payload_load_krunfw`,
  `vmm_builder_{new,vcpus,ram_mib,payload,devices,build}`, the console device, `run`,
  and error/userdata handling.
- Resolve the `libkrunfw.so.5` `dlopen` gotcha (pre-`System.load` the absolute path
  before libkrun).
- Boot a guest running `Config::builder().args(["/bin/sh","-c","echo hi"])` over a
  hand-made rootfs dir; capture console.
- **Exit:** console shows `hi`; the process exits cleanly.

### M2 — Container as microVM
- Retarget `FgDockerIo` to extract OCI images into `vmDir/<id>/rootfs`.
- Build the init config from the image's `Entrypoint`/`Cmd`/`Env`/`WorkingDir`
  (`krun_init_builder_from_oci_json` or explicit args).
- `FsDevice` tag `/dev/root` + `set_overlay(init_config)`; delete `ffrt`/`FgCpio`.
- **Exit:** boot a real image (e.g. `alpine`) and see its entrypoint output.

### M3 — Host volumes
- One additional `FsDevice` per volume (`krun_fs_device_new_read_only` for RO).
- **Exit:** a host dir is readable/writable inside the guest at the requested path,
  with no raw disk image anywhere.

### M4 — Networking (TAP + bridge)
- `krun_net_device_new_tap(id, tapName, mac, features)`; reuse `fg_tap.c` for
  create/attach/teardown and `fg_raw.c` DHCP.
- Pass `ip=`/`dns=` via `krun_payload_append_cmdline` (or enable
  `krun_init_builder_dhcp` if the bridge runs a DHCP server — see open decisions).
- **Exit:** the guest gets a LAN IP and reaches the network.

### M5 — Process supervisor + API
- `ff-vmm` launcher; `FgMain` forks/supervises; `FgVmReconciler` for `/proc`
  re-adoption.
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
- GraalVM native-image (or jlink) for both `ff-app` and `ff-vmm`, with FFM + resource
  configs.
- Tests: image pull, rootfs build, boot, exec, TAP/DHCP, process re-discovery.
- Rewrite README/docs; CI via `.github/workflows`.

---

## 8. Risks & gotchas

1. `krun_vmm_run` **never returns**: libkrun `_exit()`s its host process on guest
   shutdown, so there is no in-process stop. This is why VMs run process-per-VM;
   plan for SIGKILL.
2. `libkrunfw.so.5` is `dlopen`'d **by soname** inside libkrun (no env override) —
   preload it by absolute path.
3. libkrun_init's OCI parser reads only `args/env/cwd/mounts` (rlimits are injected
   separately; **capabilities are ignored**) — rich workloads may need cmdline/env
   workarounds.
4. FFM downcalls must not run on JVM-critical threads; each VM's `run` owns its own
   thread/process.
5. Gradle 9 / Java 25 vs. JDK21-era gitflow/ronove tooling — verify early (M0); the
   siblings are local if a republish is needed.
6. `/proc/<pid>/environ` discovery assumes root and an unmodified env; also stamp
   `cmdline`/`comm` as a secondary marker.
7. Native-image + FFM + `dlopen` chains need resource/reflect config; verify before M7.

---

## 9. Open sub-decisions (not blocking M0)

- **M4 DHCP:** keep the host-side DHCP-client trick (reuse `FgDhcp*`, pass `ip=` via
  cmdline) **vs.** run a DHCP server on the bridge and use
  `krun_init_builder_dhcp(true)`. Recommend the former (reuses existing code, matches
  old behavior).
- **`ff-host`:** keep JNI for TAP/DHCP initially, or migrate to FFM in a later pass.
  Recommend keep-JNI now, FFM later.
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
