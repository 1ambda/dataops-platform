# Health API Feature Specification

> **Version:** 0.1.0 | **Status:** Draft | **Priority:** P0 Critical
> **CLI Command:** `dli debug` | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Time:** Week 2.5 (0.5 weeks)

---

## 1. Overview

### 1.1 Purpose

The Health API provides system health status and diagnostics endpoints for:
- Load balancer health checks (basic health)
- CLI `dli debug` command diagnostics (extended health)
- Monitoring and alerting integration
- Component status verification (database, Redis, Airflow)

### 1.2 Scope

| Feature | Priority | CLI Integration |
|---------|----------|-----------------|
| Basic Health Check | P0 | `dli debug --server` |
| Extended Health Check | P0 | `dli debug` (full diagnostics) |
| Component Health Details | P0 | `dli debug --verbose` |
| System Resource Metrics | P1 | `dli debug --json` |

### 1.3 Current Implementation Status

| Endpoint | Status | Notes |
|----------|--------|-------|
| `GET /api/v1/health` | Partial | Exists but needs component health |
| `GET /api/v1/health/extended` | Not Implemented | New endpoint |
| `GET /api/v1/info` | Implemented | Build info available |

---

## 2. CLI Command Mapping

### 2.1 `dli debug` Command Options

| CLI Option | Server Endpoint | Data Source |
|------------|-----------------|-------------|
| `dli debug` | `GET /api/v1/health/extended` | Full diagnostics |
| `dli debug --server` | `GET /api/v1/health` | Basic health |
| `dli debug --connection` | N/A | Local database test |
| `dli debug --auth` | N/A | Local auth check |
| `dli debug --network` | N/A | Local network test |
| `dli debug --json` | Same + JSON format | JSON output |

### 2.2 CLI Check Categories

The CLI `dli debug` command checks these categories (from `CheckCategory` enum):

| Category | Description | Server Integration |
|----------|-------------|-------------------|
| `SYSTEM` | Python version, OS, dli version | Client-side only |
| `CONFIG` | Config file validation | Client-side only |
| `SERVER` | Basecamp Server connectivity | `GET /api/v1/health` |
| `AUTH` | Authentication validation | Client-side only |
| `DATABASE` | Database connectivity | `GET /api/v1/health/extended` |
| `NETWORK` | Network connectivity tests | Client-side only |

### 2.3 Expected Response Mapping

CLI expects these fields from server health endpoints:

```python
# CLI debug/models.py - CheckResult
class CheckResult:
    name: str           # "database", "redis", "airflow"
    category: CheckCategory
    status: CheckStatus  # PASS, FAIL, WARN, SKIP
    message: str         # Human-readable status
    details: dict | None # Additional metrics
    error: str | None    # Error message if failed
    remediation: str | None  # Fix suggestion
    duration_ms: int     # Response time
```

---

## 3. API Specifications

### 3.1 Basic Health Check

#### `GET /api/v1/health`

**Purpose**: Basic server health status for load balancers and monitoring systems.

**Authentication**: None required (public endpoint for load balancers)

**Request:**
```http
GET /api/v1/health
Accept: application/json
```

**Response (200 OK) - Healthy:**
```json
{
  "status": "healthy",
  "version": "1.0.0",
  "timestamp": "2026-01-01T10:00:00Z",
  "components": {
    "database": "healthy",
    "redis": "healthy",
    "airflow": "healthy"
  }
}
```

**Response (503 Service Unavailable) - Unhealthy:**
```json
{
  "status": "unhealthy",
  "version": "1.0.0",
  "timestamp": "2026-01-01T10:00:00Z",
  "components": {
    "database": "healthy",
    "redis": "unhealthy",
    "airflow": "healthy"
  },
  "error": "Redis connection failed"
}
```

**Response Schema:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | string | Yes | `healthy`, `unhealthy`, `degraded` |
| `version` | string | Yes | Server version (from BuildProperties) |
| `timestamp` | ISO8601 | Yes | Response timestamp |
| `components` | object | Yes | Component status map |
| `error` | string | No | Primary error message if unhealthy |

