#!/bin/bash
#
# Builds the image of every Swag Shop service for linux/amd64 and linux/arm64 and pushes each as one
# multi-arch image (manifest list) to quay.io/$QUAY_USER/swag-shop-<service>:latest.
#
#   scripts/build-push-container.sh                  every service
#   scripts/build-push-container.sh search pricing   only these services

set -euo pipefail

ALL_SERVICES="frontend catalog search pricing inventory order load-generator"

# Fail if QUAY_USER is not set or empty
if [[ -z "${QUAY_USER:-}" ]]; then
  echo "Error: QUAY_USER is not set."
  echo "Please export QUAY_USER before running this script, e.g.:"
  echo "  export QUAY_USER=your-quay-username"
  exit 1
fi

SERVICES="${*:-$ALL_SERVICES}"
for service in $SERVICES; do
  if [[ " $ALL_SERVICES " != *" $service "* ]]; then
    echo "Error: unknown service $service (services: $ALL_SERVICES)"
    exit 1
  fi
done
cd "$(dirname "$0")/.."

# Build the services; the images only copy <service>/target/quarkus-app, which runs on any architecture
./mvnw -B -q package -DskipTests -pl "$(echo "$SERVICES" | tr ' ' ',')"

for service in $SERVICES; do
  IMAGE="quay.io/$QUAY_USER/swag-shop-$service:latest"
  echo "==> $IMAGE"

  # The manifest list needs the image name for itself. Free it if a previous build left a manifest
  # list, or if the image was pulled or run locally (that stores a single-arch image under the same
  # name, and "podman manifest create" then fails with "that name is already in use").
  if podman manifest exists "$IMAGE"; then
    podman manifest rm "$IMAGE" >/dev/null
  elif podman image exists "$IMAGE"; then
    echo "Removing the local tag $IMAGE (a single-arch image, e.g. from podman pull/run)"
    podman untag "$IMAGE" "$IMAGE"
  fi

  podman manifest create "$IMAGE" >/dev/null
  podman build --platform linux/amd64,linux/arm64 --manifest "$IMAGE" --build-arg SERVICE="$service" \
    -f docker/Dockerfile.jvm .
  podman manifest push --all "$IMAGE"
done

# The manifest lists keep the names; running an image locally afterwards pulls it under the same
# name again, which the next run of this script handles.
echo ""
echo "=============================================="
echo "✅ Build and push successful!"
for service in $SERVICES; do
  echo "Image pushed to: quay.io/$QUAY_USER/swag-shop-$service:latest"
done
echo ""
echo "Deploy them to OpenShift with:"
echo "  scripts/openshift-deploy.sh"
echo "=============================================="
