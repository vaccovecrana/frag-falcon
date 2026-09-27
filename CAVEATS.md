# CAVEATS.md — libkrun integration caveats & local patches

Non-obvious behaviours we had to work around while wiring frag-falcon to
libkrun 2.0. Each entry says what the constraint is, why it exists, and what we
do about it.

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

## 4. File capabilities break `LD_LIBRARY_PATH`

A binary with file capabilities (e.g. `cap_net_admin+ep` on `fg_vmm`) runs in
the loader's **secure-execution mode**, where `LD_LIBRARY_PATH`/`LD_PRELOAD` are
ignored. To make the launcher work both rootless (setcap) and as root
(`LD_LIBRARY_PATH`), it:
- links `libkrunfw.so.5` **directly** (a `DT_NEEDED` entry, via
  `-Wl,--no-as-needed`) so libkrun's `dlopen("libkrunfw.so.5")` finds the
  already-loaded soname, and
- builds with an absolute `RUNPATH` to the vendored libs (honoured in secure
  mode; `LD_LIBRARY_PATH` still takes precedence for root/production).

`FgProc` also honours `FF_VMM_BIN` / `FF_VMM_LIBDIR` so dev/tests run the
setcap'd built launcher instead of the temp extraction.

---

## 5. File capabilities make the launcher non-dumpable (`/proc/environ`)

A process that gains file capabilities has `dumpable=0`, so
`/proc/<pid>/environ` is root-only. Our `/proc` VM re-discovery can't read it as
a normal user. The launcher sets its process name to `ff-<vmid>`
(`prctl(PR_SET_NAME)`), and `FgProc.pidOf` matches on `/proc/<pid>/comm`
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

---

## 12. Bounded log ring (launcher-owned)

The launcher redirects the guest console to a pipe; a reader thread keeps the
last `--log-lines` (default 4096) lines in memory and rewrites `vm.log`
**atomically** (temp + rename) every ~500 ms and every 64 new lines. The file is
therefore bounded, and it survives hypervisor restarts (the launcher owns it).
`FgVmLaunch` passes `--log-file <service>/vm.log --log-lines 4096`.

Trade-offs: messages beyond the last N are dropped, and because libkrun
`_exit()`s the launcher, the final few lines after the last flush can be lost.
Both are acceptable given the intent (containers ship their own telemetry).



