# syntax=docker/dockerfile:1
# Multi-stage Dockerfile for React + Vite application with BuildKit optimizations
#
# Build: docker build -t basecamp-ui -f project-basecamp-ui/Dockerfile .
# Run:   docker run -p 3000:3000 basecamp-ui

# =============================================================================
# Build Arguments for versioning and metadata
# =============================================================================
ARG APP_VERSION=2.2.0
ARG BUILD_DATE
ARG VCS_REF
ARG NODE_VERSION=22
ARG NGINX_VERSION=1.27
ARG TINI_VERSION=v0.19.0

# =============================================================================
# Stage 1: Build the application
# =============================================================================
FROM node:${NODE_VERSION}-alpine AS builder

WORKDIR /build

# Copy package files first for better caching
COPY project-basecamp-ui/package*.json ./

# Install dependencies with npm ci - uses BuildKit cache
RUN --mount=type=cache,target=/root/.npm \
    npm ci --prefer-offline --no-audit --no-fund

# Copy source code (excluding files in .dockerignore)
COPY project-basecamp-ui/ .

# Build the application with optimizations
ENV NODE_ENV=production
RUN npm run build

# Remove source maps if not needed in production (optional - uncomment to enable)
# RUN find dist -name "*.map" -type f -delete

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
# Stage 3: Runtime stage using Nginx
# =============================================================================
FROM nginx:${NGINX_VERSION}-alpine

# Re-declare ARGs needed in this stage
ARG APP_VERSION
ARG BUILD_DATE
ARG VCS_REF

# OCI Image Labels (https://github.com/opencontainers/image-spec/blob/main/annotations.md)
LABEL org.opencontainers.image.title="DataOps Platform - Basecamp UI" \
      org.opencontainers.image.description="React + Vite web dashboard for DataOps Platform" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.vendor="DataOps Platform" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.source="https://github.com/1ambda/dataops-platform" \
      org.opencontainers.image.base.name="nginx:1.27-alpine"

# Copy tini for proper PID 1 process management
COPY --from=tini /tini /tini

# Create non-root user for nginx
RUN addgroup -g 1000 appuser && \
    adduser -D -u 1000 -G appuser -s /sbin/nologin appuser && \
    # Create required directories with proper ownership
    mkdir -p /var/cache/nginx/client_temp \
             /var/cache/nginx/proxy_temp \
             /var/cache/nginx/fastcgi_temp \
             /var/cache/nginx/uwsgi_temp \
             /var/cache/nginx/scgi_temp \
             /var/run/nginx \
             /var/log/nginx && \
    touch /var/run/nginx.pid && \
    chown -R appuser:appuser /var/cache/nginx \
                              /var/log/nginx \
                              /var/run/nginx \
                              /var/run/nginx.pid && \
    # Remove default nginx configs
    rm -rf /etc/nginx/conf.d/* /usr/share/nginx/html/*

# Copy custom nginx configuration
COPY project-basecamp-ui/nginx.conf /etc/nginx/nginx.conf
COPY project-basecamp-ui/nginx/default.conf /etc/nginx/conf.d/default.conf

# Copy built application from builder
COPY --from=builder --chown=appuser:appuser /build/dist /usr/share/nginx/html

# Set proper permissions for nginx runtime (read-only for static files)
RUN chmod -R 755 /usr/share/nginx/html && \
    find /usr/share/nginx/html -type f -exec chmod 644 {} \;

EXPOSE 3000

# Health check with proper start period for nginx startup
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:3000/ || exit 1

# Switch to non-root user
USER appuser

# Use SIGQUIT for graceful shutdown (nginx recommended signal)
STOPSIGNAL SIGQUIT

# Use tini as PID 1 for proper signal handling and graceful shutdown
ENTRYPOINT ["/tini", "--"]
CMD ["nginx", "-g", "daemon off;"]
