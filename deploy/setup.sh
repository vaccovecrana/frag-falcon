#!/usr/bin/env bash
#
# frag-falcon installer / upgrader for a rootless, systemd-based deployment.
#
#   curl -fsSL https://raw.githubusercontent.com/vaccovecrana/frag-falcon/main/deploy/setup.sh -o /tmp/ff-setup.sh
#   sudo bash /tmp/ff-setup.sh
#
# It downloads the latest release tarball from GitHub, provisions the service
# user and directories, installs the binaries (with fg_usermap's file caps),
# hardens the vm-dir in /etc/fstab, installs a customized flc.service, and prints
# the commands to enable and start the service. Re-run it to upgrade: it reads
# the existing unit to reuse the user/dirs/API settings.
#
# Everything can be answered non-interactively with flags (for Ansible etc.):
#
#   --user NAME          service user                 (default flc)
#   --vm-dir PATH        VM working set              (default /var/lib/flc/vm)
#   --oci-dir PATH       OCI blob cache              (default /var/lib/flc/oci)
#   --install-dir PATH   binaries location           (default /opt/flc)
#   --api-host HOST      API bind address            (default 127.0.0.1)
#   --api-port PORT      API port                    (default 7070)
#   --version TAG        install a specific release tag instead of latest
#   --url URL            use an explicit tarball URL (http(s):// or file://)
#   --yes                do not prompt; use flags/defaults
#
# frag-falcon itself never runs as root: cap_net_admin comes from the unit's
# AmbientCapabilities, and the subuid/subgid range is mapped by fg_usermap.
set -euo pipefail

# `su` (without a login shell) can leave the sbin dirs out of PATH, so setcap,
# useradd, usermod and getent are not found. Add them so the script works the
# same whether invoked via `sudo` or `su`.
export PATH="/usr/local/sbin:/usr/sbin:/sbin:${PATH:-/usr/bin:/bin}"

repo_slug="vaccovecrana/frag-falcon"
repo_url="https://github.com/${repo_slug}"
stable_asset="frag-falcon.tar.gz"
unit_path="/etc/systemd/system/flc.service"

user="flc"
install_dir="/opt/flc"
vm_dir="/var/lib/flc/vm"
oci_dir="/var/lib/flc/oci"
api_host="127.0.0.1"
api_port="7070"
version=""
url=""
assume_yes=0

set_user=0
set_vm_dir=0
set_oci_dir=0
set_install_dir=0
set_api_host=0
set_api_port=0

usage() {
  awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 { exit }' "$0"
}

die() {
  echo "error: $*" >&2
  exit 1
}

# Accept both --flag=value and --flag value.
argv=()
for a in "$@"; do
  case "$a" in
    --*=*) argv+=("${a%%=*}" "${a#*=}") ;;
    *) argv+=("$a") ;;
  esac
done
set -- "${argv[@]}"

while [ $# -gt 0 ]; do
  case "$1" in
    --user) user="$2"; set_user=1; shift 2 ;;
    --vm-dir) vm_dir="$2"; set_vm_dir=1; shift 2 ;;
    --oci-dir) oci_dir="$2"; set_oci_dir=1; shift 2 ;;
    --install-dir) install_dir="$2"; set_install_dir=1; shift 2 ;;
    --api-host) api_host="$2"; set_api_host=1; shift 2 ;;
    --api-port) api_port="$2"; set_api_port=1; shift 2 ;;
    --version) version="$2"; shift 2 ;;
    --url) url="$2"; shift 2 ;;
    --yes|-y) assume_yes=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown option: $1 (try --help)" ;;
  esac
done

[ "$(id -u)" -eq 0 ] || die "run as root (sudo bash $0 ...)"
for t in tar setcap systemctl useradd usermod getent awk sed grep mktemp; do
  command -v "$t" >/dev/null 2>&1 || die "$t is required"
done
command -v curl >/dev/null 2>&1 || command -v wget >/dev/null 2>&1 || die "curl or wget is required"

