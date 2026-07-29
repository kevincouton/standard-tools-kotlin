#!/usr/bin/env bash
set -euo pipefail

PODMAN_SOCKET="${PODMAN_SOCKET:-$HOME/.local/share/containers/podman/machine/podman.sock}"
act \
  --container-runtime podman \
  --container-daemon-socket "unix://$PODMAN_SOCKET" \
  --container-architecture linux/amd64 \
  "$@"
