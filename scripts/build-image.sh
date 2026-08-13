#!/usr/bin/env bash
set -euo pipefail

RUNTIME="${CONTAINER_RUNTIME:-podman}"
TAG="${1:-standard-tools-kotlin:latest}"

echo "Building image with $RUNTIME as $TAG..."
$RUNTIME build -t "$TAG" .
echo "Image $TAG built successfully."