# --- reuse the existing deployment's settings on upgrade --------------------
if [ -f "$unit_path" ]; then
  echo "existing install detected: $unit_path"
  existing_exec="$(sed -n 's/^ExecStart=//p' "$unit_path" | head -1)"
  existing_user="$(sed -n 's/^User=//p' "$unit_path" | head -1)"
  existing_workdir="$(sed -n 's/^WorkingDirectory=//p' "$unit_path" | head -1)"
  existing_vm="$(printf '%s\n' "$existing_exec" | grep -oE -- '--vm-dir=[^ ]+' | head -1 | cut -d= -f2-)"
  existing_oci="$(printf '%s\n' "$existing_exec" | grep -oE -- '--oci-dir=[^ ]+' | head -1 | cut -d= -f2-)"
  existing_host="$(printf '%s\n' "$existing_exec" | grep -oE -- '--api-host=[^ ]+' | head -1 | cut -d= -f2-)"
  existing_port="$(printf '%s\n' "$existing_exec" | grep -oE -- '--api-port=[^ ]+' | head -1 | cut -d= -f2-)"
  [ "$set_user" = 1 ] || user="${existing_user:-$user}"
  [ "$set_vm_dir" = 1 ] || vm_dir="${existing_vm:-$vm_dir}"
  [ "$set_oci_dir" = 1 ] || oci_dir="${existing_oci:-$oci_dir}"
  [ "$set_install_dir" = 1 ] || install_dir="${existing_workdir:-$install_dir}"
  [ "$set_api_host" = 1 ] || api_host="${existing_host:-$api_host}"
  [ "$set_api_port" = 1 ] || api_port="${existing_port:-$api_port}"
fi

# --- interactive prompts (read from the tty, so `curl | bash` still works) ---
prompt() {
  local __var="$1" __label="$2" __def="$3" __ans=""
  if [ "$assume_yes" = 1 ] || [ ! -r /dev/tty ]; then
    printf -v "$__var" '%s' "$__def"
    return
  fi
  read -r -p "$__label [$__def]: " __ans < /dev/tty || __ans=""
  printf -v "$__var" '%s' "${__ans:-$__def}"
}

prompt user        "Service user to create/use" "$user"
prompt vm_dir      "VM storage dir (working set)" "$vm_dir"
prompt oci_dir     "OCI blob cache dir" "$oci_dir"
prompt api_host    "API bind host" "$api_host"

[ -n "$user" ] || die "user must not be empty"
case "$api_port" in
  ''|*[!0-9]*) die "api-port must be a number: $api_port" ;;
esac

# --- resolve the release tarball URL ----------------------------------------
resolve_url() {
  if [ -n "$url" ]; then
    printf '%s\n' "$url"
    return 0
  fi
  local stable="$repo_url/releases/latest/download/$stable_asset"
  if curl -fsIL "$stable" >/dev/null 2>&1; then
    printf '%s\n' "$stable"
    return 0
  fi
  local api
  if [ -n "$version" ]; then
    api="https://api.github.com/repos/$repo_slug/releases/tags/$version"
  else
    api="https://api.github.com/repos/$repo_slug/releases/latest"
  fi
  curl -fsSL "$api" 2>/dev/null \
    | grep -o '"browser_download_url": *"[^"]*\.tar\.gz"' \
    | head -1 \
    | sed 's/.*"\(https[^"]*\)".*/\1/'
}

echo "resolving release..."
tarball_url="$(resolve_url || true)"
[ -n "$tarball_url" ] || die "could not resolve a release tarball; pass --url or --version"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
tarball="$tmp_dir/frag-falcon.tar.gz"

echo "downloading: $tarball_url"
if command -v curl >/dev/null 2>&1; then
  curl -fL --retry 3 -o "$tarball" "$tarball_url"
else
  wget -O "$tarball" "$tarball_url"
fi
tar -tzf "$tarball" >/dev/null 2>&1 || die "downloaded file is not a gzip tarball"

# --- stop the running service before replacing its binary -------------------
if [ -f "$unit_path" ] && systemctl is-active --quiet flc 2>/dev/null; then
  echo "stopping flc (upgrade)"
  systemctl stop flc
fi

# --- service user, kvm group ------------------------------------------------
if ! id "$user" >/dev/null 2>&1; then
  echo "creating user: $user"
  useradd --system --create-home --shell /usr/sbin/nologin "$user"
fi

if getent group kvm >/dev/null 2>&1; then
  usermod -aG kvm "$user"
else
  echo "warning: no 'kvm' group; ensure $user can read/write /dev/kvm" >&2
