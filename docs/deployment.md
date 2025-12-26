# Deployment Guide

This guide covers deployment strategies, containerization principles, and production best practices for the DataOps Platform.

---

## Deployment Philosophy

### Container-First Architecture

1. **12-Factor App Methodology**: Configuration via environment, stateless processes, explicit dependencies
2. **Immutable Infrastructure**: Containers are built once and deployed consistently across environments
3. **BuildKit Optimization**: Aggressive caching for fast rebuild cycles during development
4. **Production-Grade Reliability**: Health checks, resource limits, graceful shutdowns

### Deployment Modes

- **Infrastructure Mode**: Run infrastructure services in Docker, applications locally (development)
- **Full Stack Mode**: Complete containerized environment (testing/production)
- **Kubernetes**: Production orchestration with scaling, service discovery, and rolling updates

---

## Quick Start Deployment

### Prerequisites

- Docker Desktop 20.10+ with BuildKit support
- Docker Compose v2.0+

### Development Deployment

```bash
# Infrastructure only (recommended for development)
make dev

# Full stack deployment
make dev-all

# Verify deployment
make health
```

---

## Docker Architecture

### Port Configuration Strategy

The platform uses a sophisticated port management strategy to avoid conflicts:

| Service | Local Dev Port | Docker Port | Purpose |
|---------|---------------|-------------|---------|
| basecamp-server | 8080 | 8081 | API server (avoids Keycloak conflict) |
| basecamp-parser | 5000 | 5000 | SQL parsing microservice |
| basecamp-ui | 3000 | 3000 | Web dashboard |
| Keycloak | 8080 | 8080 | Identity provider |
| MySQL | 3306 | 3306 | Primary database |
| Redis | 6379 | 6379 | Cache and sessions |

**Design Rationale:**
- Keycloak requires port 8080
- basecamp-server uses port 8081 in Docker to avoid conflict
- Local development uses port 8080 for basecamp-server (no Keycloak conflict)

### BuildKit Performance Optimization

**Cache Mount Strategy:**

```dockerfile
# Gradle cache mount (Spring Boot)
RUN --mount=type=cache,target=/workspace/.gradle \
    ./gradlew bootJar --no-daemon

# UV cache mount (Python)
RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --frozen

# NPM cache mount (React)
RUN --mount=type=cache,target=/root/.cache/npm \
    npm ci
```

**Spring Boot Layer Optimization:**
1. **Metadata** (least frequently changed)
2. **Loader** (Spring Boot classes)
3. **Dependencies** (external libraries)
4. **Classes** (application code - most frequently changed)

**Performance Impact:**
- First build: 3-5 minutes (cold cache)
- Rebuild: 30-60% faster (warm cache)
- No-change rebuild: 85-95% faster (hot cache)

---

## Production Deployment

### Environment Configuration

**Architecture-Driven Environment Variables:**

```bash
# Spring Boot Application
SPRING_PROFILES_ACTIVE=prod
SPRING_JPA_HIBERNATE_DDL_AUTO=validate

# Hexagonal Architecture Database Connection
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/dataops
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=20

# Infrastructure Services
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379

# Security (OAuth2 Integration)
SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://keycloak:8080/realms/dataops-realm

# Monitoring (Production Observability)
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics,prometheus
```

### Resource Management

**Production Resource Allocation:**

```yaml
# Production scaling configuration
services:
  basecamp-server:
    deploy:
      replicas: 2
      resources:
        limits:
          cpus: '2'
          memory: 4G
        reservations:
          cpus: '1'
          memory: 2G
      restart_policy:
        condition: on-failure
        max_attempts: 3
```

### Health Monitoring

**Comprehensive Health Checks:**

```yaml
# Application health verification
healthcheck:
  test: ["CMD", "curl", "-f", "http://localhost:8080/api/health"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 60s
```

---

## Container Orchestration

### Kubernetes Principles

**Cloud-Native Deployment Strategy:**

1. **Namespace Isolation**: Dedicated namespace for platform components
2. **ConfigMaps**: Environment-specific configuration management
3. **Secrets**: Secure credential storage (database passwords, API keys)
4. **Persistent Volumes**: Stateful data persistence (MySQL, Redis)
5. **Services**: Internal communication and load balancing
6. **Ingress**: External traffic routing with TLS termination

**Resource Management:**

```yaml
# Production resource configuration
resources:
  requests:
    cpu: "1000m"
    memory: "2Gi"
  limits:
    cpu: "2000m"
    memory: "4Gi"
```

### Service Discovery

**Internal Communication Pattern:**

- Services communicate via Kubernetes DNS
- MySQL: `mysql:3306`
- Redis: `redis:6379`
- basecamp-server: `basecamp-server:8080`

