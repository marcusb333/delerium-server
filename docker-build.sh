#!/bin/bash

# Docker build and push script for delerium-paste server
# Usage: ./docker-build.sh [version] [registry] [username]
# Examples:
#   ./docker-build.sh 1.0.0 dockerhub myusername
#   ./docker-build.sh 1.0.0 ghcr myusername
#   ./docker-build.sh latest dockerhub myusername

set -e

VERSION=${1:-latest}
REGISTRY=${2:-dockerhub}
USERNAME=${3:-${DOCKERHUB_USERNAME:-${GITHUB_USERNAME:-"your-username"}}}

IMAGE_NAME="delerium-paste-server"

# Determine registry URL and full image name
case "${REGISTRY}" in
    dockerhub|docker.io)
        REGISTRY_URL="docker.io"
        FULL_IMAGE_NAME="${USERNAME}/${IMAGE_NAME}:${VERSION}"
        LATEST_TAG="${USERNAME}/${IMAGE_NAME}:latest"
        ;;
    ghcr|ghcr.io)
        REGISTRY_URL="ghcr.io"
        FULL_IMAGE_NAME="ghcr.io/${USERNAME}/${IMAGE_NAME}:${VERSION}"
        LATEST_TAG="ghcr.io/${USERNAME}/${IMAGE_NAME}:latest"
        ;;
    *)
        echo "Error: Unknown registry '${REGISTRY}'. Use 'dockerhub' or 'ghcr'."
        exit 1
        ;;
esac

echo "=========================================="
echo "Building Docker image"
echo "  Registry: ${REGISTRY_URL}"
echo "  Image: ${FULL_IMAGE_NAME}"
echo "=========================================="

# Build the image
if ! docker build -t "${FULL_IMAGE_NAME}" .; then
    echo "Error: Docker build failed"
    exit 1
fi

# Tag as latest if version is not "latest"
if [ "${VERSION}" != "latest" ]; then
    echo "Tagging as latest: ${LATEST_TAG}"
    docker tag "${FULL_IMAGE_NAME}" "${LATEST_TAG}"
fi

echo ""
echo "✓ Build complete!"
echo ""
echo "To push the image, run:"
echo ""

if [ "${REGISTRY}" = "dockerhub" ] || [ "${REGISTRY}" = "docker.io" ]; then
    echo "  # Login to Docker Hub:"
    echo "  docker login"
    echo ""
    echo "  # Push the image:"
    echo "  docker push ${FULL_IMAGE_NAME}"
    if [ "${VERSION}" != "latest" ]; then
        echo "  docker push ${LATEST_TAG}"
    fi
else
    echo "  # Login to GHCR:"
    echo "  echo \$GITHUB_TOKEN | docker login ghcr.io -u ${USERNAME} --password-stdin"
    echo ""
    echo "  # Push the image:"
    echo "  docker push ${FULL_IMAGE_NAME}"
    if [ "${VERSION}" != "latest" ]; then
        echo "  docker push ${LATEST_TAG}"
    fi
    echo ""
    echo "  Note: Create a GitHub Personal Access Token with 'write:packages' permission"
    echo "        at https://github.com/settings/tokens"
fi

echo ""
echo "Built images:"
docker images | grep "${IMAGE_NAME}" | grep -E "${VERSION}|latest" || true