fi

# --- directories ------------------------------------------------------------
for d in "$vm_dir" "$oci_dir"; do
  echo "creating dir: $d"
  mkdir -p "$d"
  chown "$user":"$user" "$d"
  chmod 0750 "$d"
done

# --- subuid/subgid range (arbitrary guest uids via fg_usermap) --------------
subid_min="$(awk '/^SUB_UID_MIN/{print $2}' /etc/login.defs 2>/dev/null || true)"
subid_count="$(awk '/^SUB_UID_COUNT/{print $2}' /etc/login.defs 2>/dev/null || true)"
subid_min="${subid_min:-100000}"
subid_count="${subid_count:-65536}"

block_free() {
  local file="$1" start="$2" end="$3" _n s c e
  [ -f "$file" ] || return 0
  while IFS=: read -r _n s c; do
    [ -z "${s:-}" ] && continue
    e=$((s + c - 1))
    if [ "$start" -le "$e" ] && [ "$end" -ge "$s" ]; then
      return 1
    fi
  done < "$file"
  return 0
}

if grep -qE "^${user}:" /etc/subuid 2>/dev/null && grep -qE "^${user}:" /etc/subgid 2>/dev/null; then
  echo "subuid/subgid range already configured for $user"
else
  start="$subid_min"
  while :; do
    end=$((start + subid_count - 1))
    if block_free /etc/subuid "$start" "$end" && block_free /etc/subgid "$start" "$end"; then
      break
    fi
    start=$((end + 1))
  done
  echo "allocating subuid/subgid range ${start}-${end} for $user"
  usermod --add-subuids "${start}-${end}" --add-subgids "${start}-${end}" "$user"
fi

# --- install binaries -------------------------------------------------------
echo "installing binaries into: $install_dir"
mkdir -p "$install_dir"
tar -xzf "$tarball" -C "$install_dir" --strip-components=1

helper="$install_dir/fg_usermap"
if [ -f "$helper" ]; then
  echo "setting fg_usermap capabilities"
  chown root:"$user" "$helper"
  chmod 0750 "$helper"
  setcap 'cap_setuid,cap_setgid+ep' "$helper"
else
  echo "warning: $helper not found; the launcher will fall back to a single-uid map" >&2
fi

# --- harden the vm-dir in /etc/fstab ----------------------------------------
fstab_changed=0
if ! awk -v d="$vm_dir" '$1==d && $2==d {found=1} END{exit !found}' /etc/fstab 2>/dev/null; then
  echo "adding vm-dir hardening to /etc/fstab"
  printf '%s %s none bind,nosuid,nodev,noexec 0 0\n' "$vm_dir" "$vm_dir" >> /etc/fstab
  fstab_changed=1
fi

# --- install the systemd unit -----------------------------------------------
echo "writing $unit_path"
cat > "$unit_path" <<EOF
[Unit]
Description=frag-falcon libkrun microVM hypervisor
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$user
Group=$user
WorkingDirectory=$install_dir
ExecStart=$install_dir/flc --vm-dir=$vm_dir --oci-dir=$oci_dir --api-host=$api_host --api-port=$api_port
AmbientCapabilities=CAP_NET_ADMIN
CapabilityBoundingSet=CAP_NET_ADMIN CAP_SETUID CAP_SETGID
Restart=on-failure
StandardOutput=syslog
StandardError=inherit
SyslogIdentifier=flc

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload

# --- final instructions -----------------------------------------------------
cat <<EOF

Setup complete.

  service user : $user (group $user, member of kvm)
  install dir  : $install_dir
  vm-dir       : $vm_dir
  oci-dir      : $oci_dir
  api          : $api_host:$api_port
  unit         : $unit_path

Next steps (run as root):

EOF

if [ "$fstab_changed" = 1 ]; then
  cat <<EOF
  1. Apply the vm-dir hardening added to /etc/fstab:
       systemctl daemon-reload && mount -a

EOF
  step=2
else
  step=1
fi

cat <<EOF
  $step. Enable and start the service:
       systemctl enable --now flc

Then open http://$api_host:$api_port/ (no authentication: keep it on a private
LAN segment or behind a VPN). Logs: journalctl -u flc -f.

To upgrade later, re-run this script; it reuses the settings above and leaves
starting the new binary to you.
EOF