**Component Status Values:**
- `healthy` - Component is fully operational
- `unhealthy` - Component is not responding
- `degraded` - Component is slow or partially operational

---

### 3.2 Extended Health Check

#### `GET /api/v1/health/extended`

**Purpose**: Detailed system diagnostics for `dli debug` command with component metrics.

**Authentication**: Required (OAuth2 Bearer token)

**Request:**
```http
GET /api/v1/health/extended
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Response (200 OK) - All Healthy:**
```json
{
  "status": "healthy",
  "version": "1.0.0",
  "timestamp": "2026-01-01T10:00:00Z",
  "uptime_seconds": 3600,
  "components": {
    "database": {
      "status": "healthy",
      "response_time_ms": 5,
      "pool_active": 10,
      "pool_max": 20,
      "pool_idle": 10
    },
    "redis": {
      "status": "healthy",
      "response_time_ms": 2,
      "memory_used_mb": 128,
      "connected_clients": 15
    },
    "airflow": {
      "status": "healthy",
      "response_time_ms": 150,
      "api_version": "2.8.0",
      "active_dags": 45,
      "running_tasks": 12
    }
  },
  "system": {
    "jvm_memory_used_mb": 512,
    "jvm_memory_max_mb": 1024,
    "jvm_memory_free_mb": 512,
    "cpu_usage_percent": 15.5,
    "disk_usage_percent": 67.2,
    "thread_count": 42
  },
  "environment": {
    "profile": "production",
    "region": "us-west-2",
    "java_version": "21",
    "kotlin_version": "2.2.21"
  }
}
```

**Response (503 Service Unavailable) - Degraded:**
```json
{
  "status": "degraded",
  "version": "1.0.0",
  "timestamp": "2026-01-01T10:00:00Z",
  "uptime_seconds": 3600,
  "components": {
    "database": {
      "status": "healthy",
      "response_time_ms": 5
    },
    "redis": {
      "status": "unhealthy",
      "response_time_ms": -1,
      "error": "Connection timeout after 5s"
    },
    "airflow": {
      "status": "degraded",
      "response_time_ms": 12500,
      "warning": "High response time (>10s)"
    }
  },
  "errors": [
    {
      "component": "redis",
      "error": "Connection timeout after 5s",
      "remediation": "Check Redis server status and network connectivity"
    },
    {
      "component": "airflow",
      "error": "High response time (>10s)",
      "remediation": "Check Airflow scheduler load and API health"
    }
  ],
  "system": {
    "jvm_memory_used_mb": 512,
    "jvm_memory_max_mb": 1024
  }
}
```

**Response Schema:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | string | Yes | Overall status: `healthy`, `unhealthy`, `degraded` |
| `version` | string | Yes | Server version |
| `timestamp` | ISO8601 | Yes | Response timestamp |
| `uptime_seconds` | long | Yes | Server uptime in seconds |
| `components` | object | Yes | Detailed component health objects |
| `system` | object | Yes | JVM and system resource metrics |
| `environment` | object | Yes | Environment and profile info |
| `errors` | array | No | List of component errors with remediation |

**Component Health Object Schema:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `status` | string | Yes | `healthy`, `unhealthy`, `degraded` |
| `response_time_ms` | int | Yes | Health check response time (-1 if failed) |
| `error` | string | No | Error message if unhealthy |
| `warning` | string | No | Warning message if degraded |
| *(component-specific)* | varies | No | Additional metrics per component |

---

## 4. Domain Model

### 4.1 DTOs

```kotlin
// HealthStatus enum
enum class HealthStatus {
    HEALTHY,
    UNHEALTHY,
    DEGRADED
}

// Basic Health Response
data class HealthDto(
    val status: HealthStatus,
    val version: String,
    val timestamp: LocalDateTime,
    val components: Map<String, HealthStatus>,
    val error: String? = null,
)

// Extended Health Response
data class ExtendedHealthDto(
    val status: HealthStatus,
    val version: String,
    val timestamp: LocalDateTime,
    val uptimeSeconds: Long,
    val components: Map<String, ComponentHealthDto>,
    val system: SystemHealthDto,
    val environment: EnvironmentDto,
    val errors: List<ComponentErrorDto>? = null,
)

