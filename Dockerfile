# ---- builder ----
FROM gradle:8.10.2-jdk17 AS builder
WORKDIR /build
COPY build.gradle.kts settings.gradle.kts ./
COPY src ./src
RUN gradle --no-daemon clean installDist

# ---- runner ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=builder /build/build/install/delerium-paste-server/ /app/
VOLUME ["/data"]
EXPOSE 8080
ENTRYPOINT ["/app/bin/delerium-paste-server"]
