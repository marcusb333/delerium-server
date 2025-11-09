# Container Image Publishing Guide

This guide explains how to publish the delerium-paste server as a reusable container image to Docker Hub or GitHub Container Registry (GHCR).

## Overview

The project includes:
- **Dockerfile**: Multi-stage build for optimized image size
- **docker-build.sh**: Manual build script for local testing
- **GitHub Actions workflow**: Automated CI/CD for publishing images

## Prerequisites

1. **Docker** installed and running
2. **Docker Hub account** (for Docker Hub publishing) OR
3. **GitHub account** (for GHCR publishing)
4. **GitHub repository** (for automated publishing via Actions)

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
   - Add the following secrets:
     - `DOCKERHUB_USERNAME`: Your Docker Hub username
     - `DOCKERHUB_TOKEN`: Your Docker Hub access token (create at https://hub.docker.com/settings/security)

2. **Trigger the workflow:**
   - Push to `main` branch → builds and pushes `latest` tag
   - Create a git tag (e.g., `v1.0.0`) → builds and pushes versioned tags:
     ```bash
     git tag v1.0.0
     git push origin v1.0.0
     ```

3. **Image tags created:**
   - `your-username/delerium-paste-server:latest` (on main branch)
   - `your-username/delerium-paste-server:1.0.0` (on version tag)
   - `your-username/delerium-paste-server:1.0` (major.minor)
   - `your-username/delerium-paste-server:1` (major)

## Publishing to GitHub Container Registry (GHCR)

### Option 1: Manual Publishing

1. **Build the image:**
   ```bash
   docker build -t ghcr.io/your-username/delerium-paste-server:1.0.0 .
   ```

2. **Login to GHCR:**
   ```bash
   echo $GITHUB_TOKEN | docker login ghcr.io -u your-username --password-stdin
   ```
   (Create a Personal Access Token with `write:packages` permission)

3. **Push the image:**
   ```bash
   docker push ghcr.io/your-username/delerium-paste-server:1.0.0
   ```

### Option 2: Automated Publishing via GitHub Actions

The workflow can be configured to publish to GHCR instead of (or in addition to) Docker Hub. See the workflow file for configuration options.

## Using the Published Image

### Basic Usage

```bash
docker run -d \
  -p 8080:8080 \
  -v /path/to/data:/data \
  -e DELETION_TOKEN_PEPPER=your-secret-pepper \
  your-username/delerium-paste-server:latest
```

### Environment Variables

- `DELETION_TOKEN_PEPPER` (required): Secret pepper for hashing deletion tokens
  - Default: `change-me` (change this in production!)

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

1. **Change the default pepper**: Always set `DELETION_TOKEN_PEPPER` to a strong, random value
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

The GitHub Actions workflow (`.github/workflows/docker-publish.yml` at the repository root) automatically:
- Builds images on pushes to `main` and version tags
- Creates multiple tags for semantic versioning
- Uses build cache for faster builds
- Only pushes on non-PR events (builds only on PRs)

To modify the workflow:
1. Edit `.github/workflows/docker-publish.yml`
2. Adjust triggers, registries, or tags as needed
3. Update secrets if switching registries