// Component Health Details
data class ComponentHealthDto(
    val status: HealthStatus,
    val responseTimeMs: Int,
    val error: String? = null,
    val warning: String? = null,
    // Component-specific metrics as extension properties
    val metrics: Map<String, Any>? = null,
)

// Database Component Health
data class DatabaseHealthDto(
    val status: HealthStatus,
    val responseTimeMs: Int,
    val poolActive: Int,
    val poolMax: Int,
    val poolIdle: Int,
    val error: String? = null,
)

// Redis Component Health
data class RedisHealthDto(
    val status: HealthStatus,
    val responseTimeMs: Int,
    val memoryUsedMb: Long,
    val connectedClients: Int,
    val error: String? = null,
)

// Airflow Component Health
data class AirflowHealthDto(
    val status: HealthStatus,
    val responseTimeMs: Int,
    val apiVersion: String?,
    val activeDags: Int?,
    val runningTasks: Int?,
    val error: String? = null,
)

// System Resources
data class SystemHealthDto(
    val jvmMemoryUsedMb: Long,
    val jvmMemoryMaxMb: Long,
    val jvmMemoryFreeMb: Long,
    val cpuUsagePercent: Double,
    val diskUsagePercent: Double,
    val threadCount: Int,
)

// Environment Info
data class EnvironmentDto(
    val profile: String,
    val region: String?,
    val javaVersion: String,
    val kotlinVersion: String,
)

// Component Error with Remediation
data class ComponentErrorDto(
    val component: String,
    val error: String,
    val remediation: String?,
)
```

### 4.2 Health Status Logic

```kotlin
// Overall status determination
fun determineOverallStatus(components: Map<String, ComponentHealthDto>): HealthStatus {
    val statuses = components.values.map { it.status }
    return when {
        statuses.any { it == HealthStatus.UNHEALTHY } -> HealthStatus.UNHEALTHY
        statuses.any { it == HealthStatus.DEGRADED } -> HealthStatus.DEGRADED
        else -> HealthStatus.HEALTHY
    }
}

