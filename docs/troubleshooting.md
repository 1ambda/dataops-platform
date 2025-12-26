# Troubleshooting Guide

This guide helps you diagnose and resolve common issues when working with the DataOps Platform.

## Table of Contents

- [Quick Diagnostics](#quick-diagnostics)
- [Common Issues](#common-issues)
- [Service-Specific Issues](#service-specific-issues)
- [Health Checks](#health-checks)
- [Debug Mode](#debug-mode)
- [Reset Procedures](#reset-procedures)

---

## Quick Diagnostics

### First Steps

```bash
# 1. Check container status
make status

# 2. Check service health
make health

# 3. Check resource usage
make stats

# 4. View recent logs
make logs
```

### Quick Fixes

```bash
# Restart all services
make restart

# Rebuild all images (no cache)
make rebuild

# Nuclear option: complete reset
make clean-all
make setup
make dev
```

---

## Common Issues

### 1. Services Fail to Start

**Symptom:** Containers exit immediately or fail health checks

**Diagnosis:**
```bash
# Check logs for errors
make logs

# Check specific service
make logs-server
make logs-parser
make logs-ui

# Check container status
docker ps -a | grep dataops
```

**Solutions:**

**Port Conflicts:**
```bash
# Find process using port
lsof -i :8080  # Keycloak
lsof -i :8081  # Server (full stack mode)
lsof -i :3000  # UI
lsof -i :3306  # MySQL
lsof -i :5000  # Parser
lsof -i :5432  # PostgreSQL
lsof -i :6379  # Redis

# Kill process or change port in docker-compose files
kill -9 <PID>

# Then restart
make restart
```

**Insufficient Resources:**
```bash
# Check Docker Desktop resources
# Recommended: 4+ CPU cores, 8GB+ RAM

# On macOS/Linux, check available resources
docker info | grep -E 'CPUs|Total Memory'

# Increase Docker Desktop resource limits in Settings
```

**Image Build Failures:**
```bash
# Clean and rebuild
make clean-images
make build

# Build with verbose output
docker compose -f docker-compose.all.yaml build --progress=plain
```

---

### 2. Database Connection Errors

**Symptom:** Server cannot connect to MySQL or Keycloak cannot connect to PostgreSQL

**Diagnosis:**
```bash
# Check database health
make health

# View database logs
make logs-mysql
make logs-postgres

# Check database is running
docker ps | grep -E 'mysql|postgres'

# Try manual connection
docker exec -it dataops-mysql mysql -u dataops_user -pdataops_password dataops
docker exec -it ops-postgres psql -U postgres
```

**Solutions:**

**MySQL Connection Issues:**
```bash
# Check MySQL is healthy
docker exec dataops-mysql mysqladmin ping -h localhost

# Restart MySQL
docker restart dataops-mysql

# Wait for MySQL to be ready (30-60 seconds)
sleep 30

# Verify basecamp-server can connect
make logs-server | grep -i mysql
```

**PostgreSQL Connection Issues:**
```bash
# Check PostgreSQL is ready
docker exec ops-postgres pg_isready -U postgres

# Restart PostgreSQL
docker restart ops-postgres

# Check Keycloak logs for connection errors
make logs-keycloak | grep -i postgres
```

**Reset Database (WARNING: Deletes all data):**
```bash
# MySQL
make db-reset

# Or manually
docker compose down
docker volume rm dataops-platform_mysql_data
docker compose up -d mysql
```

**Connection String Issues:**
```bash
# Verify connection string in basecamp-server
# Should be: jdbc:mysql://mysql:3306/dataops (Docker)
# Or: jdbc:mysql://localhost:3306/dataops (local)

# Check environment variables
docker exec basecamp-server env | grep -i mysql
```

---

### 3. Keycloak Issues

**Symptom:** Keycloak not starting or authentication failures

**Diagnosis:**
```bash
# Check Keycloak logs
make logs-keycloak

# Verify PostgreSQL is healthy (Keycloak dependency)
docker exec ops-postgres pg_isready -U postgres

# Check Keycloak health endpoint
curl http://localhost:8080/health

# Check if Keycloak is accessible
curl -I http://localhost:8080
```

**Solutions:**

**Keycloak Won't Start:**
```bash
# Keycloak takes 30-60 seconds to fully start
# Wait and check again
sleep 60
curl http://localhost:8080/health

# Check PostgreSQL is running first
docker ps | grep ops-postgres

# Restart in order: PostgreSQL → Keycloak
docker restart ops-postgres
sleep 10
docker restart keycloak
```

**Authentication Failures:**
```bash
# Access Keycloak admin console
open http://localhost:8080
# Login: admin/admin

# Check realm configuration
# Expected realm: dataops-realm

# Verify client configuration
# Check redirect URIs match your UI URL
```

**Realm Import Issues:**
```bash
# Check realm configuration file
ls -la ./_docker/keycloak/realm.json

# Verify realm import in Keycloak logs
make logs-keycloak | grep -i realm

# Manual realm import if needed:
# 1. Access admin console
# 2. Navigate to "Add realm"
# 3. Import ./_docker/keycloak/realm.json
```

**Port 8080 Conflict:**
```bash
# Keycloak uses port 8080
# This conflicts with basecamp-server in local mode

# Solution: Use full stack mode (server on port 8081)
make dev-all

# Or run server on different port locally
./gradlew bootRun --args='--server.port=8090'
```

---

### 4. Port Conflicts

**Symptom:** "port already allocated" error

**Common Port Conflicts:**

| Service | Port | Conflict Resolution |
|---------|------|---------------------|
| Keycloak | 8080 | Use `dev-all` mode (server → 8081) |
| basecamp-server | 8081 | Stop conflicting service |
| basecamp-ui | 3000 | Change port in vite.config.ts |
| MySQL | 3306 | Stop local MySQL instance |
| PostgreSQL | 5432 | Stop local PostgreSQL instance |
| basecamp-parser | 5000 | Change port in main.py |
| Redis | 6379 | Stop local Redis instance |

**Solutions:**

```bash
# Find and kill process using port
lsof -i :8080
kill -9 <PID>

# Or change port in docker-compose files
# Edit docker-compose.all.yaml:
services:
  basecamp-server:
    ports:
      - "8082:8080"  # Change external port
```

---

### 5. BuildKit Cache Issues

**Symptom:** Builds are slower than expected or caching not working

**Diagnosis:**
```bash
# Check BuildKit is enabled
echo $DOCKER_BUILDKIT
# Should output: 1

# Check cache status
make cache-info

# View Docker build cache
docker buildx du
```

**Solutions:**

**Enable BuildKit:**
```bash
# Temporary (current session)
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Permanent (add to ~/.bashrc or ~/.zshrc)
echo 'export DOCKER_BUILDKIT=1' >> ~/.bashrc
echo 'export COMPOSE_DOCKER_CLI_BUILD=1' >> ~/.bashrc
```

**Clear and Rebuild:**
```bash
# Clean build cache
docker builder prune -a

# Clean images and rebuild
make clean-images
make build

# Or complete rebuild from scratch
make rebuild
```

**Verify Cache is Working:**
```bash
# First build (should take 3-5 minutes)
time make build

# Second build (should be 30-60% faster)
time make build

# No-change rebuild (should be 85-95% faster)
time make build
```

---

### 6. Volume Permission Issues

**Symptom:** Permission denied errors in containers

**Diagnosis:**
```bash
# Check volume ownership
docker volume inspect dataops-platform_mysql_data

# Check container logs for permission errors
make logs-mysql | grep -i permission
make logs-redis | grep -i permission
```

**Solutions:**

```bash
# Clean volumes and restart
make clean-volumes
make dev

# This will recreate volumes with correct permissions
```

**On Linux (SELinux):**
```bash
# Add :z or :Z suffix to volume mounts
# Edit docker-compose.yaml:
volumes:
  - ./data:/data:z
```

---

## Service-Specific Issues

### basecamp-server

**Build Failures:**
```bash
# Clean Gradle cache
cd project-basecamp-server
./gradlew clean

# Delete .gradle directory
rm -rf ~/.gradle/caches

# Rebuild
./gradlew build --refresh-dependencies
```

**Application Won't Start:**
```bash
# Check Java version
java -version
# Should be 24+

# Check application logs
make logs-server

# Common issues:
# - Port already in use
# - Database connection failure
# - Redis connection failure
# - Missing environment variables
```

**Flyway Migration Failures:**
```bash
# Check migration scripts
ls -la project-basecamp-server/module-server-api/src/main/resources/db/migration/

# Reset database
make db-reset

# Restart server
make restart
```

---

### basecamp-parser

**Import Errors:**
```bash
# Ensure dependencies are installed
cd project-basecamp-parser
uv sync

# Activate virtual environment
source .venv/bin/activate

# Verify SQLglot is installed
python -c "import sqlglot; print(sqlglot.__version__)"
```

**Parser Errors:**
```bash
# Test SQL parsing manually
uv run python -c "
from src.parser.sql_parser import parse_sql
result = parse_sql('SELECT * FROM users')
print(result)
"

# Check logs for detailed error messages
make logs-parser
```

---

### basecamp-ui

**Build Errors:**
```bash
# Clear node_modules and reinstall
cd project-basecamp-ui
rm -rf node_modules
pnpm install

# Clear Vite cache
rm -rf node_modules/.vite

# Rebuild
pnpm run build
```

**API Connection Failures:**
```bash
# Check proxy configuration in vite.config.ts
# Should point to: http://localhost:8070

# Verify basecamp-server is running
curl http://localhost:8070/api/health

# Update proxy target if server is on different port
```

**TypeScript Errors:**
```bash
# Regenerate route tree
pnpm run dev
# Automatically generates routeTree.gen.ts

# Check TypeScript configuration
pnpm run type-check
```

---

### interface-cli

**Command Not Found:**
```bash
# Ensure CLI is installed
cd project-interface-cli
uv pip install -e .

# Verify installation
which dli
dli --version
```

**Connection Errors:**
```bash
# Check server URL configuration
dli config --show

# Update server URL
dli config --set-url http://localhost:8080

# Test connection
dli health --url http://localhost:8080
```

---

## Health Checks

### Manual Health Checks

**Infrastructure Services:**
```bash
# MySQL
docker exec dataops-mysql mysqladmin ping -h localhost
# Output: mysqld is alive

# Redis
docker exec dataops-redis redis-cli ping
# Output: PONG

# PostgreSQL
docker exec ops-postgres pg_isready -U postgres
# Output: accepting connections

# Keycloak
curl http://localhost:8080/health
# Output: {"status": "UP"}
```

**Application Services:**
```bash
# basecamp-server
curl http://localhost:8081/api/health
# Output: {"status": "healthy"}

# basecamp-parser
curl http://localhost:5000/health
# Output: {"status": "healthy", "service": "sql-parser"}

# basecamp-ui
curl http://localhost:3000/
# Output: HTML content
```

---

## Debug Mode

### Enable Debug Logging

**basecamp-server:**
```yaml
# application.yml
logging:
  level:
    root: DEBUG
    com.github.lambda: TRACE
```

**basecamp-parser:**
```bash
export PARSER_LOG_LEVEL=DEBUG
export PARSER_DEBUG=true
uv run python main.py
```

**basecamp-ui:**
```typescript
// vite.config.ts
server: {
  logLevel: 'debug'
}
```

### Debug Specific Components

```bash
# Database queries (basecamp-server)
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

# HTTP requests (basecamp-server)
logging:
  level:
    org.springframework.web: DEBUG

# Flask routes (basecamp-parser)
export FLASK_DEBUG=1
```

---

## Reset Procedures

### Soft Reset (Keep Data)

```bash
# Restart all services
make restart

# Rebuild images
make build
make restart
```

### Hard Reset (Delete Data)

```bash
# Stop and remove containers
make down

# Remove volumes (WARNING: Deletes all data)
make clean-volumes

# Remove images
make clean-images

# Start fresh
make setup
make dev
```

### Nuclear Reset (Complete Fresh Start)

```bash
# Complete cleanup
make clean-all

# Prune Docker system
make prune

# Remove any remaining resources
docker system prune -a --volumes

# Start from scratch
make setup
make dev
```

### Database-Only Reset

```bash
# MySQL
make db-reset

# Or manually
docker volume rm dataops-platform_mysql_data
docker compose up -d mysql
```

### Redis-Only Reset

```bash
# Flush all Redis data
make redis-flush

# Or manually
docker exec -it dataops-redis redis-cli FLUSHALL
```

---

## Logging Best Practices

### Accessing Logs

```bash
# Follow all logs
make logs

# Follow specific service
make logs-server

# View last 100 lines
docker logs --tail 100 basecamp-server

# View logs since timestamp
docker logs --since 2024-01-01T00:00:00 basecamp-server

# Save logs to file
docker logs basecamp-server > server.log 2>&1
```

### Log Levels

**Production:**
- ERROR: Critical issues requiring immediate attention
- WARN: Important issues that should be addressed
- INFO: Normal operational messages

**Development:**
- DEBUG: Detailed debug information
- TRACE: Very detailed trace information

---

## Getting Help

### Self-Service Resources

1. **Documentation:**
   - [Main README](../README.md)
   - [Architecture Guide](./architecture.md)
   - [Development Guide](./development.md)
   - [Deployment Guide](./deployment.md)

2. **Project-Specific README:**
   - [basecamp-server README](../project-basecamp-server/README.md)
   - [basecamp-parser README](../project-basecamp-parser/README.md)
   - [basecamp-ui README](../project-basecamp-ui/README.md)
   - [interface-cli README](../project-interface-cli/README.md)

3. **Test Cases:**
   - Review test files for usage examples
   - Check integration tests for API examples

### Reporting Issues

When reporting issues, include:

1. **Environment Information:**
   ```bash
   # Docker version
   docker --version
   docker compose version

   # OS information
   uname -a

   # Service status
   make status
   ```

2. **Error Logs:**
   ```bash
   # Capture relevant logs
   make logs > error-logs.txt
   ```

3. **Steps to Reproduce:**
   - Detailed steps leading to the issue
   - Expected vs actual behavior

4. **Configuration:**
   - Docker Compose files (if modified)
   - Environment variables
   - Custom configuration

---

## Advanced Debugging

### Network Debugging

```bash
# Check network connectivity between containers
docker exec basecamp-server ping mysql

# Inspect Docker network
docker network inspect dataops-platform_dataops-network

# Check DNS resolution
docker exec basecamp-server nslookup mysql
```

### Database Debugging

```bash
# Connect to MySQL
make db-shell

# View tables
SHOW TABLES;

# Check table structure
DESCRIBE pipelines;

# View recent data
SELECT * FROM pipelines ORDER BY created_at DESC LIMIT 10;
```

### Redis Debugging

```bash
# Connect to Redis
make redis-cli

# View all keys
KEYS *

# Get key value
GET some_key

# View database info
INFO
```

---

## Performance Issues

### Slow Builds

```bash
# Check BuildKit is enabled
echo $DOCKER_BUILDKIT

# Use cache mounts effectively
# Verify cache mount paths in Dockerfiles

# Parallel builds
docker compose -f docker-compose.all.yaml build --parallel
```

### High Resource Usage

```bash
# Monitor resource usage
make stats

# Check disk space
docker system df

# Clean up unused resources
make prune
```

---

**For additional support, check project-specific README files or consult the development team.**
