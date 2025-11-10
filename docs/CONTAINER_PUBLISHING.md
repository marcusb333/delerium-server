# Container Image Publishing Guide

This guide explains how to publish the delerium-paste-server as a reusable container image to Docker Hub or GitHub Container Registry (GHCR).

## Quick Start

**For GitHub Container Registry (GHCR) - Easiest Option:**
- ✅ **No setup required!** Just push to your GitHub repository
- Images are automatically published to `ghcr.io/<your-username>/delerium-paste-server`
- Works immediately - no secrets to configure

**For Docker Hub:**
- Requires setting up secrets in GitHub (see below)
- Images published to `docker.io/<your-username>/delerium-paste-server`

## Overview

The project includes:
- **Dockerfile**: Multi-stage build for optimized image size
- **docker-build.sh**: Manual build script for local testing
- **GitHub Actions workflow**: Automated CI/CD for publishing images

## Prerequisites

1. **Docker** installed and running (for local builds)
2. **GitHub repository** (for automated publishing via Actions)
3. **Docker Hub account** (optional, only if publishing to Docker Hub)

## Publishing to Docker Hub

### Option 1: Manual Publishing

1. **Build the image locally:**
   ```bash
   ./docker-build.sh 1.0.0 your-dockerhub-username
   ```

2. **Login to Docker Hub:**
   ```bash
   docker login
   ```

3. **Push the image:**
   ```bash
   docker push your-dockerhub-username/delerium-paste-server:1.0.0
   docker push your-dockerhub-username/delerium-paste-server:latest
   ```

### Option 2: Automated Publishing via GitHub Actions