// Response time thresholds (configurable)
object HealthThresholds {
    const val DATABASE_HEALTHY_MS = 100
    const val DATABASE_DEGRADED_MS = 1000
    const val REDIS_HEALTHY_MS = 50
    const val REDIS_DEGRADED_MS = 500
    const val AIRFLOW_HEALTHY_MS = 5000
    const val AIRFLOW_DEGRADED_MS = 10000
}
```

---

## 5. Implementation Notes

### 5.1 Service Layer

```kotlin
@Service
class ExtendedHealthService(
    private val dataSource: DataSource,
    private val redisTemplate: RedisTemplate<String, String>,
    private val airflowClient: AirflowClient?,  // Optional dependency
    private val buildProperties: BuildProperties?,
    private val applicationContext: ApplicationContext,
) {
    private val startTime = System.currentTimeMillis()

    /**
     * Basic health check for load balancers.
     * No authentication required.
     */
    fun getBasicHealth(): HealthDto {
        val components = mutableMapOf<String, HealthStatus>()

        components["database"] = checkDatabaseStatus()
        components["redis"] = checkRedisStatus()
        airflowClient?.let { components["airflow"] = checkAirflowStatus() }

        val overallStatus = determineOverallStatus(components)
        val error = if (overallStatus != HealthStatus.HEALTHY) {
            components.entries
                .filter { it.value != HealthStatus.HEALTHY }
                .joinToString(", ") { "${it.key}: ${it.value}" }
        } else null

        return HealthDto(
            status = overallStatus,
            version = buildProperties?.version ?: "unknown",
            timestamp = LocalDateTime.now(),
            components = components,
            error = error,
        )
    }

    /**
     * Extended health check with detailed diagnostics.
     * Requires authentication.
     */
    fun getExtendedHealth(): ExtendedHealthDto {
        val components = mutableMapOf<String, ComponentHealthDto>()
        val errors = mutableListOf<ComponentErrorDto>()

        // Check each component
        val dbHealth = checkDatabaseHealth()
        components["database"] = dbHealth.toComponentHealthDto()
        if (dbHealth.status != HealthStatus.HEALTHY) {
            errors.add(ComponentErrorDto(
                component = "database",
                error = dbHealth.error ?: "Database check failed",
                remediation = "Check database connection settings and server availability"
            ))
        }

        val redisHealth = checkRedisHealth()
        components["redis"] = redisHealth.toComponentHealthDto()
        if (redisHealth.status != HealthStatus.HEALTHY) {
            errors.add(ComponentErrorDto(
                component = "redis",
                error = redisHealth.error ?: "Redis check failed",
                remediation = "Check Redis server status and network connectivity"
            ))
        }

        airflowClient?.let {
            val airflowHealth = checkAirflowHealth()
            components["airflow"] = airflowHealth.toComponentHealthDto()
            if (airflowHealth.status != HealthStatus.HEALTHY) {
                errors.add(ComponentErrorDto(
                    component = "airflow",
                    error = airflowHealth.error ?: "Airflow check failed",
                    remediation = "Check Airflow scheduler and API health"
                ))
            }
        }

        return ExtendedHealthDto(
            status = determineOverallStatus(components),
            version = buildProperties?.version ?: "unknown",
            timestamp = LocalDateTime.now(),
            uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000,
            components = components,
            system = getSystemHealth(),
            environment = getEnvironment(),
            errors = errors.takeIf { it.isNotEmpty() },
        )
    }

    private fun checkDatabaseHealth(): DatabaseHealthDto {
        return try {
            val startTime = System.nanoTime()
            dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute("SELECT 1")
                }
            }
            val responseTimeMs = ((System.nanoTime() - startTime) / 1_000_000).toInt()

            val hikariDataSource = dataSource as? HikariDataSource
            DatabaseHealthDto(
                status = when {
                    responseTimeMs > HealthThresholds.DATABASE_DEGRADED_MS -> HealthStatus.DEGRADED
                    responseTimeMs > HealthThresholds.DATABASE_HEALTHY_MS -> HealthStatus.DEGRADED
                    else -> HealthStatus.HEALTHY
                },
                responseTimeMs = responseTimeMs,
                poolActive = hikariDataSource?.hikariPoolMXBean?.activeConnections ?: 0,
                poolMax = hikariDataSource?.maximumPoolSize ?: 0,
                poolIdle = hikariDataSource?.hikariPoolMXBean?.idleConnections ?: 0,
            )
        } catch (e: Exception) {
            DatabaseHealthDto(
                status = HealthStatus.UNHEALTHY,
                responseTimeMs = -1,
                poolActive = 0,
                poolMax = 0,
                poolIdle = 0,
                error = e.message,
            )
        }
    }

    private fun checkRedisHealth(): RedisHealthDto {
        return try {
            val startTime = System.nanoTime()
            val pong = redisTemplate.connectionFactory?.connection?.ping()
            val responseTimeMs = ((System.nanoTime() - startTime) / 1_000_000).toInt()

            val info = redisTemplate.connectionFactory?.connection?.info("memory")
            val usedMemory = info?.get("used_memory")?.toString()?.toLongOrNull()?.div(1024 * 1024) ?: 0
            val clients = redisTemplate.connectionFactory?.connection?.info("clients")
                ?.get("connected_clients")?.toString()?.toIntOrNull() ?: 0

            RedisHealthDto(
                status = when {
                    responseTimeMs > HealthThresholds.REDIS_DEGRADED_MS -> HealthStatus.DEGRADED
                    responseTimeMs > HealthThresholds.REDIS_HEALTHY_MS -> HealthStatus.DEGRADED
                    else -> HealthStatus.HEALTHY
                },
                responseTimeMs = responseTimeMs,
                memoryUsedMb = usedMemory,
                connectedClients = clients,
            )
        } catch (e: Exception) {
            RedisHealthDto(
                status = HealthStatus.UNHEALTHY,
                responseTimeMs = -1,
                memoryUsedMb = 0,
                connectedClients = 0,
                error = e.message,
            )
        }
    }

    private fun checkAirflowHealth(): AirflowHealthDto {
        return try {
            val startTime = System.nanoTime()
            val status = airflowClient?.getHealth()
            val responseTimeMs = ((System.nanoTime() - startTime) / 1_000_000).toInt()

            AirflowHealthDto(
                status = when {
                    responseTimeMs > HealthThresholds.AIRFLOW_DEGRADED_MS -> HealthStatus.DEGRADED
                    responseTimeMs > HealthThresholds.AIRFLOW_HEALTHY_MS -> HealthStatus.DEGRADED
                    else -> HealthStatus.HEALTHY
                },
                responseTimeMs = responseTimeMs,
                apiVersion = status?.version,
                activeDags = status?.activeDags,
                runningTasks = status?.runningTasks,
            )
        } catch (e: Exception) {
            AirflowHealthDto(
                status = HealthStatus.UNHEALTHY,
                responseTimeMs = -1,
                apiVersion = null,
                activeDags = null,
                runningTasks = null,
                error = e.message,
            )
        }
    }

    private fun getSystemHealth(): SystemHealthDto {
        val runtime = Runtime.getRuntime()
        val osBean = ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean
        val threadMXBean = ManagementFactory.getThreadMXBean()

        return SystemHealthDto(
            jvmMemoryUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024),
            jvmMemoryMaxMb = runtime.maxMemory() / (1024 * 1024),
            jvmMemoryFreeMb = runtime.freeMemory() / (1024 * 1024),
            cpuUsagePercent = osBean?.processCpuLoad?.times(100) ?: 0.0,
            diskUsagePercent = calculateDiskUsage(),
            threadCount = threadMXBean.threadCount,
        )
    }

    private fun getEnvironment(): EnvironmentDto {
        return EnvironmentDto(
            profile = applicationContext.environment.activeProfiles.firstOrNull() ?: "default",
            region = applicationContext.environment.getProperty("cloud.region"),
            javaVersion = System.getProperty("java.version"),
            kotlinVersion = KotlinVersion.CURRENT.toString(),
        )
    }

    private fun calculateDiskUsage(): Double {
        val roots = java.io.File.listRoots()
        val totalSpace = roots.sumOf { it.totalSpace }
        val freeSpace = roots.sumOf { it.freeSpace }
        return if (totalSpace > 0) ((totalSpace - freeSpace).toDouble() / totalSpace) * 100 else 0.0
    }
}
```

### 5.2 Controller Layer

```kotlin
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "System health and diagnostics APIs")
class HealthController(
    private val extendedHealthService: ExtendedHealthService,
) {
    @Operation(
        summary = "Basic health check",
        description = "Returns basic health status for load balancers. No authentication required."
    )
    @GetMapping("/health")
    fun health(): ResponseEntity<HealthDto> {
        val health = extendedHealthService.getBasicHealth()
        val httpStatus = when (health.status) {
            HealthStatus.HEALTHY -> HttpStatus.OK
            HealthStatus.DEGRADED -> HttpStatus.OK  // Still operational
            HealthStatus.UNHEALTHY -> HttpStatus.SERVICE_UNAVAILABLE
        }
        return ResponseEntity.status(httpStatus).body(health)
    }

    @Operation(
        summary = "Extended health check",
        description = "Returns detailed system diagnostics. Requires authentication."
    )
    @GetMapping("/health/extended")
    @PreAuthorize("isAuthenticated()")
    fun extendedHealth(): ResponseEntity<ExtendedHealthDto> {
        val health = extendedHealthService.getExtendedHealth()
        val httpStatus = when (health.status) {
            HealthStatus.HEALTHY -> HttpStatus.OK
            HealthStatus.DEGRADED -> HttpStatus.OK
            HealthStatus.UNHEALTHY -> HttpStatus.SERVICE_UNAVAILABLE
        }
        return ResponseEntity.status(httpStatus).body(health)
    }
}
```

### 5.3 Configuration

```kotlin
@Configuration
class HealthConfiguration {
    @Bean
    @ConfigurationProperties(prefix = "health.thresholds")
    fun healthThresholds(): HealthThresholdsProperties = HealthThresholdsProperties()
}

