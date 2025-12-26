# syntax=docker/dockerfile:1
# Multi-stage Dockerfile for Python Flask microservice with BuildKit optimizations
#
# Build: docker build -t basecamp-parser -f project-basecamp-parser/Dockerfile .
# Run:   docker run -p 5000:5000 basecamp-parser

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
    && rm -rf /var/lib/apt/lists/*

# Install uv with BuildKit cache
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-cache-dir uv

# Copy dependency files first for better caching
COPY project-basecamp-parser/pyproject.toml project-basecamp-parser/uv.lock project-basecamp-parser/README.md ./

# Create virtual environment and install dependencies with BuildKit cache
RUN --mount=type=cache,target=/root/.cache/uv \
    uv venv /opt/venv && \
    . /opt/venv/bin/activate && \
    uv sync --no-editable --no-dev

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
FROM python:${PYTHON_VERSION}-slim

# Re-declare ARGs needed in this stage
ARG APP_VERSION
ARG BUILD_DATE
ARG VCS_REF

# OCI Image Labels (https://github.com/opencontainers/image-spec/blob/main/annotations.md)
LABEL org.opencontainers.image.title="DataOps Platform - Basecamp Parser" \
      org.opencontainers.image.description="SQL parsing microservice using SQLglot" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.vendor="DataOps Platform" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.source="https://github.com/1ambda/dataops-platform" \
      org.opencontainers.image.base.name="python:3.12-slim"

# Copy tini for proper PID 1 process management
COPY --from=tini /tini /tini

# Install curl for health checks (lighter than Python imports)
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/* \
    # Create non-root user with no login shell for security
    && groupadd -g 1000 appuser \
    && useradd -u 1000 -g appuser -d /app -s /sbin/nologin appuser

WORKDIR /app

# Copy virtual environment from builder - dependencies change less frequently
COPY --from=builder --chown=appuser:appuser /opt/venv /opt/venv

# Copy application code - more likely to change during development
COPY --chown=appuser:appuser project-basecamp-parser/ .

# Remove unnecessary files to reduce image size
RUN rm -rf tests/ .pytest_cache/ __pycache__/ *.md .git* .ruff* 2>/dev/null || true

# Set environment variables for Python optimization
ENV PATH="/opt/venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONFAULTHANDLER=1 \
    PYTHONHASHSEED=random \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    # Flask configuration (FLASK_ENV deprecated in Flask 2.3+)
    FLASK_APP=app.py \
    FLASK_DEBUG=0

EXPOSE 5000

# Health check using curl (more reliable than Python import)
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl --fail --silent --show-error http://localhost:5000/health || exit 1

# Switch to non-root user
USER appuser

# Use SIGTERM for graceful shutdown
STOPSIGNAL SIGTERM

# Use tini as PID 1 for proper signal handling and zombie process cleanup
ENTRYPOINT ["/tini", "--"]

# Run Flask with Werkzeug (development server - for production use gunicorn)
CMD ["flask", "run", "--host=0.0.0.0", "--port=5000"]