---

## CI/CD Architecture

### Pipeline Strategy

**Multi-Service Build Pipeline:**

```bash
# Parallel builds for efficiency
make build         # Build all services
make ci-build      # CI simulation (no cache)
make rebuild       # Clean rebuild
```

**Deployment Workflow:**

1. **Code Commit**: Feature branches and pull requests
2. **Automated Testing**: Unit, integration, and end-to-end tests
3. **Image Building**: Multi-stage Docker builds with BuildKit
4. **Security Scanning**: Container vulnerability assessment
5. **Staging Deployment**: Full stack testing environment
6. **Production Deployment**: Rolling updates with health checks

### Container Registry Strategy

**Image Lifecycle Management:**

```bash
# Tag strategy for production deployment
basecamp-server:latest          # Latest stable build
basecamp-server:v1.2.3         # Semantic versioning
basecamp-server:main-abc123     # Git commit-based tags
basecamp-server:staging         # Pre-production builds
```

---

## Observability & Monitoring

### Application Monitoring

**Prometheus Metrics Integration:**

- JVM performance metrics (memory, threads, GC)
- HTTP request metrics (rate, duration, errors)
- Database connection pool monitoring
- Cache hit rate tracking
- Custom business metrics (pipeline executions, failures)

**Health Check Endpoints:**

```bash
# Spring Boot Actuator endpoints
/actuator/health         # Overall application health
/actuator/metrics        # Application metrics
/actuator/prometheus     # Prometheus-format metrics
/actuator/info           # Application information
```

### Infrastructure Monitoring

**Container Orchestration Metrics:**

- Resource utilization (CPU, memory, disk)
- Container health and restart counts
- Service discovery and network connectivity
- Persistent volume usage
- Ingress traffic patterns

---

## Security & Production Readiness

### Security Architecture

**Multi-Layer Security Strategy:**

1. **Authentication**: OAuth2/OpenID Connect via Keycloak
2. **Authorization**: Role-based access control (RBAC)
3. **Network Security**: TLS/HTTPS for all endpoints
4. **Container Security**: Non-root user execution, minimal base images
5. **Secret Management**: Kubernetes secrets or external secret stores
6. **Audit Logging**: Comprehensive access and change tracking

### Production Checklist

**Deployment Readiness:**

- [ ] **Environment Configuration**: All environment variables properly configured
- [ ] **Secret Management**: Secure credential storage implemented
- [ ] **TLS/HTTPS**: End-to-end encryption enabled
- [ ] **Resource Limits**: Appropriate CPU/memory limits configured
- [ ] **Health Checks**: Comprehensive health monitoring in place
- [ ] **Backup Strategy**: Database and volume backup procedures established
- [ ] **Monitoring**: Prometheus metrics and Grafana dashboards configured
- [ ] **Logging**: Centralized log aggregation implemented
- [ ] **Security Scanning**: Regular vulnerability assessments scheduled

---

## Backup & Disaster Recovery

### Data Persistence Strategy

**Stateful Service Backup:**

```bash
# Database backup strategy
kubectl exec mysql-pod -- mysqldump -u root -p dataops > backup-$(date +%Y%m%d).sql

# Volume snapshot strategy
kubectl create volumesnapshot mysql-snapshot --volume-snapshot-class=csi-snapshotter
```

### Recovery Procedures

**Disaster Recovery Planning:**

1. **RTO (Recovery Time Objective)**: Target system restoration time
2. **RPO (Recovery Point Objective)**: Maximum acceptable data loss
3. **Automated Failover**: Health check-driven service restart
4. **Cross-Region Replication**: Geographic disaster resilience

---

## Essential Commands

### Deployment Operations

```bash
# Environment management
make setup              # Initialize deployment environment
make dev                # Development infrastructure deployment
make dev-all           # Full stack deployment
make health            # Verify service health
make logs              # Monitor application logs

# Production operations
make build             # Build production images
make ci-build          # Continuous integration build
make clean             # Clean deployment artifacts
make backup            # Backup persistent data
```

### Service Control

```bash
# Individual service management
make up-server         # Deploy basecamp-server only
make up-parser         # Deploy basecamp-parser only
make up-ui             # Deploy basecamp-ui only

# Scaling operations
docker compose up --scale basecamp-server=3  # Horizontal scaling
```

---

## References

- **[Architecture Documentation](./architecture.md)** - System design and hexagonal architecture
- **[Development Guide](./development.md)** - Local development and architecture patterns
- **[Troubleshooting](./troubleshooting.md)** - Common deployment issues

---

**Production deployment success depends on understanding the architecture. Always consider hexagonal architecture principles, service boundaries, and infrastructure dependencies when deploying the platform.**