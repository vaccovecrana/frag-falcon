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

`deploy/setup.sh <user> <vm-dir> [install-dir] [oci-dir]` performs the one-time
setup (it allocates a free subuid/subgid block for the user and installs
`fg_usermap`), and `deploy/flc.service` is a sample systemd unit. The hypervisor
never needs root.

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
# 1. Unpack the release somewhere persistent
sudo mkdir -p /opt/flc
sudo tar -xzf frag-falcon-{{gsVersion}}.tar.gz -C /opt/flc --strip-components=1

# 2. One-time root setup: service user, kvm group, vm-dir + oci-dir
sudo bash /opt/flc/deploy/setup.sh flc /var/lib/flc /opt/flc /var/lib/flc-oci

# 3. Harden the vm-dir (host-wide, so a private mount namespace is not enough)
sudo mount --bind /var/lib/flc /var/lib/flc
sudo mount -o remount,bind,nosuid,nodev,noexec /var/lib/flc

# 4. Install and start the service (grants CAP_NET_ADMIN via AmbientCapabilities)
sudo cp /opt/flc/deploy/flc.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now flc
```

The sample `deploy/flc.service` runs as the service user with the `kvm` group and
`AmbientCapabilities=CAP_NET_ADMIN`. The `nosuid,nodev,noexec` mount should be
made permanent in `/etc/fstab`.

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
