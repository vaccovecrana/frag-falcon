# AGENTS.md — frag-falcon-libkrun

Guidance for agents working in this repository. Read `PLAN.md` for the migration
roadmap and current progress; this file covers conventions and how to work here.

## What this project is

A migration of the Firecracker-based micro-VM manager (`../frag-falcon-main`, the
old sources; **do not edit it**) to **libkrun 2.0**. OCI/Docker images are pulled and
run as microVMs whose rootfs is a **host directory shared via virtiofs** (no raw
disk images). The UI is later restructured to follow the element layout of
`../dockge` while keeping our own Java (murmux/ronove/shax) backend.

Reference material (read-only, outside this repo):
- `../libkrun-build/out/include/{libkrun.h,libkrun_init.h}` — C headers (source of truth).
- `../libkrun-build/out/lib64` and `../libkrun-build/out/lib/x86_64-linux-gnu` — native libs.
- `../libkrun-build/src/libkrun/` — libkrun C sources; `examples/chroot_vm.c` is the canonical builder-API example.
- `../frag-falcon-main/` — old Java/Go/JNI sources to port from.
- `../dockge/frontend/src/` — UX reference.

## Build & test

Toolchain: **Java 25** (`JAVA_HOME` already set) and **Gradle 9.2.1** on PATH. No
wrapper is committed (Gradle is invoked as `gradle`).

```bash
gradle build                 # compile + j8spec tests, all modules
gradle :ff-jni:compileJava    # fast compile of one module
gradle :ff-test:test         # integration tests
gradle :ff-test:test --rerun-tasks   # force re-run (boot test is not cheap)
```

- Tests use **j8spec**: annotate classes with `@DefinedOrder` +
  `@RunWith(J8SpecRunner.class)` and declare examples in a `static { it("...", () -> {...}); }` block.
- The Alpine boot test requires **`/dev/kvm`** access and **network** (it pulls the image).
  Volume tests need a mount namespace: root on the hypervisor, or unprivileged user
  namespaces for local runs.
- `ff-jni`'s native code is built by `make` (invoked from Gradle's `nativeBuild` task);
  it needs `cc` and `JAVA_HOME` (set). Rebuild directly with `make -C ff-jni`.
- The **network test** needs `cap_net_admin` on the launcher: run
  `sudo bash ff-jni/setup-caps.sh` (again after any launcher rebuild). Without it the
  test no-ops.
- CI/GitHub Actions is intentionally **out of scope for now** — do not build it out yet.

## Module layout

```
ff-jni    Host primitives (JNI) + the native C VM launcher; vendors the libkrun
          shared objects. Built by a Makefile (invoked from Gradle) with `cc`.
ff-oci    OCI registry client + tar extraction to a rootfs dir, with a persistent
          content-addressable blob cache (gson + slf4j-api).
ff-test   Centralized tests for all modules.
ff-api    (planned) domain model, lifecycle services, murmux/ronove REST, shax.
ff-app    (planned) hypervisor main: REST API, spawns/supervises ff-jni launchers.
ff-ui     (planned) Preact SPA.
```

Dependency direction: `ff-oci` is standalone; `ff-test → ff-jni, ff-oci`. Do not
introduce cycles. `ff-krun` and `ff-vmm` were retired in M3 (the C launcher owns
libkrun).

## Conventions

- **Code commits**: *never* commit code automatically. Human review is a crucial step
  to code quality. This is achieved by having the human reviewer go through the code
  diff, asking for changes, or agreeing to commit.
- **Class naming: every Java class MUST use the `Fg` prefix** (e.g. `FgJni`,
  `FgProc`, `FgDockerIo`, `FgVmBootTest`). C files use the `fg_` prefix
  (e.g. `fg_proc.c`, `fg_vmm.c`). This mirrors the old codebase.
- **Tests live in `ff-test`**, not in the modules under test. Move/centralize any
  test code there.
- **Incremental migration**: keep what is still useful, drop what libkrun now
  provides. Prefer porting/adapting old code over rewriting from scratch.
- **Do not add comments** unless asked.
- **If you are confused, stop and ask for direction** — do not guess.

