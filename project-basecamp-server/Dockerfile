# syntax=docker/dockerfile:1
# Multi-stage Dockerfile for Spring Boot application with BuildKit optimizations
#
# Build: docker build -t basecamp-server -f project-basecamp-server/Dockerfile .
# Run:   docker run -p 8080:8080 basecamp-server

# =============================================================================
# Build Arguments for versioning and metadata
# =============================================================================
ARG APP_VERSION=0.0.1-SNAPSHOT
ARG BUILD_DATE
ARG VCS_REF
ARG JAVA_VERSION=24
ARG TINI_VERSION=v0.19.0

# =============================================================================
# Stage 1: Build the application
# =============================================================================
FROM eclipse-temurin:${JAVA_VERSION}-jdk-alpine AS builder

WORKDIR /workspace

# Copy Gradle wrapper and configuration first for better layer caching
COPY project-basecamp-server/gradlew .
COPY project-basecamp-server/gradle gradle

# Ensure gradlew is executable
RUN chmod +x gradlew

# Copy build configuration files (changes less frequently than source)
COPY project-basecamp-server/build.gradle.kts project-basecamp-server/settings.gradle.kts ./

# Copy module build files separately for better caching
COPY project-basecamp-server/module-core-common/build.gradle.kts module-core-common/build.gradle.kts
COPY project-basecamp-server/module-core-domain/build.gradle.kts module-core-domain/build.gradle.kts
COPY project-basecamp-server/module-core-infra/build.gradle.kts module-core-infra/build.gradle.kts
COPY project-basecamp-server/module-server-api/build.gradle.kts module-server-api/build.gradle.kts

# Download dependencies (cached layer if build files unchanged)
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew dependencies --no-daemon || true

# Copy source code
COPY project-basecamp-server/module-core-common module-core-common
COPY project-basecamp-server/module-core-domain module-core-domain
COPY project-basecamp-server/module-core-infra module-core-infra
COPY project-basecamp-server/module-server-api module-server-api

# Build application with layered JAR support
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew clean build -x test --no-daemon

# Extract layered JAR for optimized Docker layers
RUN java -Djarmode=layertools -jar module-server-api/build/libs/*.jar extract --destination extracted

# =============================================================================
# Stage 2: Download tini (multi-architecture support)
# =============================================================================
FROM alpine:3.21 AS tini

ARG TINI_VERSION
ARG TARGETARCH

RUN apk add --no-cache wget ca-certificates && \
    TINI_ARCH=$(case ${TARGETARCH} in \
        amd64) echo "amd64" ;; \
        arm64) echo "arm64" ;; \
        *) echo "amd64" ;; \
    esac) && \
    wget -q -O /tini https://github.com/krallin/tini/releases/download/${TINI_VERSION}/tini-static-${TINI_ARCH} && \
    chmod +x /tini && \
    # Verify tini binary
    /tini --version

# =============================================================================
# Stage 3: Runtime stage
# =============================================================================
FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine

# Re-declare ARGs needed in this stage
ARG APP_VERSION
ARG BUILD_DATE
ARG VCS_REF

# OCI Image Labels (https://github.com/opencontainers/image-spec/blob/main/annotations.md)
LABEL org.opencontainers.image.title="DataOps Platform - Basecamp Server" \
      org.opencontainers.image.description="Spring Boot API server for DataOps Platform" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.vendor="DataOps Platform" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.source="https://github.com/1ambda/dataops-platform" \
      org.opencontainers.image.base.name="eclipse-temurin:24-jre-alpine"

# Copy tini for proper PID 1 signal handling
COPY --from=tini /tini /tini

# Create non-root user with explicit UID/GID for security
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser -h /app -s /sbin/nologin appuser

WORKDIR /app

# Copy layered JAR components (ordered by change frequency for optimal caching)
# Dependencies layer - changes least frequently
COPY --from=builder --chown=appuser:appuser /workspace/extracted/dependencies/ ./
# Spring Boot loader
COPY --from=builder --chown=appuser:appuser /workspace/extracted/spring-boot-loader/ ./
# Snapshot dependencies
COPY --from=builder --chown=appuser:appuser /workspace/extracted/snapshot-dependencies/ ./
# Application code - changes most frequently
COPY --from=builder --chown=appuser:appuser /workspace/extracted/application/ ./

# JVM configuration for containerized environments
ENV JAVA_TOOL_OPTIONS="-XX:+UseG1GC \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=25.0 \
    -XX:+ParallelRefProcEnabled \
    -XX:+UseContainerSupport \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/tmp/heapdump.hprof \
    -Djava.security.egd=file:/dev/./urandom"

ENV PORT=8080 \
    JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE=production

EXPOSE 8080

# Health check using wget (available in Alpine)
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

# Switch to non-root user
USER appuser

# Use SIGTERM for graceful shutdown (Spring Boot handles this)
STOPSIGNAL SIGTERM

# Use tini as PID 1 for proper signal handling and zombie process reaping
ENTRYPOINT ["/tini", "--"]

# Use Spring Boot layered JAR launcher
CMD ["java", "org.springframework.boot.loader.launch.JarLauncher"]