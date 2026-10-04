# Development & API

## Build & test

Toolchain: **Java 25** (with GraalVM native-image) and **Gradle** on `PATH`.

```bash
gradle build                 # compile + j8spec tests, all modules
gradle :flc:distNativeTar    # build the release distribution
gradle :ff-jni:compileJava    # fast compile of one module
gradle :ff-test:test         # integration tests
gradle :ff-test:test --rerun-tasks   # force re-run (boot test is not cheap)
```

- Tests use **j8spec**. The Alpine boot test requires **`/dev/kvm`** access and
  **network** (it pulls the image). Volume tests need a mount namespace; the
  launcher uses an unprivileged user namespace, so no root is required.
- Build-only CI (no KVM/caps/network):
  `gradle :ff-test:test -PskipPrivilegedTests` (or `FF_SKIP_PRIVILEGED_TESTS=1`)
  excludes the boot/DHCP/E2E/image-pull tests.
- `ff-jni`'s native code is built by `make` (invoked from Gradle's `nativeBuild`
  task); it needs `cc` and `JAVA_HOME`.
- The network test needs `cap_net_admin` on the launcher: run
  `sudo bash ff-jni/setup-caps.sh` (again after any launcher rebuild).

## UI tests

The Preact SPA is bundled into `flc` (see `ff-ui/`). Browser E2E tests and a
visual-audit capture run against a live hypervisor:

```bash
# one-shot: builds, starts a throwaway backend, runs the suite, tears down
ff-test/e2e.sh

# or, with a hypervisor already running on 127.0.0.1:7070
npm --prefix ff-ui run test:e2e   # assertions (or: gradle :ff-ui:e2eTest)
npm --prefix ff-ui run visual     # per-screen screenshots (or: gradle :ff-ui:visual)
```

The visual capture walks each screen/state at desktop (1440×950) and mobile
(390×844) viewports and writes full-page PNGs to
`ff-ui/build/test-artifacts/visual/`. `FF_UI_URL` overrides the target.

## API contract

All operations are stack-oriented (see [Using `flc`](30-usage.md) for the
endpoint list). Every controller endpoint returns an `RvResult` envelope whose
body is set on **both** success and failure — validation failures carry
`validations`, and concurrent operations return **HTTP 409**. See
[CAVEATS §16 (error contract)](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md)
and [§18 (locking)](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md).

## Module layout

```
ff-jni    Host primitives (JNI): process spawn, kernel-confined tar extraction,
          bridge discovery; the C VM launcher; vendored libkrun libs.
ff-api    Domain model, VM lifecycle, stack model + supervisor, the ronove REST
          API, and the OCI client.
ff-app    Packaging (project name `flc`): the GraalVM native executable and the
          release distribution.
ff-test   Integration and security tests.
ff-ui     Preact SPA (the ff-* design system).
```

Next: [Resources](90-resources.md).