## Critical runtime rules (learned the hard way)

1. **Process-per-VM is mandatory.** libkrun calls `libc::_exit()` when the guest
   exits, so `krun_vmm_run` never returns and terminates its host process. Never
   host a VM (or call `krun_vmm_run`) in the hypervisor's JVM or a JUnit test JVM.
   The native launcher `ff-jni/src/vmm/fg_vmm.c` is one process per VM; `FgProc`
   spawns it and `ff-test` waits on it.
2. **Volumes are host-side bind mounts.** The launcher `unshare`s a mount namespace
   (`CLONE_NEWNS` as root, `CLONE_NEWUSER|CLONE_NEWNS` unprivileged) and bind-mounts
   each host dir into the rootfs dir at its guest path; libkrun's virtiofs follows
   submounts, so the guest needs no mount step. libkrun's built-in init only applies
   `tmpfs` mounts — do not expect it to mount virtiofs devices.
3. **Console ports must be named** `krun-stdin` / `krun-stdout` / `krun-stderr` —
   use `krun_console_builder_add_default_console`. Custom-named inout ports yield no
   workload stdout.
4. **Keep the launcher's `LD_LIBRARY_PATH`** pointing at the extracted libkrun libs;
   libkrun `dlopen`s `libkrunfw.so.5` by soname (no env override inside libkrun).
5. **OCI extraction must be idempotent**: clear the rootfs dir before extraction and
   delete existing entries before recreating symlinks/hardlinks.
6. **Close the extraction `OutputStream` before `execve`** — an unclosed stream makes
   the launcher fail with `ETXTBSY` ("Text file busy").
7. **TAP/bridge networking** needs `cap_net_admin`. The tap must be pre-created
   persistent (a tap can only be attached by one process). libkrun's DHCP was patched
   to retry — see `CAVEATS.md` and `patches/libkrun-dhcp-retry.patch`; reapply the
   patch after a fresh `build-libkrun.sh` fetch.
8. **A cap'd launcher is non-dumpable**, so `/proc/<pid>/environ` is root-only;
   discovery matches `/proc/<pid>/comm` (`ff-<vmid>`) first. A cap'd binary also
   ignores `LD_LIBRARY_PATH` (secure-execution mode), hence the launcher's absolute
   `RUNPATH` and direct `libkrunfw` `DT_NEEDED`.
9. Read `CAVEATS.md` before touching libkrun integration, the launcher, or networking.

## Technology choices

- Backend frameworks: **murmux** (HTTP), **ronove** (REST + TS codegen), **shax**
  (slf4j backend). Use **ronove plugin `3.0.0`** and dependency
  `io.vacco.ronove:rv-kit-murmux:3.0.0` (includes murmux and building blocks).
- Build plugin: **`io.vacco.oss.gitflow` 1.9.0** (org config `vacco-oss-java-25`).
- Logging: `io.vacco.shax:shax:2.0.18.1`; JSON: `com.google.code.gson:gson:2.11.0`.
- FFM bindings are **not used** (retired in M3). libkrun is driven from C by the
  native launcher `ff-jni/src/vmm/fg_vmm.c`, compiled by `ff-jni/Makefile` with `cc`
  and linked against the vendored libs via `-l:libkrun.so.2 -l:libkrun_init.so`.
  Host primitives (TAP/raw/proc) are exposed through the JNI shim `fg_jni.c` +
  `FgJni.java`.

## UI direction (when the UI milestone starts)

- Keep the **Preact** codebase; restructure it to mirror dockge's UX element layout
  (master/detail stack list, status pills, action button groups, streaming terminal).
- Adopt the `vf-*` design system by copying `main.scss`/`fonts.scss` from
  `../vgmusic-restoration/vgr-funding/vf-ui/res/`; add widget classes as needed.
- Networking target is **virtio-net + TAP + bridge** (reuse the native TAP/DHCP code).

## Memory (Engram)

The parent directory contains multiple git repos, so Engram may report an
`ambiguous_project`. This project is `frag-falcon-libkrun`. Adding
`.engram/config.json` here would make detection deterministic.
