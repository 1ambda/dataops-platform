# Deployment Guide

Production deployment strategies and Docker orchestration for DataOps Platform.

## Quick Deployment

**Development**: `make dev` (infrastructure) or `make dev-all` (full stack)
**Production**: Use Docker Compose with environment-specific configs
**Requirements**: Docker 20.10+ with BuildKit, Docker Compose v2.0+

## Architecture

**Ports**: Server 8080→8081 (Docker), UI 3000, Parser 5000, Connect 5001, Keycloak 8080
**BuildKit**: 30-60% faster rebuilds with cache mounts (Gradle, uv, npm)

## Production Configuration

**Environment Variables**: Use `.env` files for each environment
**Database**: External MySQL/PostgreSQL recommended for production
**Security**: Update default credentials, enable HTTPS, firewall configuration
**Monitoring**: Health checks on `/health` endpoints, log aggregation

## Docker Compose

**Infrastructure Only** (`docker-compose.yaml`): MySQL, Redis, PostgreSQL, Keycloak
**Full Stack** (`docker-compose.all.yaml`): All services containerized
**Environment Files**: Create `.env.prod`, `.env.staging` with specific configurations

## Kubernetes (Optional)

**Helm Charts**: Available for production orchestration
**Scaling**: Horizontal pod autoscaling for basecamp-server and parser
**Secrets**: Use Kubernetes secrets for database credentials
**Ingress**: Configure for external access and SSL termination

## Security Checklist

- Change default passwords (admin/admin for Keycloak)
- Use strong database credentials
- Enable HTTPS/TLS for all external communication
- Configure firewall rules for required ports only
- Regular security updates for base images
- Backup strategy for persistent volumes

