#!/usr/bin/env bash
#
# One-time (and after each rebuild) developer setup: grants the VM launcher the
# network capabilities it needs to create/attach/open TAP devices without root.
#
#   sudo bash ff-jni/setup-caps.sh
#
# Without this, networking integration tests skip themselves; volume tests do
# not need it (they use unprivileged user namespaces).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
bin="$here/out/fg_vmm"

if [ ! -f "$bin" ]; then
  echo "error: $bin not found; build it first with: make -C $here" >&2
  exit 1
fi

setcap 'cap_net_admin+ep' "$bin"
echo "applied cap_net_admin to $bin"
getcap "$bin"
