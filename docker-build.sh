#!/bin/bash

# Multi-architecture Docker build and push script for delerium-paste server
# Usage: ./docker-build.sh [version] [registry] [username] [platforms] [push]
# Examples:
#   ./docker-build.sh 1.0.0 dockerhub myusername
#   ./docker-build.sh 1.0.0 ghcr myusername "linux/amd64,linux/arm64"
#   ./docker-build.sh latest dockerhub myusername "linux/amd64,linux/arm64,linux/arm/v7" push

set -e

VERSION=${1:-latest}
REGISTRY=${2:-dockerhub}
USERNAME=${3:-${DOCKERHUB_USERNAME:-${GITHUB_USERNAME:-"your-username"}}}
PLATFORMS=${4:-"linux/amd64,linux/arm64,linux/arm/v7"}
PUSH_FLAG=${5:-""}

IMAGE_NAME="delerium-server"

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
echo "Building Multi-Architecture Docker Image"
echo "  Registry: ${REGISTRY_URL}"
echo "  Image: ${FULL_IMAGE_NAME}"
echo "  Platforms: ${PLATFORMS}"
echo "=========================================="

# Check if Docker Buildx is available
if ! docker buildx version &> /dev/null; then
    echo "Error: Docker Buildx is not available. Please update Docker to a version that supports Buildx."
    exit 1
fi

# Create or use existing buildx builder
BUILDER_NAME="delerium-multiarch-builder"
if ! docker buildx inspect "${BUILDER_NAME}" &> /dev/null; then
    echo "Creating new buildx builder: ${BUILDER_NAME}"
    docker buildx create --name "${BUILDER_NAME}" --driver docker-container --bootstrap --use
else
    echo "Using existing buildx builder: ${BUILDER_NAME}"
    docker buildx use "${BUILDER_NAME}"
fi

# Build arguments
BUILD_ARGS=(
    "--platform" "${PLATFORMS}"
    "--tag" "${FULL_IMAGE_NAME}"
    "--build-arg" "BUILDKIT_INLINE_CACHE=1"
)

# Add latest tag if version is not "latest"
if [ "${VERSION}" != "latest" ]; then
    BUILD_ARGS+=("--tag" "${LATEST_TAG}")
fi

# Add push flag if specified
if [ "${PUSH_FLAG}" = "push" ]; then
    BUILD_ARGS+=("--push")
    echo "  Mode: Build and Push"
else
    BUILD_ARGS+=("--load")
    echo "  Mode: Build Only (local)"
    echo "  Note: Multi-platform builds can only be loaded for single platform"
    echo "        Building for current platform only when using --load"
    # Override platforms to current platform only when loading
    CURRENT_PLATFORM=$(docker version --format '{{.Server.Os}}/{{.Server.Arch}}')
    BUILD_ARGS=("${BUILD_ARGS[@]/--platform ${PLATFORMS}/--platform ${CURRENT_PLATFORM}}")
fi

# Add metadata
BUILD_ARGS+=(
    "--label" "org.opencontainers.image.created=$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
    "--label" "org.opencontainers.image.version=${VERSION}"
    "--label" "org.opencontainers.image.revision=$(git rev-parse --short HEAD 2>/dev/null || echo 'unknown')"
)

echo ""
echo "Building with command:"
echo "docker buildx build ${BUILD_ARGS[*]} ."
echo ""

# Build the image
if ! docker buildx build "${BUILD_ARGS[@]}" .; then
    echo "Error: Docker build failed"
    exit 1
fi

echo ""
echo "✓ Build complete!"
echo ""

if [ "${PUSH_FLAG}" != "push" ]; then
    echo "To push the multi-architecture image, run:"
    echo ""
    echo "  ./docker-build.sh ${VERSION} ${REGISTRY} ${USERNAME} \"${PLATFORMS}\" push"
    echo ""
    
    if [ "${REGISTRY}" = "dockerhub" ] || [ "${REGISTRY}" = "docker.io" ]; then
        echo "Make sure you're logged in first:"
        echo "  docker login"
    else
        echo "Make sure you're logged in first:"
        echo "  echo \$GITHUB_TOKEN | docker login ghcr.io -u ${USERNAME} --password-stdin"
        echo ""
        echo "Note: Create a GitHub Personal Access Token with 'write:packages' permission"
        echo "      at https://github.com/settings/tokens"
    fi
else
    echo "✓ Image pushed successfully to ${REGISTRY_URL}"
    echo ""
    echo "Verify the multi-architecture manifest:"
    echo "  docker buildx imagetools inspect ${FULL_IMAGE_NAME}"
fi

echo ""
echo "=========================================="
echo "Multi-Architecture Build Summary"
echo "=========================================="
echo "Image: ${FULL_IMAGE_NAME}"
echo "Platforms: ${PLATFORMS}"
echo "Status: $([ "${PUSH_FLAG}" = "push" ] && echo "Built and Pushed" || echo "Built Locally")"
echo "=========================================="
