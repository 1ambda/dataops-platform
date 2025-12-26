# syntax=docker/dockerfile:1
# Multi-stage Dockerfile for Python CLI application with BuildKit optimizations
#
# Build: docker build -t interface-cli -f project-interface-cli/Dockerfile .
# Run:   docker run --rm interface-cli --help

# =============================================================================
# Build Arguments for versioning and metadata
# =============================================================================
ARG APP_VERSION=0.1.0
ARG BUILD_DATE
ARG VCS_REF
ARG PYTHON_VERSION=3.12
ARG TINI_VERSION=v0.19.0

# =============================================================================
# Stage 1: Build the application
# =============================================================================
FROM python:${PYTHON_VERSION}-slim AS builder

WORKDIR /build

# Install build dependencies with cache
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libffi-dev \
    binutils \
    && rm -rf /var/lib/apt/lists/*

# Install uv with BuildKit cache
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-cache-dir uv

# Copy project files (use correct lock file name)
COPY project-interface-cli/pyproject.toml project-interface-cli/uv.lock* project-interface-cli/README.md ./

# Create virtual environment and install dependencies with BuildKit cache
RUN --mount=type=cache,target=/root/.cache/uv \
    uv venv /opt/venv && \
    . /opt/venv/bin/activate && \
    uv sync --no-editable

# Copy source code and build files
COPY project-interface-cli/ .

# Install PyInstaller and build standalone binary
RUN --mount=type=cache,target=/root/.cache/pip \
    . /opt/venv/bin/activate && \
    pip install pyinstaller && \
    pyinstaller dli.spec --noconfirm && \
    # Verify binary was created
    test -f dist/dli && \
    # Strip debug symbols to reduce size
    strip dist/dli || true

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
# Stage 3: Runtime stage (minimal distroless-like image)
# =============================================================================
FROM debian:bookworm-slim

# Re-declare ARGs needed in this stage
ARG APP_VERSION
ARG BUILD_DATE
ARG VCS_REF

# OCI Image Labels (https://github.com/opencontainers/image-spec/blob/main/annotations.md)
LABEL org.opencontainers.image.title="DataOps Platform - Interface CLI" \
      org.opencontainers.image.description="Command-line interface for DataOps Platform operations" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.vendor="DataOps Platform" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.source="https://github.com/1ambda/dataops-platform" \
      org.opencontainers.image.base.name="debian:bookworm-slim"

# Copy tini for proper PID 1 process management
COPY --from=tini /tini /tini

# Install minimal runtime dependencies and create non-root user
RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    # Create non-root user with no login shell for security
    && groupadd -g 1000 appuser \
    && useradd -u 1000 -g appuser -d /home/appuser -m -s /sbin/nologin appuser \
    # Create configuration directory
    && mkdir -p /home/appuser/.dli \
    && chown -R appuser:appuser /home/appuser

WORKDIR /app

# Copy built binary from builder
COPY --from=builder --chown=appuser:appuser /build/dist/dli /usr/local/bin/dli

# Set executable permissions (should already be set, but ensure it)
RUN chmod 755 /usr/local/bin/dli

# Environment configuration
ENV HOME=/home/appuser \
    PATH="/usr/local/bin:$PATH" \
    # Disable Python-related warnings (binary is self-contained)
    PYTHONDONTWRITEBYTECODE=1

# Switch to non-root user
USER appuser

# Use SIGTERM for graceful shutdown
STOPSIGNAL SIGTERM

# Use tini as PID 1 for proper signal handling
ENTRYPOINT ["/tini", "--", "dli"]
CMD ["--help"]