@ConfigurationProperties(prefix = "health.thresholds")
data class HealthThresholdsProperties(
    var databaseHealthyMs: Int = 100,
    var databaseDegradedMs: Int = 1000,
    var redisHealthyMs: Int = 50,
    var redisDegradedMs: Int = 500,
    var airflowHealthyMs: Int = 5000,
    var airflowDegradedMs: Int = 10000,
)
```

### 5.4 application.yaml

```yaml
health:
  thresholds:
    database-healthy-ms: 100
    database-degraded-ms: 1000
    redis-healthy-ms: 50
    redis-degraded-ms: 500
    airflow-healthy-ms: 5000
    airflow-degraded-ms: 10000
```

---

## 6. Testing Requirements

### 6.1 Unit Tests

```kotlin
@ExtendWith(MockitoExtension::class)
class ExtendedHealthServiceTest {
    @Mock private lateinit var dataSource: DataSource
    @Mock private lateinit var connection: Connection
    @Mock private lateinit var statement: Statement
    @Mock private lateinit var redisTemplate: RedisTemplate<String, String>
    @Mock private lateinit var buildProperties: BuildProperties
    @Mock private lateinit var applicationContext: ApplicationContext

    @InjectMocks
    private lateinit var healthService: ExtendedHealthService

    @Test
    fun `should return healthy status when all components are healthy`() {
        // Given
        given(dataSource.connection).willReturn(connection)
        given(connection.createStatement()).willReturn(statement)
        given(statement.execute("SELECT 1")).willReturn(true)
        given(redisTemplate.connectionFactory?.connection?.ping()).willReturn("PONG")
        given(buildProperties.version).willReturn("1.0.0")

        // When
        val result = healthService.getBasicHealth()

        // Then
        assertThat(result.status).isEqualTo(HealthStatus.HEALTHY)
        assertThat(result.components["database"]).isEqualTo(HealthStatus.HEALTHY)
        assertThat(result.components["redis"]).isEqualTo(HealthStatus.HEALTHY)
    }

