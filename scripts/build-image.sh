#!/usr/bin/env bash
set -euo pipefail

RUNTIME="${CONTAINER_RUNTIME:-podman}"
TAG="${1:-kotlin-grpc-rest-starter:latest}"

echo "Building image with $RUNTIME as $TAG..."
$RUNTIME build -t "$TAG" .
echo "Image $TAG built successfully."
