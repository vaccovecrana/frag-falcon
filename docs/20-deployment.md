# Deployment

`frag-falcon` (the `flc` executable) ships as a flat `tar.gz`: the GraalVM native
hypervisor plus the native launcher and libkrun shared objects, all in one
directory.

```
frag-falcon-{{gsVersion}}/
  flc                 # the hypervisor (GraalVM native executable)
  fg_vmm              # per-VM native launcher
  fg_usermap          # subuid/subgid -> user-namespace mapping helper
  fg_jni.so           # host primitives (JNI)
  libkrun.so.2
  libkrun_init.so
  libkrunfw.so.5
```

Unpack it and run the executable from that directory — it finds `fg_vmm` and the
libkrun libraries beside itself. Download the latest archive from the
[releases page](https://github.com/vaccovecrana/frag-falcon/releases).

## Rootless by design

`flc` is designed to run **unprivileged**. Privilege is delegated to the OS once,
not reimplemented in the hypervisor:

- a dedicated service user owns the VM storage dir (`--vm-dir`) and the OCI cache
  dir (`--oci-dir`);
- the `fg_vmm` launcher receives `cap_net_admin` (for TAP devices) from the
  systemd unit's `AmbientCapabilities=CAP_NET_ADMIN`;
- the `fg_usermap` helper carries `cap_setuid,cap_setgid` (root-owned, mode
  `0750`) and maps the service user's `/etc/subuid`/`/etc/subgid` range into each
  VM's user namespace, so guest images can run as arbitrary uids (not just root);
- the service user is in the **`kvm`** group (for `/dev/kvm`);
- the VM storage dir is mounted **`nosuid,nodev,noexec`** (host-wide, e.g. via
  fstab) so files a guest plants in its rootfs — setuid binaries, device nodes,
  executables — are inert to host-side processes. The hypervisor audits this at
  startup and logs a warning if it is missing. (The `--oci-dir` blob cache holds
  only downloaded layers and needs no such hardening.)

`deploy/setup.sh` is a standalone installer/upgrader: it downloads the latest
release tarball, provisions the service user and directories, installs the
binaries and their capabilities, hardens the vm-dir, and installs a customized
`flc.service`. Re-run it to upgrade. The hypervisor never needs root.

`flc` **refuses to start** if it lacks `CAP_NET_ADMIN` (the systemd unit grants it
via `AmbientCapabilities`), since it could not create the per-VM TAP devices a
stack needs.

> **Do not `setcap` the deployed `fg_vmm`.** A file-capped binary runs in the
> loader's secure-execution mode, which ignores `$ORIGIN`; the launcher would then
> fail to find the `libkrun*.so` files sitting beside it. `AmbientCapabilities`
> grants the same capability without triggering secure mode, so the flat
> distribution stays relocatable. See
> [CAVEATS §4](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md).

## Quick start (Debian/systemd)

```bash
# 1. Download and run the installer (as root). It prompts for the service user,
#    vm-dir, oci-dir and API host; press Enter to accept the defaults.
curl -fsSL https://raw.githubusercontent.com/vaccovecrana/frag-falcon/main/deploy/setup.sh -o /tmp/ff-setup.sh
sudo bash /tmp/ff-setup.sh

# 2. Apply the vm-dir hardening the installer added to /etc/fstab
sudo systemctl daemon-reload && sudo mount -a

# 3. Enable and start the service
sudo systemctl enable --now flc
```

Defaults: user `flc`, `--vm-dir=/var/lib/flc/vm`, `--oci-dir=/var/lib/flc/oci`,
`--install-dir=/opt/flc`, `--api-host=127.0.0.1`, `--api-port=7070`.

The installer writes `/etc/systemd/system/flc.service` with the values you chose
and grants `fg_vmm` `CAP_NET_ADMIN` via `AmbientCapabilities`. `deploy/flc.service`
mirrors that template for reference.

Non-interactive / multi-host (e.g. Ansible):

```bash
sudo bash /tmp/ff-setup.sh --yes \
  --user flc --vm-dir /var/lib/flc/vm --oci-dir /var/lib/flc/oci \
  --api-host 127.0.0.1 --api-port 7070
```

Re-running the script upgrades in place: it reuses the user/dirs/API settings
from the installed unit, replaces the binaries, and re-applies capabilities. It
does **not** start or restart the service — that is left to the operator. To
install a specific release or a local tarball, pass `--version TAG` or
`--url URL` (including `file:///path/to/frag-falcon.tar.gz`).

## Command-line options

```
--vm-dir=PATH        VM storage directory (required)
--oci-dir=PATH       OCI blob cache directory (required)
--api-host=HOST      API bind address (default 127.0.0.1)
--api-port=PORT      API port (default 7070)
--log-format=FORMAT  text|json (default text)
--log-level=LEVEL    error|warning|info|debug|trace (default info)
```

For the full packaging and hardening rationale, see
[CAVEATS §14 and §15](https://github.com/vaccovecrana/frag-falcon/blob/main/CAVEATS.md).

Next: [Using `flc`](30-usage.md).