    @Test
    fun `should return unhealthy status when database connection fails`() {
        // Given
        given(dataSource.connection).willThrow(SQLException("Connection refused"))
        given(redisTemplate.connectionFactory?.connection?.ping()).willReturn("PONG")

        // When
        val result = healthService.getBasicHealth()

        // Then
        assertThat(result.status).isEqualTo(HealthStatus.UNHEALTHY)
        assertThat(result.components["database"]).isEqualTo(HealthStatus.UNHEALTHY)
        assertThat(result.error).contains("database")
    }

    @Test
    fun `should return degraded status when response time exceeds threshold`() {
        // Given - simulate slow database
        given(dataSource.connection).willAnswer {
            Thread.sleep(150)  // Exceed healthy threshold
            connection
        }
        given(connection.createStatement()).willReturn(statement)
        given(statement.execute("SELECT 1")).willReturn(true)

        // When
        val result = healthService.getExtendedHealth()

        // Then
        assertThat(result.components["database"]?.status).isEqualTo(HealthStatus.DEGRADED)
    }
}
```

### 6.2 Integration Tests

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class HealthControllerIntegrationTest {
    @Autowired
    private lateinit var testRestTemplate: TestRestTemplate

    @Test
    fun `GET health should return 200 when healthy`() {
        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/health",
            HealthDto::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.status).isEqualTo(HealthStatus.HEALTHY)
        assertThat(response.body?.components).containsKey("database")
    }

    @Test
    fun `GET health extended without auth should return 401`() {
        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/health/extended",
            String::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    @WithMockUser
    fun `GET health extended with auth should return detailed diagnostics`() {
        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/health/extended",
            ExtendedHealthDto::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.uptimeSeconds).isGreaterThan(0)
        assertThat(response.body?.system).isNotNull
        assertThat(response.body?.environment).isNotNull
    }
}
```

### 6.3 CLI Integration Tests

```bash
# Test basic health endpoint
curl -X GET http://localhost:8081/api/v1/health | jq

# Test extended health (requires auth)
curl -X GET http://localhost:8081/api/v1/health/extended \
  -H "Authorization: Bearer $TOKEN" | jq

# Test via CLI
dli debug --server --server-url http://localhost:8081
dli debug --verbose --server-url http://localhost:8081
dli debug --json --server-url http://localhost:8081
```

