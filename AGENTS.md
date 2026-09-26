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
gradle :ff-krun:compileJava  # fast compile of one module
gradle :ff-test:test         # integration tests
gradle :ff-test:test --rerun-tasks   # force re-run (boot test is not cheap)
```

- Tests use **j8spec**: annotate classes with `@DefinedOrder` +
  `@RunWith(J8SpecRunner.class)` and declare examples in a `static { it("...", () -> {...}); }` block.
- The Alpine boot test requires **`/dev/kvm`** access and **network** (it pulls the image).
- Native-access warning: test JVMs pass `--enable-native-access=ALL-UNNAMED` (already configured in `ff-test`/`ff-krun`).
- CI/GitHub Actions is intentionally **out of scope for now** — do not build it out yet.

## Module layout

```
ff-krun   Java 25 FFM bindings to libkrun 2.0 + libkrun_init; vendors the .so files.
ff-oci    OCI registry client + tar extraction to a rootfs dir, with a persistent
          content-addressable blob cache (gson + slf4j-api).
ff-vmm    Per-VM launcher process (main class).
ff-test   Centralized tests for all modules.
ff-host   (planned) TAP/raw/proc + DHCP host primitives.
ff-api    (planned) domain model, lifecycle services, murmux/ronove REST, shax.
ff-app    (planned) hypervisor main: REST API, forks/supervises ff-vmm.
ff-ui     (planned) Preact SPA.
```

Dependency direction: `ff-oci` and `ff-krun` are standalone; `ff-vmm → ff-krun`;
`ff-test → ff-krun, ff-oci, ff-vmm`. Do not introduce cycles.

## Conventions

- **Class naming: every Java class MUST use the `Fg` prefix** (e.g. `FgKrun`,
  `FgKrunVm`, `FgKrunLib`, `FgVmmMain`, `FgDockerIo`). This mirrors the old codebase.
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
   `ff-test` forks `ff-vmm` via `ProcessBuilder` and asserts on its console.
2. **Load libkrun `RTLD_GLOBAL`.** `krun_init_config_apply` resolves libkrun symbols
   via weak `dlsym(RTLD_DEFAULT)`. `FgKrunLib` uses `dlopen(RTLD_NOW | RTLD_GLOBAL)`;
   `System.load` (RTLD_LOCAL) causes `APPLY_SYMBOL_NOT_FOUND`.
3. **Console ports must be named** `krun-stdin` / `krun-stdout` / `krun-stderr` —
   use `krun_console_builder_add_default_console`. Custom-named inout ports yield no
   workload stdout.
4. **`krun_init_log` is once-per-process** (repeat calls return a VMM `Internal`
   error); it is guarded in `FgKrun`.
5. **OCI extraction must be idempotent**: clear the rootfs dir before extraction and
   delete existing entries before recreating symlinks/hardlinks.

## Technology choices

- Backend frameworks: **murmux** (HTTP), **ronove** (REST + TS codegen), **shax**
  (slf4j backend). Use **ronove plugin `3.0.0`** and dependency
  `io.vacco.ronove:rv-kit-murmux:3.0.0` (includes murmux and building blocks).
- Build plugin: **`io.vacco.oss.gitflow` 1.9.0** (org config `vacco-oss-java-25`).
- Logging: `io.vacco.shax:shax:2.0.18.1`; JSON: `com.google.code.gson:gson:2.11.0`.
- FFM bindings are **hand-written & curated**, validated against `jextract 25`
  (installed on PATH). `jextract` output is a throwaway oracle — never commit it.
  `KrunStr`/`KrunBytes` are passed **by value** via `StructLayout`.

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
