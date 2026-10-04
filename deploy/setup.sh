#!/usr/bin/env bash
#
# One-time root setup for a *rootless* frag-falcon deployment.
#
#   sudo bash deploy/setup.sh <user> <vm-dir> [install-dir] [oci-dir]
#
# It:
#   1. creates the service user (if missing) and adds it to the `kvm` group,
#   2. creates the VM storage dir and the OCI blob cache dir, owned by that user,
#   3. prints the mount command that hardens the VM storage dir.
#
# cap_net_admin (needed for TAP devices) is granted by the systemd unit via
# AmbientCapabilities=CAP_NET_ADMIN; do NOT `setcap` the launcher. A file-capped
# binary runs in the loader's secure-execution mode, which ignores $ORIGIN, so it
# cannot resolve the libkrun shared objects sitting beside it.
#
# frag-falcon itself never runs as root. Step 3 (mounting the vm-dir
# nosuid,nodev,noexec) is what makes guest-planted setuid files, device nodes
# and executables inert to host-side processes; the hypervisor only *audits* it
# at startup (reading /proc/self/mountinfo) and logs a warning otherwise. The
# oci-dir only holds downloaded blobs and needs no such hardening.
set -euo pipefail

user="${1:?usage: setup.sh <user> <vm-dir> [install-dir] [oci-dir]}"
vm_dir="${2:?usage: setup.sh <user> <vm-dir> [install-dir] [oci-dir]}"
install_dir="${3:-$(pwd)}"
oci_dir="${4:-${vm_dir}-oci}"

if [ "$(id -u)" -ne 0 ]; then
  echo "error: run as root (sudo bash deploy/setup.sh ...)" >&2
  exit 1
fi

if ! id "$user" >/dev/null 2>&1; then
  echo "creating user: $user"
  /sbin/useradd --system --create-home --shell /usr/sbin/nologin "$user"
fi

if getent group kvm >/dev/null 2>&1; then
  echo "adding $user to group: kvm"
  /sbin/usermod -aG kvm "$user"
else
  echo "warning: no 'kvm' group; ensure $user can read/write /dev/kvm" >&2
fi

echo "creating VM storage dir: $vm_dir"
mkdir -p "$vm_dir"
chown "$user":"$user" "$vm_dir"
chmod 0750 "$vm_dir"

echo "creating OCI cache dir: $oci_dir"
mkdir -p "$oci_dir"
chown "$user":"$user" "$oci_dir"
chmod 0750 "$oci_dir"

cat <<EOF

Setup complete. cap_net_admin is granted by the systemd unit
(AmbientCapabilities=CAP_NET_ADMIN) — do NOT setcap $install_dir/fg_vmm.

Hardening the VM storage dir is the operator's responsibility
(it must be host-wide, so a private mount namespace is not enough):

  # one-time, host-wide (fstab):
  $vm_dir $vm_dir none bind,nosuid,nodev,noexec 0 0

  # or immediately, for the running system:
  mount --bind $vm_dir $vm_dir
  mount -o remount,bind,nosuid,nodev,noexec $vm_dir

frag-falcon will log a warning at startup if the vm-dir is not hardened.
See deploy/flc.service for a sample systemd unit.
EOF