### 6.4 Test Coverage Requirements

| Component | Coverage Target | Notes |
|-----------|----------------|-------|
| `ExtendedHealthService` | 90% | Core health check logic |
| `HealthController` | 80% | Endpoint routing |
| Component health checks | 85% | Database, Redis, Airflow |
| Error scenarios | 90% | Connection failures, timeouts |

---

## 7. Related Documents

### 7.1 Internal References

| Document | Purpose |
|----------|---------|
| [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) | Implementation timeline |
| [`INTEGRATION_PATTERNS.md`](./INTEGRATION_PATTERNS.md) | Spring Boot patterns |
| [`ERROR_CODES.md`](./ERROR_CODES.md) | Error handling standards |
| [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md) | CLI to API mapping |
| [`archive/P0_CRITICAL_APIS.md`](./archive/P0_CRITICAL_APIS.md) | Source specification |

### 7.2 CLI References

| File | Purpose |
|------|---------|
| `project-interface-cli/src/dli/commands/debug.py` | CLI debug command |
| `project-interface-cli/src/dli/core/debug/models.py` | Debug data models |
| `project-interface-cli/src/dli/api/debug.py` | DebugAPI implementation |

### 7.3 Existing Implementation

| File | Status | Action Required |
|------|--------|-----------------|
| `HealthController.kt` | Partial | Refactor to use new service |
| `ExtendedHealthService` | Not exists | Create new |
| `HealthDto` | Not exists | Create new |

---

## Appendix A: Migration from Current Implementation

### Current HealthController (to be replaced)

The existing `HealthController.kt` returns a basic status but lacks:
- Component health checks (database, Redis, Airflow)
- Extended diagnostics endpoint
- Proper HTTP status codes (503 for unhealthy)

### Migration Steps

1. Create `ExtendedHealthService` with component health checks
2. Create DTO classes in `dto/health/` package
3. Update `HealthController` to use new service
4. Add authentication to extended endpoint
5. Configure health thresholds in `application.yaml`

---

## Appendix B: Error Codes

| Code | Description | HTTP Status |
|------|-------------|-------------|
| `HEALTH_CHECK_FAILED` | One or more components unhealthy | 503 |
| `DATABASE_UNAVAILABLE` | Database connection failed | 503 |
| `REDIS_UNAVAILABLE` | Redis connection failed | 503 |
| `AIRFLOW_UNAVAILABLE` | Airflow API unavailable | 503 |
| `UNAUTHORIZED` | Missing or invalid authentication | 401 |

---

*This document provides implementation-ready specifications for Health APIs, enabling the `dli debug` CLI command within Week 2.5.*

---

## Appendix C: Review Feedback

> **Reviewed by:** feature-basecamp-server Agent | **Date:** 2026-01-01 | **Rating:** 4.5/5

### Strengths
- Excellent CLI command mapping with clear data source attribution
- Well-defined API specifications with both basic and extended health endpoints
- Comprehensive DTO hierarchy (HealthDto, ExtendedHealthDto, ComponentHealthDto)
- Good implementation of configurable health thresholds via `@ConfigurationProperties`
- Proper authentication distinction (basic health is public, extended requires auth)

### Issues to Address

| Priority | Issue | Recommendation |
|----------|-------|----------------|
| **High** | `ExtendedHealthService` in domain layer has infrastructure dependencies (`DataSource`, `RedisTemplate`) - violates hexagonal architecture | Move to `module-core-infra` or create domain interfaces (ports) for health checking |
| **Medium** | Error codes don't align with ERROR_CODES.md patterns - missing `httpStatus` property | Add `httpStatus` property to exception classes |
| **Low** | DTOs not following `*Dto` suffix consistently | Minor inconsistency, optional fix |

### Required Changes Before Implementation
1. Refactor `ExtendedHealthService` to respect hexagonal architecture (domain should not import infrastructure)
2. Create `HealthIndicator` interfaces in domain layer, implement in infrastructure
3. Add `@RestControllerAdvice` exception handler for health-specific exceptions
