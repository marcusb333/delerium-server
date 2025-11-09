# Quick Start: Publishing Container Images

## ✅ Setup Complete!

All build tools and workflows are ready. Here's what to do next:

## Option 1: Publish to GitHub Container Registry (GHCR) - Recommended

**No additional setup needed!** The GitHub Actions workflow will automatically publish to GHCR when you push to `main` or create a version tag.

### Steps:
1. **Push to main branch** (publishes `latest` tag):
   ```bash
   git push origin main
   ```
   Image will be available at: `ghcr.io/YOUR_USERNAME/delerium-paste-server:latest`

2. **Create a versioned release**:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
   Images will be available at:
   - `ghcr.io/YOUR_USERNAME/delerium-paste-server:1.0.0`
   - `ghcr.io/YOUR_USERNAME/delerium-paste-server:1.0`
   - `ghcr.io/YOUR_USERNAME/delerium-paste-server:1`

## Option 2: Publish to Docker Hub

### Setup (one-time):
1. Go to https://hub.docker.com/settings/security
2. Create an access token
3. In your GitHub repo: Settings → Secrets and variables → Actions
4. Add secrets:
   - `DOCKERHUB_USERNAME`: Your Docker Hub username
   - `DOCKERHUB_TOKEN`: Your access token

### Then:
- Push to `main` or create tags (same as GHCR above)
- Images will be published to both GHCR and Docker Hub

## Option 3: Manual Publishing

### For Docker Hub:
```bash
./docker-build.sh 1.0.0 dockerhub YOUR_USERNAME
docker login
docker push YOUR_USERNAME/delerium-paste-server:1.0.0
docker push YOUR_USERNAME/delerium-paste-server:latest
```

### For GHCR:
```bash
./docker-build.sh 1.0.0 ghcr YOUR_USERNAME
echo $GITHUB_TOKEN | docker login ghcr.io -u YOUR_USERNAME --password-stdin
docker push ghcr.io/YOUR_USERNAME/delerium-paste-server:1.0.0
docker push ghcr.io/YOUR_USERNAME/delerium-paste-server:latest
```

## Using the Published Image

```bash
docker run -d \
  -p 8080:8080 \
  -v $(pwd)/data:/data \
  -e DELETION_TOKEN_PEPPER=your-secret-pepper \
  ghcr.io/YOUR_USERNAME/delerium-paste-server:latest
```

## Next Steps

1. **Test locally first**: The image has been built and tested successfully
2. **Choose your registry**: GHCR (automatic) or Docker Hub (requires secrets)
3. **Push to trigger**: Push to `main` or create a version tag
4. **Verify**: Check your registry to confirm the image was published

For detailed information, see [CONTAINER_PUBLISHING.md](./CONTAINER_PUBLISHING.md).