1. **Set up Docker Hub secrets in GitHub:**
   - Go to your repository → Settings → Secrets and variables → Actions
   - Click "New repository secret"
   - Add the following secrets:
     - **Name:** `DOCKERHUB_USERNAME` → **Value:** Your Docker Hub username
     - **Name:** `DOCKERHUB_TOKEN` → **Value:** Your Docker Hub access token
       - Create token at: https://hub.docker.com/settings/security
       - Click "New Access Token"
       - Give it a description (e.g., "GitHub Actions")
       - Copy the token immediately (you won't see it again!)

2. **Trigger the workflow:**
   - **Push to `main` branch:**
     ```bash
     git push origin main
     ```
     Builds and pushes: `your-username/delerium-paste-server:latest`
   
   - **Create a version tag:**
     ```bash
     git tag v1.0.0
     git push origin v1.0.0
     ```
     Builds and pushes multiple tags:
     - `your-username/delerium-paste-server:1.0.0`
     - `your-username/delerium-paste-server:1.0`
     - `your-username/delerium-paste-server:1`
     - `your-username/delerium-paste-server:latest`

3. **Verify the workflow:**
   - Go to your repository → Actions tab
   - You should see "Build and Push Docker Image" workflow running
   - Once complete, check Docker Hub: https://hub.docker.com/r/your-username/delerium-paste-server

## Publishing to GitHub Container Registry (GHCR)

### Option 1: Automated Publishing via GitHub Actions (Recommended)

**GHCR publishing works automatically with zero configuration!**

1. **No setup required** - The workflow uses GitHub's built-in `GITHUB_TOKEN` which has permissions to push to GHCR
2. **Push to your repository:**
   ```bash
   git push origin main
   ```
   This automatically builds and pushes:
   - `ghcr.io/<your-username>/delerium-paste-server:latest`

3. **Create a version tag:**
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   This automatically creates multiple tags:
   - `ghcr.io/<your-username>/delerium-paste-server:1.0.0`
   - `ghcr.io/<your-username>/delerium-paste-server:1.0`
   - `ghcr.io/<your-username>/delerium-paste-server:1`
   - `ghcr.io/<your-username>/delerium-paste-server:latest`

4. **View your images:**
   - Go to your GitHub repository → Packages (right sidebar)
   - Or visit: `https://github.com/<your-username>?tab=packages`

**Note:** By default, packages are private. To make them public:
- Go to the package page → Package settings → Change visibility → Make public

### Option 2: Manual Publishing

1. **Create a GitHub Personal Access Token:**
   - Go to https://github.com/settings/tokens
   - Click "Generate new token (classic)"
   - Select scope: `write:packages`
   - Generate and copy the token

2. **Build the image using the script:**
   ```bash
   ./docker-build.sh 1.0.0 ghcr your-github-username
   ```

3. **Login to GHCR:**
   ```bash
   echo $GITHUB_TOKEN | docker login ghcr.io -u your-github-username --password-stdin
   ```
   (Replace `$GITHUB_TOKEN` with your actual token, or set it as an environment variable)

4. **Push the image:**
   ```bash
   docker push ghcr.io/your-github-username/delerium-paste-server:1.0.0
   docker push ghcr.io/your-github-username/delerium-paste-server:latest
   ```

## Using the Published Image

### Basic Usage

```bash
# With auto-generated pepper (works for development)
docker run -d \
  -p 8080:8080 \
  -v /path/to/data:/data \
  your-username/delerium-paste-server:latest

# With explicit pepper (recommended for production)
docker run -d \
  -p 8080:8080 \
  -v /path/to/data:/data \
  -e DELETION_TOKEN_PEPPER=your-secret-pepper \
  your-username/delerium-paste-server:latest
```

### Environment Variables

- `DELETION_TOKEN_PEPPER` (optional, but recommended for production): Secret pepper for hashing deletion tokens
  - **Auto-generation**: If not set, the application automatically generates a cryptographically secure random pepper (32 bytes = 64 hex characters)
  - **Production recommendation**: Set explicitly for consistency across container restarts
    - If the pepper changes between restarts, deletion tokens created before the restart will no longer work
    - Generate a secure random value: `openssl rand -hex 32`
  - **Development**: Auto-generation works fine for development/testing

### Volumes

- `/data`: Directory where the SQLite database will be stored
  - The database file will be created at `/data/pastes.db`

### Ports

- `8080`: HTTP server port (configurable via `application.conf`)

### Example with docker-compose

```yaml
version: '3.8'

services:
  delerium-paste:
    image: your-username/delerium-paste-server:latest
    ports:
      - "8080:8080"
    volumes:
      - ./data:/data
    environment:
      - DELETION_TOKEN_PEPPER=your-secret-pepper-here
    restart: unless-stopped
```

## Image Details

### Base Images
- **Builder**: `gradle:8.10.2-jdk17` (for building the application)
- **Runtime**: `eclipse-temurin:21-jre-jammy` (JRE only, smaller size)

### Image Size
The multi-stage build produces a minimal runtime image containing only:
- JRE 21
- Application binaries
- Application dependencies

### Security Considerations

1. **Pepper management**: 
   - **Auto-generation**: If `DELETION_TOKEN_PEPPER` is not set, the application automatically generates a cryptographically secure random pepper (32 bytes)
   - **Production best practice**: Set `DELETION_TOKEN_PEPPER` explicitly for consistency across restarts
     - If the pepper changes between restarts, deletion tokens created before restart will be invalid
     - Generate a secure value: `openssl rand -hex 32`
   - **Security**: The auto-generated pepper is cryptographically secure (uses `SecureRandom`)
2. **Volume permissions**: Ensure the `/data` volume has appropriate permissions
3. **Network security**: Consider using a reverse proxy (nginx, traefik) in front of the container
4. **Secrets management**: Use Docker secrets or environment variable management tools in production

## Troubleshooting

### Container won't start
- Check logs: `docker logs <container-id>`
- Verify database path is writable: `docker exec <container-id> ls -la /data`
- Ensure `DELETION_TOKEN_PEPPER` is set

### Database issues
- Ensure `/data` volume is mounted and writable
- Check file permissions on the host directory
- Verify SQLite is working: `docker exec <container-id> sqlite3 /data/pastes.db ".tables"`

### Build failures
- Ensure Docker has enough resources (memory, disk space)
- Check network connectivity for downloading dependencies
- Review build logs for specific errors

## Best Practices

1. **Versioning**: Use semantic versioning (e.g., `v1.0.0`) for releases
2. **Tagging**: Always tag releases, use `latest` for the current stable version
3. **Security**: Regularly update base images and dependencies
4. **Testing**: Test images locally before pushing to registries
5. **Documentation**: Keep this guide and README updated with usage examples

## CI/CD Workflow Details

The GitHub Actions workflow (`.github/workflows/docker-publish.yml`) automatically:
- **Builds images** on pushes to `main` and version tags (format: `v*`)
- **Creates multiple tags** for semantic versioning (e.g., `1.0.0`, `1.0`, `1`, `latest`)
- **Uses build cache** for faster builds (GitHub Actions cache)
- **Publishes to both registries** (if Docker Hub secrets are configured):
  - GHCR: Always enabled (uses `GITHUB_TOKEN`)
  - Docker Hub: Only if `DOCKERHUB_USERNAME` secret is set
- **Builds only** on pull requests (doesn't push)

### Workflow Triggers

- **Push to `main` branch:** Builds and pushes `latest` tag
- **Push version tag (`v*`):** Builds and pushes versioned tags
- **Pull requests:** Builds only (for testing), doesn't push

### Registry Behavior

- **GHCR:** Always publishes (no secrets needed)
- **Docker Hub:** Only publishes if `DOCKERHUB_USERNAME` secret exists

### Customizing the Workflow

To modify the workflow:
1. Edit `.github/workflows/docker-publish.yml`
2. Adjust triggers, registries, or tags as needed
3. Update secrets if switching registries
4. To disable a registry, comment out or remove the relevant steps

## Quick Reference

### Automated Publishing (Recommended)

**GHCR (Zero Setup):**
```bash
git push origin main                    # Pushes latest tag
git tag v1.0.0 && git push origin v1.0.0  # Pushes versioned tags
```

**Docker Hub (Requires Secrets):**
1. Add `DOCKERHUB_USERNAME` and `DOCKERHUB_TOKEN` secrets in GitHub
2. Same git commands as above

### Manual Publishing

**GHCR:**
```bash
./docker-build.sh 1.0.0 ghcr your-github-username
echo $GITHUB_TOKEN | docker login ghcr.io -u your-github-username --password-stdin
docker push ghcr.io/your-github-username/delerium-paste-server:1.0.0
```

**Docker Hub:**
```bash
./docker-build.sh 1.0.0 dockerhub your-dockerhub-username
docker login
docker push your-dockerhub-username/delerium-paste-server:1.0.0
```

### Pull and Run Published Images

**From GHCR:**
```bash
docker pull ghcr.io/your-username/delerium-paste-server:latest
docker run -d -p 8080:8080 -v ./data:/data -e DELETION_TOKEN_PEPPER=your-secret ghcr.io/your-username/delerium-paste-server:latest
```

**From Docker Hub:**
```bash
docker pull your-username/delerium-paste-server:latest
docker run -d -p 8080:8080 -v ./data:/data -e DELETION_TOKEN_PEPPER=your-secret your-username/delerium-paste-server:latest
```
