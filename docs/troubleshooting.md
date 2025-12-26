# Troubleshooting Guide

Quick solutions for common DataOps Platform issues.

**Quick Fix**: `make logs` → `make restart` → `make health`

## Common Issues

### Services Won't Start
**Check**: `make logs` → Look for port conflicts → `make restart`
**Port conflicts**: `lsof -i :3000` (UI), `:8080` (Keycloak), `:8081` (server)
**Reset**: `make clean && make setup && make dev`

### Database Connection Errors
**Check**: `make health` → Look for MySQL/PostgreSQL status
**Fix**: `make db-reset` (⚠️ deletes data) or restart database containers
**Logs**: Check for authentication errors or network issues

### Build Issues
**Docker cache**: `make clean-images && make rebuild`
**Dependencies**: Check internet connectivity, registry access
**Space**: `docker system prune` to free space

### Service Health Check Failures
**Timeouts**: Services may need more time to start (check logs)
**Configuration**: Verify environment variables in docker-compose files
**Resources**: Check CPU/memory usage with `make stats`

### Network/Connectivity Issues
**Internal**: Services can't reach each other (check Docker networks)
**External**: Can't reach external APIs (check firewall, proxy)
**DNS**: Docker DNS resolution issues (restart Docker Desktop)
---

## Service-Specific Issues

### basecamp-server (Spring Boot)
**Startup errors**: Check Java version (21+), port 8080/8081 availability
**Database connection**: Verify MySQL is running and accessible
**Common error**: `Failed to configure a DataSource` → Check database credentials

### basecamp-parser (Python)
**Import errors**: Check Python version (3.12+), `uv sync` dependencies
**SQL parsing**: Verify SQLglot version compatibility
**Common error**: `ModuleNotFoundError` → Run `uv install`

### basecamp-ui (React)
**Build failures**: Check Node.js version (22+), clear `node_modules`
**Runtime errors**: Check API connectivity to basecamp-server
**Common error**: `Network Error` → Verify backend is running

### basecamp-connect (Python)
**Integration failures**: Check external API credentials and connectivity
**Database errors**: Verify PostgreSQL is accessible for data persistence
**Common error**: `Connection timeout` → Check network/firewall settings

## Emergency Procedures

**Complete Reset**: `make clean-all && make setup && make dev-all`
**Nuclear Option**: Remove all containers, images, volumes, and start fresh
**Data Recovery**: Database volumes persist - only data loss if explicitly reset
**Support**: Check logs first, then create GitHub issue with error details

