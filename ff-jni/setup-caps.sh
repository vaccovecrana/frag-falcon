#!/usr/bin/env bash
#
# Developer setup: grants the VM launcher the network capabilities it needs to
# create/attach/open TAP devices without root. Run once, and again after every
# launcher rebuild (`gradle :ff-jni:nativeBuild`).
#
#   sudo bash ff-jni/setup-caps.sh
#
# Production does NOT use this: operators untar a flat distribution and run
# `flc` as root, where no capabilities are needed.
#
# Without this, networking integration tests skip themselves; volume tests do
# not need it (they use unprivileged user namespaces).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# The launcher is executed from wherever FgNative resolves its home: the
# Gradle build dirs (dev/tests, gradle run) or a flat distribution. Cap every
# copy that exists so any spawn path works.
candidates=(
  "$here/out/fg_vmm"
  "$here/build/native/fg_vmm"
  "$here/build/resources/main/io/vacco/ff/fg_vmm"
)

applied=0
for bin in "${candidates[@]}"; do
  if [ -f "$bin" ]; then
    setcap 'cap_net_admin+ep' "$bin"
    echo "applied cap_net_admin to $bin"
    getcap "$bin"
    applied=1
  fi
done

if [ "$applied" -eq 0 ]; then
  echo "error: no fg_vmm found; build it first with: make -C $here" >&2
  exit 1
fi
