# Multi-architecture Dockerfile for delerium-paste-server
# Supports: linux/amd64, linux/arm64, linux/arm/v7
# Build with: docker buildx build --platform linux/amd64,linux/arm64,linux/arm/v7 -t image:tag .

# ---- builder ----
FROM --platform=$BUILDPLATFORM gradle:8.11.1-jdk21 AS builder

# Build arguments for cross-compilation
ARG TARGETPLATFORM
ARG BUILDPLATFORM
ARG TARGETOS
ARG TARGETARCH
ARG TARGETVARIANT

WORKDIR /build

# Copy build files
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src

# Build the application
# Gradle will automatically use the correct JVM for the target platform
RUN gradle --no-daemon clean installDist

# ---- runner ----
FROM eclipse-temurin:21-jre-jammy

# Metadata
LABEL org.opencontainers.image.title="Delirium Paste Server"
LABEL org.opencontainers.image.description="Zero-knowledge encrypted paste service backend"
LABEL org.opencontainers.image.source="https://github.com/marcusb333/delerium-server"
LABEL org.opencontainers.image.licenses="MIT"

WORKDIR /app

# Copy application from builder
COPY --from=builder /build/build/install/delerium-server/ /app/

# Create non-root user for security
RUN groupadd -r delirium && useradd -r -g delirium delirium && \
    chown -R delirium:delirium /app && \
    mkdir -p /data && \
    chown -R delirium:delirium /data

# Switch to non-root user
USER delirium

# Volume for persistent data
VOLUME ["/data"]

# Expose application port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/api/pow || exit 1

# Run the application
ENTRYPOINT ["/app/bin/delerium-server"]
