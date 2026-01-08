# Transpile API Feature Specification

> **Version:** 1.1.0 | **Status:** ✅ **IMPLEMENTED** | **Priority:** P3 Low
> **CLI Commands:** `dli metric transpile`, `dli dataset transpile` | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Timeline:** Week 11-12 (P3 Phase)
> **Cross-Reference:** [`archive/P3_LOW_APIS.md`](./archive/P3_LOW_APIS.md) Section 4

> **📦 Data Source:** ✅ Self-managed JPA (규칙 저장) + ✅ External API (basecamp-parser 변환)
> **Entities:** `TranspileRuleEntity`
> **External:** `BasecampParserClient` → basecamp-parser service

---

## 1. Overview

### 1.1 Purpose

The Transpile API provides SQL dialect conversion capabilities, enabling the CLI to transform SQL queries between different database engines (BigQuery, Trino). This feature supports:

1. **Rule Management**: Centralized transpile rules for consistent SQL transformation
2. **Metric/Dataset SQL Transpilation**: Convert resource SQL from source dialect to target dialect
3. **Parser Integration**: Leverage basecamp-parser for SQLglot-based transformations

### 1.2 Business Value

| Benefit | Description |
|---------|-------------|
| **Cross-Engine Portability** | Execute same SQL across BigQuery and Trino |
| **Centralized Rules** | Server-managed transpile rules with CLI caching |
| **Audit Trail** | Track applied rules and transformations |
| **Development Velocity** | Write once, run anywhere SQL |

### 1.3 Architecture Context

```
dli CLI                    Basecamp Server              Basecamp Parser
   |                             |                            |
   |--GET /transpile/rules------>|                            |
   |<--rules (cached)------------|                            |
   |                             |                            |
   |--GET /transpile/metrics/{n}->|                            |
   |                             |--POST /transpile----------->|
   |                             |<--transpiled SQL-----------|
   |<--TranspileResult-----------|                            |
```

---

## 2. CLI Command Mapping

### 2.1 Command to API Endpoint Mapping

| CLI Command | HTTP Method | API Endpoint | Description |
|-------------|-------------|--------------|-------------|
| `dli transpile rules` | GET | `/api/v1/transpile/rules` | Get transpile rules for CLI caching |
| `dli metric transpile <name>` | GET | `/api/v1/transpile/metrics/{metric_name}` | Get transpiled metric SQL |
| `dli dataset transpile <name>` | GET | `/api/v1/transpile/datasets/{dataset_name}` | Get transpiled dataset SQL |

### 2.2 CLI Command Details

**Metric Transpile Command:**
```bash
# Transpile metric SQL from BigQuery to Trino
dli metric transpile iceberg.reporting.user_summary \
    --target-dialect trino \
    --source-dialect bigquery \
    --output-format json

# Transpile from file (local mode)
dli metric transpile user_summary \
    --file ./metrics/user_summary.yaml \
    --target-dialect trino
```

**Dataset Transpile Command:**
```bash
# Transpile dataset SQL
dli dataset transpile iceberg.analytics.users \
    --target-dialect trino \
    --source-dialect bigquery

# Transpile from file (local mode)
dli dataset transpile users \
    --file ./datasets/users.yaml \
    --target-dialect bigquery
```

### 2.3 CLI Parameters to API Parameters

| CLI Flag | API Parameter | Type | Description |
|----------|---------------|------|-------------|
| `--target-dialect` | `target_dialect` | query param | Target SQL dialect (trino, bigquery) |
| `--source-dialect` | `source_dialect` | query param | Source SQL dialect (auto-detected if omitted) |
| `--output-format` | N/A | CLI only | Output format (text, json, yaml) |
| `--file` | N/A | CLI only | Local file path (local execution mode) |

---

## 3. API Specifications

### 3.1 Get Transpile Rules

#### `GET /api/v1/transpile/rules`

**Purpose**: Get SQL transpile rules for CLI caching. Rules are versioned to enable efficient cache invalidation.

**Request:**
```http
GET /api/v1/transpile/rules?version=latest
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `version` | string | No | `latest` | Rule version (`latest`, specific version ID) |
| `from_dialect` | string | No | - | Filter rules by source dialect |
| `to_dialect` | string | No | - | Filter rules by target dialect |

**Response (200 OK):**
```json
{
  "version": "2026-01-01-001",
  "rules": [
    {
      "name": "bigquery_to_trino_date_functions",
      "from_dialect": "bigquery",
      "to_dialect": "trino",
      "pattern": "DATE_SUB\\((.+?), INTERVAL (.+?) DAY\\)",
      "replacement": "date_add('day', -$2, $1)",
      "priority": 100,
      "enabled": true,
      "description": "Convert BigQuery DATE_SUB to Trino date_add"
    },
    {
      "name": "standard_table_references",
      "from_dialect": "any",
      "to_dialect": "any",
      "pattern": "`([^`]+)`",
      "replacement": "\"$1\"",
      "priority": 50,
      "enabled": true,
      "description": "Convert backtick identifiers to double quotes"
    },
    {
      "name": "bigquery_to_trino_array_agg",
      "from_dialect": "bigquery",
      "to_dialect": "trino",
      "pattern": "ARRAY_AGG\\((.+?) IGNORE NULLS\\)",
      "replacement": "array_agg($1) FILTER (WHERE $1 IS NOT NULL)",
      "priority": 90,
      "enabled": true,
      "description": "Convert BigQuery ARRAY_AGG IGNORE NULLS to Trino"
    }
  ],
  "metadata": {
    "created_at": "2026-01-01T10:00:00Z",
    "created_by": "system",
    "total_rules": 3,
    "cache_ttl_seconds": 3600
  }
}
```

**Response Headers:**
```http
ETag: "2026-01-01-001"
Cache-Control: max-age=3600
```

**Response (304 Not Modified):**
When client provides `If-None-Match` header matching current version.

---

### 3.2 Get Metric SQL (Transpiled)

#### `GET /api/v1/transpile/metrics/{metric_name}`

**Purpose**: Get transpiled SQL for a registered metric.

**Request:**
```http
GET /api/v1/transpile/metrics/iceberg.reporting.user_summary?target_dialect=trino
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `metric_name` | string | Yes | Full metric name (catalog.schema.name) |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `target_dialect` | string | Yes | - | Target SQL dialect (`trino`, `bigquery`) |
| `source_dialect` | string | No | auto-detect | Source SQL dialect |
| `parameters` | object | No | - | Parameter substitution values (JSON encoded) |

**Response (200 OK):**
```json
{
  "metric_name": "iceberg.reporting.user_summary",
  "source_dialect": "bigquery",
  "target_dialect": "trino",
  "original_sql": "SELECT user_id, COUNT(*) FROM `iceberg.analytics.users` WHERE DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) GROUP BY 1",
  "transpiled_sql": "SELECT user_id, COUNT(*) FROM \"iceberg.analytics.users\" WHERE date_add('day', -30, CURRENT_DATE) GROUP BY 1",
  "applied_rules": [
    {
      "name": "bigquery_to_trino_date_functions",
      "source": "DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY)",
      "target": "date_add('day', -30, CURRENT_DATE)"
    },
    {
      "name": "standard_table_references",
      "source": "`iceberg.analytics.users`",
      "target": "\"iceberg.analytics.users\""
    }
  ],
  "warnings": [],
  "transpiled_at": "2026-01-01T10:00:00Z",
  "duration_ms": 45
}
```

**Response (400 Bad Request):**
```json
{
  "error": {
    "code": "INVALID_DIALECT",
    "message": "Unsupported target dialect: 'postgres'",
    "details": {
      "supported_dialects": ["trino", "bigquery"]
    }
  }
}
```

**Response (404 Not Found):**
```json
{
  "error": {
    "code": "METRIC_NOT_FOUND",
    "message": "Metric 'iceberg.invalid.metric' not found",
    "details": {
      "metric_name": "iceberg.invalid.metric"
    }
  }
}
```

**Response (422 Unprocessable Entity):**
```json
{
  "error": {
    "code": "TRANSPILE_ERROR",
    "message": "Failed to transpile SQL",
    "details": {
      "parser_error": "Syntax error at line 1, column 45: unexpected token 'INVALID'",
      "original_sql": "SELECT INVALID SYNTAX FROM table"
    }
  }
}
```

---

### 3.3 Get Dataset SQL (Transpiled)

#### `GET /api/v1/transpile/datasets/{dataset_name}`

**Purpose**: Get transpiled SQL for a registered dataset.

**Request:**
```http
GET /api/v1/transpile/datasets/iceberg.analytics.users?target_dialect=bigquery
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `dataset_name` | string | Yes | Full dataset name (catalog.schema.name) |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `target_dialect` | string | Yes | - | Target SQL dialect (`trino`, `bigquery`) |
| `source_dialect` | string | No | auto-detect | Source SQL dialect |
| `parameters` | object | No | - | Parameter substitution values (JSON encoded) |

**Response (200 OK):**
```json
{
  "dataset_name": "iceberg.analytics.users",
  "source_dialect": "trino",
  "target_dialect": "bigquery",
  "original_sql": "SELECT user_id, date_add('day', -30, CURRENT_DATE) as cutoff FROM \"iceberg.raw.users\"",
  "transpiled_sql": "SELECT user_id, DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) as cutoff FROM `iceberg.raw.users`",
  "applied_rules": [
    {
      "name": "trino_to_bigquery_date_add",
      "source": "date_add('day', -30, CURRENT_DATE)",
      "target": "DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY)"
    },
    {
      "name": "standard_table_references_reverse",
      "source": "\"iceberg.raw.users\"",
      "target": "`iceberg.raw.users`"
    }
  ],
  "warnings": [],
  "transpiled_at": "2026-01-01T10:00:00Z",
  "duration_ms": 38
}
```

---

## 4. Domain Model

### 4.1 Entity Diagram

```
TranspileRule                     TranspileResult
+------------------+              +----------------------+
| id: Long (PK)    |              | id: Long (PK)        |
| name: String     |              | resource_name: String|
| from_dialect: Enum|             | resource_type: Enum  |
| to_dialect: Enum |              | source_dialect: Enum |
| pattern: String  |              | target_dialect: Enum |
| replacement: String|            | original_sql: Text   |
| priority: Int    |              | transpiled_sql: Text |
| enabled: Boolean |              | applied_rules: JSON  |
| description: String|            | warnings: JSON       |
| created_at: DateTime|           | duration_ms: Int     |
| updated_at: DateTime|           | created_at: DateTime |
+------------------+              +----------------------+
```

### 4.2 Kotlin Entity Classes

```kotlin
// Domain Entity
@Entity
@Table(name = "transpile_rules")
class TranspileRuleEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "from_dialect", nullable = false)
    val fromDialect: SqlDialect,

    @Enumerated(EnumType.STRING)
    @Column(name = "to_dialect", nullable = false)
    val toDialect: SqlDialect,

    @Column(nullable = false)
    val pattern: String,

    @Column(nullable = false)
    val replacement: String,

    @Column(nullable = false)
    val priority: Int = 100,

    @Column(nullable = false)
    val enabled: Boolean = true,

    @Column
    val description: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)

enum class SqlDialect {
    TRINO,
    BIGQUERY,
    ANY,
}
```

### 4.3 DTO Classes

```kotlin
// Request/Response DTOs
data class TranspileRulesDto(
    val version: String,
    val rules: List<TranspileRuleDto>,
    val metadata: TranspileMetadataDto,
)

data class TranspileRuleDto(
    val name: String,
    val fromDialect: String,
    val toDialect: String,
    val pattern: String,
    val replacement: String,
    val priority: Int,
    val enabled: Boolean,
    val description: String?,
)

data class TranspileMetadataDto(
    val createdAt: Instant,
    val createdBy: String,
    val totalRules: Int,
    val cacheTtlSeconds: Int,
)

data class TranspileResultDto(
    val resourceName: String,
    val sourceDialect: String,
    val targetDialect: String,
    val originalSql: String,
    val transpiledSql: String,
    val appliedRules: List<AppliedRuleDto>,
    val warnings: List<TranspileWarningDto>,
    val transpiledAt: Instant,
    val durationMs: Long,
)

data class AppliedRuleDto(
    val name: String,
    val source: String,
    val target: String,
)

data class TranspileWarningDto(
    val type: String,
    val message: String,
    val line: Int?,
    val column: Int?,
)
```

---

## 5. Integration with basecamp-parser

### 5.1 Parser API Call

The Basecamp Server delegates actual SQL transpilation to the basecamp-parser service using SQLglot.

**Request to Parser:**
```http
POST http://basecamp-parser:5000/transpile
Content-Type: application/json

{
  "sql": "SELECT user_id, COUNT(*) FROM `iceberg.analytics.users` WHERE DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) GROUP BY 1",
  "source_dialect": "bigquery",
  "target_dialect": "trino",
  "rules": [
    {
      "name": "bigquery_to_trino_date_functions",
      "pattern": "DATE_SUB\\((.+?), INTERVAL (.+?) DAY\\)",
      "replacement": "date_add('day', -$2, $1)"
    }
  ]
}
```

**Response from Parser:**
```json
{
  "success": true,
  "transpiled_sql": "SELECT user_id, COUNT(*) FROM \"iceberg.analytics.users\" WHERE date_add('day', -30, CURRENT_DATE) GROUP BY 1",
  "applied_transformations": [
    {
      "type": "dialect_conversion",
      "from": "bigquery",
      "to": "trino"
    },
    {
      "type": "custom_rule",
      "name": "bigquery_to_trino_date_functions"
    }
  ],
  "warnings": [],
  "parse_time_ms": 12,
  "transpile_time_ms": 8
}
```

### 5.2 Service Implementation

```kotlin
@Service
class TranspileService(
    private val parserClient: BasecampParserClient,
    private val transpileRuleRepository: TranspileRuleRepositoryJpa,
    private val metricService: MetricService,
    private val datasetService: DatasetService,
) {
    suspend fun transpileMetric(
        metricName: String,
        targetDialect: SqlDialect,
        sourceDialect: SqlDialect?,
    ): TranspileResultDto {
        // 1. Get metric SQL
        val metric = metricService.getMetric(GetMetricQuery(metricName))
            ?: throw MetricNotFoundException(metricName)

        // 2. Detect source dialect if not provided
        val detectedSourceDialect = sourceDialect
            ?: detectDialect(metric.sql)

        // 3. Get applicable rules
        val rules = transpileRuleRepository.findByDialects(
            fromDialect = detectedSourceDialect,
            toDialect = targetDialect,
        )

        // 4. Call parser service
        val startTime = System.currentTimeMillis()
        val parserResult = parserClient.transpile(
            sql = metric.sql,
            sourceDialect = detectedSourceDialect.name.lowercase(),
            targetDialect = targetDialect.name.lowercase(),
            rules = rules.map { it.toParserRule() },
        )

        val duration = System.currentTimeMillis() - startTime

        return TranspileResultDto(
            resourceName = metricName,
            sourceDialect = detectedSourceDialect.name.lowercase(),
            targetDialect = targetDialect.name.lowercase(),
            originalSql = metric.sql,
            transpiledSql = parserResult.transpiledSql,
            appliedRules = parserResult.appliedTransformations.map {
                AppliedRuleDto(
                    name = it.name ?: it.type,
                    source = "", // Extracted from transformations
                    target = "",
                )
            },
            warnings = parserResult.warnings.map {
                TranspileWarningDto(
                    type = it.type,
                    message = it.message,
                    line = it.line,
                    column = it.column,
                )
            },
            transpiledAt = Instant.now(),
            durationMs = duration,
        )
    }

    private fun detectDialect(sql: String): SqlDialect {
        // Simple heuristics for dialect detection
        return when {
            sql.contains("`") && sql.contains("DATE_SUB") -> SqlDialect.BIGQUERY
            sql.contains("\"") && sql.contains("date_add") -> SqlDialect.TRINO
            sql.contains("SAFE_DIVIDE") -> SqlDialect.BIGQUERY
            sql.contains("TRY_CAST") -> SqlDialect.TRINO
            else -> SqlDialect.TRINO // Default
        }
    }
}
```

### 5.3 Parser Client

```kotlin
@Component
class BasecampParserClient(
    private val webClient: WebClient,
    @Value("\${basecamp.parser.url}") private val parserUrl: String,
) {
    suspend fun transpile(
        sql: String,
        sourceDialect: String,
        targetDialect: String,
        rules: List<ParserRuleDto>,
    ): ParserTranspileResponse {
        return webClient.post()
            .uri("$parserUrl/transpile")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(
                ParserTranspileRequest(
                    sql = sql,
                    sourceDialect = sourceDialect,
                    targetDialect = targetDialect,
                    rules = rules,
                )
            )
            .retrieve()
            .awaitBody<ParserTranspileResponse>()
    }
}

data class ParserTranspileRequest(
    val sql: String,
    @JsonProperty("source_dialect")
    val sourceDialect: String,
    @JsonProperty("target_dialect")
    val targetDialect: String,
    val rules: List<ParserRuleDto>,
)

data class ParserRuleDto(
    val name: String,
    val pattern: String,
    val replacement: String,
)

data class ParserTranspileResponse(
    val success: Boolean,
    @JsonProperty("transpiled_sql")
    val transpiledSql: String,
    @JsonProperty("applied_transformations")
    val appliedTransformations: List<TransformationDto>,
    val warnings: List<ParserWarningDto>,
    @JsonProperty("parse_time_ms")
    val parseTimeMs: Long,
    @JsonProperty("transpile_time_ms")
    val transpileTimeMs: Long,
)
```

---

## 6. Rule Caching Strategy

### 6.1 Server-Side Caching

```kotlin
@Service
class TranspileRuleCacheService(
    private val transpileRuleRepository: TranspileRuleRepositoryJpa,
    private val cacheManager: CacheManager,
) {
    companion object {
        const val RULES_CACHE_NAME = "transpile-rules"
        const val CACHE_TTL_SECONDS = 3600L
    }

    @Cacheable(RULES_CACHE_NAME, key = "#fromDialect + '-' + #toDialect")
    fun getRulesByDialect(
        fromDialect: SqlDialect?,
        toDialect: SqlDialect?,
    ): TranspileRulesDto {
        val rules = transpileRuleRepository.findByDialectsAndEnabled(
            fromDialect = fromDialect,
            toDialect = toDialect,
            enabled = true,
        )

        return TranspileRulesDto(
            version = generateVersion(),
            rules = rules.map { it.toDto() },
            metadata = TranspileMetadataDto(
                createdAt = Instant.now(),
                createdBy = "system",
                totalRules = rules.size,
                cacheTtlSeconds = CACHE_TTL_SECONDS.toInt(),
            ),
        )
    }

    @CacheEvict(RULES_CACHE_NAME, allEntries = true)
    fun invalidateCache() {
        // Called when rules are updated
    }

    private fun generateVersion(): String {
        val now = Instant.now()
        return "${now.atZone(ZoneId.of("UTC")).toLocalDate()}-${now.toEpochMilli() % 1000}"
    }
}
```

### 6.2 CLI-Side Caching

The CLI caches transpile rules locally to minimize API calls:

```python
# CLI caching strategy (from project-interface-cli)
class TranspileRuleCache:
    """Local cache for transpile rules with ETag support."""

    def __init__(self, cache_dir: Path = Path.home() / ".dli" / "cache"):
        self.cache_dir = cache_dir
        self.cache_file = cache_dir / "transpile_rules.json"
        self.etag_file = cache_dir / "transpile_rules.etag"

    def get_rules(self, client: BasecampClient) -> TranspileRules:
        """Get rules with conditional request."""
        etag = self._read_etag()

        # Try conditional request
        response = client.get_transpile_rules(etag=etag)

        if response.status_code == 304:
            # Not modified, use cached rules
            return self._read_cached_rules()

        # New rules, update cache
        rules = response.json()
        self._write_cache(rules, response.headers.get("ETag"))
        return TranspileRules.from_dict(rules)
```

### 6.3 Cache Invalidation

| Event | Action |
|-------|--------|
| Rule created/updated/deleted | Evict server cache, increment version |
| CLI startup | Conditional GET with ETag |
| CLI `--no-cache` flag | Force fresh fetch |
| Cache TTL expired | Automatic re-fetch |

---

## 7. Testing Requirements

### 7.1 Unit Tests

```kotlin
@ExtendWith(MockKExtension::class)
class TranspileServiceTest {

    @MockK
    lateinit var parserClient: BasecampParserClient

    @MockK
    lateinit var transpileRuleRepository: TranspileRuleRepositoryJpa

    @MockK
    lateinit var metricService: MetricService

    @InjectMockKs
    lateinit var transpileService: TranspileService

    @Test
    fun `should transpile metric SQL from BigQuery to Trino`() = runTest {
        // Given
        val metricName = "iceberg.reporting.user_summary"
        val originalSql = "SELECT * FROM `users` WHERE DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY)"
        val expectedSql = "SELECT * FROM \"users\" WHERE date_add('day', -30, CURRENT_DATE)"

        coEvery { metricService.getMetric(any()) } returns MetricDto(
            name = metricName,
            sql = originalSql,
            // ...
        )

        coEvery { transpileRuleRepository.findByDialects(any(), any()) } returns listOf(
            createTestRule("bigquery_to_trino_date")
        )

        coEvery { parserClient.transpile(any(), any(), any(), any()) } returns ParserTranspileResponse(
            success = true,
            transpiledSql = expectedSql,
            appliedTransformations = emptyList(),
            warnings = emptyList(),
            parseTimeMs = 10,
            transpileTimeMs = 5,
        )

        // When
        val result = transpileService.transpileMetric(
            metricName = metricName,
            targetDialect = SqlDialect.TRINO,
            sourceDialect = SqlDialect.BIGQUERY,
        )

        // Then
        assertThat(result.transpiledSql).isEqualTo(expectedSql)
        assertThat(result.sourceDialect).isEqualTo("bigquery")
        assertThat(result.targetDialect).isEqualTo("trino")
    }

    @Test
    fun `should detect BigQuery dialect from SQL syntax`() = runTest {
        val sql = "SELECT SAFE_DIVIDE(a, b) FROM `table`"
        // Test dialect detection
    }

    @Test
    fun `should throw MetricNotFoundException for unknown metric`() = runTest {
        coEvery { metricService.getMetric(any()) } returns null

        assertThrows<MetricNotFoundException> {
            transpileService.transpileMetric(
                metricName = "unknown.metric",
                targetDialect = SqlDialect.TRINO,
                sourceDialect = null,
            )
        }
    }
}
```

### 7.2 Integration Tests

```kotlin
@SpringBootTest
@AutoConfigureWebTestClient
class TranspileControllerIntegrationTest(
    @Autowired val webTestClient: WebTestClient,
) {

    @Test
    fun `GET transpile rules should return versioned rules`() {
        webTestClient.get()
            .uri("/api/v1/transpile/rules")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.version").isNotEmpty
            .jsonPath("$.rules").isArray
            .jsonPath("$.metadata.total_rules").isNumber
    }

    @Test
    fun `GET transpile rules with ETag should return 304 when not modified`() {
        // First request to get ETag
        val response = webTestClient.get()
            .uri("/api/v1/transpile/rules")
            .exchange()
            .expectStatus().isOk
            .expectHeader().exists("ETag")
            .returnResult(String::class.java)

        val etag = response.responseHeaders.getFirst("ETag")

        // Second request with If-None-Match
        webTestClient.get()
            .uri("/api/v1/transpile/rules")
            .header("If-None-Match", etag)
            .exchange()
            .expectStatus().isNotModified
    }

    @Test
    fun `GET transpile metric should return transpiled SQL`() {
        webTestClient.get()
            .uri("/api/v1/transpile/metrics/iceberg.reporting.user_summary?target_dialect=trino")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.metric_name").isEqualTo("iceberg.reporting.user_summary")
            .jsonPath("$.transpiled_sql").isNotEmpty
            .jsonPath("$.applied_rules").isArray
    }
}
```

### 7.3 CLI Integration Tests

```bash
#!/bin/bash
# tests/integration/test_transpile.sh

SERVER_URL="http://localhost:8081"

# Test 1: Get transpile rules
echo "Testing transpile rules..."
dli transpile rules --server-url $SERVER_URL | jq -e '.version'

# Test 2: Transpile metric
echo "Testing metric transpile..."
RESULT=$(dli metric transpile iceberg.reporting.user_summary \
    --target-dialect trino \
    --server-url $SERVER_URL \
    --output-format json)

echo $RESULT | jq -e '.transpiled_sql'

# Test 3: Transpile dataset
echo "Testing dataset transpile..."
dli dataset transpile iceberg.analytics.users \
    --target-dialect bigquery \
    --server-url $SERVER_URL

echo "All transpile tests passed"
```

### 7.4 Test Coverage Requirements

| Component | Target Coverage | Key Test Areas |
|-----------|----------------|----------------|
| TranspileService | 90% | Dialect detection, rule application, error handling |
| TranspileController | 85% | Request validation, response formatting, caching headers |
| BasecampParserClient | 80% | API calls, error handling, timeout handling |
| TranspileRuleCacheService | 85% | Cache hits, misses, invalidation |

---

## 8. Related Documents

| Document | Description |
|----------|-------------|
| [`archive/P3_LOW_APIS.md`](./archive/P3_LOW_APIS.md) | P3 APIs specification (source) |
| [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md) | CLI to API mapping reference |
| [`ERROR_CODES.md`](./ERROR_CODES.md) | Error code definitions |
| [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) | Implementation timeline |
| [`../basecamp-parser/README.md`](../../project-basecamp-parser/README.md) | Parser service documentation |

### 8.1 CLI Reference

| CLI Document | Description |
|--------------|-------------|
| `project-interface-cli/features/TRANSPILE_FEATURE.md` | CLI transpile feature spec |
| `project-interface-cli/src/dli/core/transpile/` | CLI transpile implementation |

### 8.2 Implementation Checklist ✅ **COMPLETED**

- [x] ✅ Create `TranspileRuleEntity` in `module-core-domain`
- [x] ✅ Implement `TranspileRuleRepositoryJpa` and `TranspileRuleRepositoryDsl`
- [x] ✅ Create `TranspileService` with parser integration
- [x] ✅ Implement `MockBasecampParserClient` for transpile calls
- [x] ✅ Add `/api/v1/transpile/rules` endpoint
- [x] ✅ Add `/api/v1/transpile/metrics/{name}` endpoint
- [x] ✅ Add `/api/v1/transpile/datasets/{name}` endpoint
- [x] ✅ Configure Redis caching for rules
- [x] ✅ Add ETag support for conditional requests
- [x] ✅ Write unit tests (90% coverage target - 130+ tests achieved)
- [x] ✅ Write integration tests
- [x] ✅ Update OpenAPI specification

> **📦 Implementation Status:** All components implemented and tested. See [`TRANSPILE_RELEASE.md`](./TRANSPILE_RELEASE.md) for complete implementation details.

---

*This document provides implementation-ready specifications for the Transpile API, enabling cross-dialect SQL transformation between BigQuery and Trino.*

---

## 9. CLI SQL Rendering Architecture (v1.1.0) ✅ IMPLEMENTED

> **Added in v1.1.0** - CLI performs 100% self-contained SQL transpilation and sends rendered SQL to server
> **Implementation Status:** ✅ **COMPLETED** - See [`EXECUTION_RELEASE.md`](./EXECUTION_RELEASE.md) for implementation details

### 9.1 Execution Flow Overview

CLI는 Dataset, Metric, Quality 실행/검증 시 **자체적으로 SQL 렌더링을 100% 완료**한 후 Server로 전달합니다.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         CLI SQL Rendering Flow                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  [1] Spec YML 로드                                                           │
│      spec.iceberg.analytics.daily_clicks.yaml                               │
│                           │                                                  │
│                           ▼                                                  │
│  [2] Jinja 변수 치환 (기본 변수 치환만)                                        │
│      {{ execution_date }} → 2025-01-15                                       │
│      {{ param_name }} → value                                                │
│                           │                                                  │
│                           ▼                                                  │
│  [3] Transpile 정책 적용 (Optional)                                          │
│      ├── Bypass Mode (Default): SQLGlot만 사용                               │
│      └── Server Policy Mode: Server에서 규칙 다운로드 후 적용                  │
│          (--use-server-policy 또는 config.transpile.use_server_policy)       │
│                           │                                                  │
│                           ▼                                                  │
│  [4] SQLGlot으로 최종 SQL 생성                                                │
│      BigQuery → Trino 또는 Trino → BigQuery 변환                             │
│                           │                                                  │
│                           ▼                                                  │
│  [5] Execution Mode에 따른 실행                                               │
│      ┌─────────────────────────────────────────────────────────────────┐    │
│      │  LOCAL:  CLI → BigQuery/Trino 직접 실행                          │    │
│      │  SERVER: CLI → Basecamp Server (SQL + Spec + Deps 전송)         │    │
│      │  REMOTE: CLI → Basecamp Server → Redis/Kafka → Worker          │    │
│      └─────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.2 Execution Modes

| Mode | Description | Use Case |
|------|-------------|----------|
| **LOCAL** | CLI가 QueryEngine(BigQuery/Trino)에 직접 연결하여 실행 | 로컬 개발, 즉시 결과 확인 |
| **SERVER** | CLI가 렌더링된 SQL을 Server로 전송, Server가 실행 | 프로덕션 환경, 감사 추적 필요 |
| **REMOTE** | CLI → Server → Redis/Kafka → Worker 비동기 실행 | 장시간 실행 쿼리, 배치 작업 |

### 9.3 Server Transmission Payload

SERVER/REMOTE 모드에서 CLI가 Server로 전송하는 데이터:

```json
{
  "execution_request": {
    "resource_name": "iceberg.analytics.daily_clicks",
    "resource_type": "DATASET",
    "execution_mode": "SERVER",

    "rendered_sql": "SELECT user_id, COUNT(*) FROM \"iceberg.analytics.users\" WHERE created_at >= '2025-01-15' GROUP BY 1",

    "original_spec": {
      "apiVersion": "v1",
      "kind": "Dataset",
      "metadata": {
        "name": "iceberg.analytics.daily_clicks",
        "owner": "data-team"
      },
      "spec": {
        "sql": "datasets/iceberg/analytics/daily_clicks.sql",
        "parameters": [
          {"name": "execution_date", "type": "date", "required": true}
        ]
      }
    },

    "dependencies": [
      "iceberg.raw.events",
      "iceberg.dim.users"
    ],

    "parameters": {
      "execution_date": "2025-01-15"
    },

    "transpile_info": {
      "source_dialect": "bigquery",
      "target_dialect": "trino",
      "used_server_policy": false,
      "applied_rules": []
    }
  }
}
```

### 9.4 Transpile Policy Options

CLI에서 Server Transpile Policy를 사용하는 두 가지 방법:

#### Option 1: CLI Flag
```bash
# Server에서 transpile 규칙 다운로드 후 적용
dli dataset run iceberg.analytics.daily_clicks --use-server-policy

dli metric transpile iceberg.reporting.user_summary --use-server-policy
```

#### Option 2: Config Setting
```yaml
# dli.yaml 또는 ~/.dli/config.yaml
transpile:
  use_server_policy: true   # 항상 Server 정책 사용
  cache_policy: true        # 정책 로컬 캐싱 (ETag 기반)
  cache_ttl_seconds: 3600   # 캐시 TTL
```

#### Policy 적용 우선순위
```
CLI Flag > Config Setting > Default (Bypass)

--use-server-policy  → Server Policy 사용
--no-server-policy   → Bypass Mode 강제
(없음)               → Config 설정 또는 Bypass Mode
```

### 9.5 Jinja Template Level

CLI는 **기본 변수 치환만** 지원합니다 (dbt와 유사한 고급 기능 제외):

| 지원 | 미지원 |
|------|--------|
| `{{ param_name }}` | `{% if %}...{% endif %}` |
| `{{ param_name \| default('value') }}` | `{% for %}...{% endfor %}` |
| `{{ param_name \| upper }}` | `{% macro %}...{% endmacro %}` |
| 기본 필터 (upper, lower, trim) | ref(), source() 함수 |

**예시:**
```sql
-- datasets/iceberg/analytics/daily_clicks.sql
SELECT
    user_id,
    COUNT(*) as click_count
FROM {{ source_table }}
WHERE
    created_at >= '{{ execution_date }}'
    AND created_at < DATE_ADD('{{ execution_date }}', INTERVAL 1 DAY)
GROUP BY 1
```

### 9.6 Server-Side Changes

Server는 CLI로부터 **이미 렌더링된 SQL**을 받습니다. 기존 Server-side 렌더링 로직은 backward compatibility를 위해 유지하되, 새로운 API는 pre-rendered SQL을 수용합니다.

#### New Execution API (v2)

```http
POST /api/v1/execution/run
Content-Type: application/json

{
  "resource_name": "iceberg.analytics.daily_clicks",
  "resource_type": "DATASET",
  "execution_mode": "SERVER",
  "rendered_sql": "SELECT ...",
  "original_spec": { ... },
  "dependencies": [ ... ],
  "parameters": { ... },
  "transpile_info": { ... }
}
```

**Response:**
```json
{
  "execution_id": "exec-12345",
  "status": "RUNNING",
  "submitted_at": "2025-01-15T10:00:00Z",
  "estimated_completion": null
}
```

#### Backward Compatibility

기존 API(`GET /api/v1/transpile/metrics/{name}`, `GET /api/v1/transpile/datasets/{name}`)는 유지됩니다:
- Server-side 렌더링이 필요한 레거시 클라이언트 지원
- Admin UI에서의 직접 실행
- 디버깅 및 테스트 용도

### 9.7 Quality Rule SQL Generation

Quality Built-in Rules는 **CLI에서 SQL로 변환**됩니다:

| Rule Type | SQL Pattern |
|-----------|-------------|
| `not_null` | `SELECT COUNT(*) as failed_count FROM {{ table_name }} WHERE {{ column }} IS NULL` |
| `unique` | `SELECT {{ column }}, COUNT(*) as cnt FROM {{ table_name }} GROUP BY {{ column }} HAVING COUNT(*) > 1` |
| `row_count > 0` | `SELECT CASE WHEN COUNT(*) > 0 THEN 0 ELSE 1 END as failed FROM {{ table_name }}` |

**Spec 예시:**
```yaml
tests:
  - name: user_id_not_null
    type: not_null
    column: user_id
  - name: email_unique
    type: unique
    column: email
  - name: table_has_rows
    type: row_count
    operator: ">"
    value: 0
```

**CLI 생성 SQL:**
```sql
-- not_null test
SELECT COUNT(*) as failed_count
FROM iceberg.analytics.users
WHERE user_id IS NULL;

-- unique test
SELECT email, COUNT(*) as cnt
FROM iceberg.analytics.users
GROUP BY email
HAVING COUNT(*) > 1;

-- row_count test
SELECT CASE WHEN COUNT(*) > 0 THEN 0 ELSE 1 END as failed
FROM iceberg.analytics.users;
```

### 9.8 Related CLI Documents

| Document | Description |
|----------|-------------|
| [`CLI MODEL_FEATURE.md`](../../project-interface-cli/features/MODEL_FEATURE.md) | MODEL 추상화 및 CLI 구조 |
| [`CLI QUALITY_FEATURE.md`](../../project-interface-cli/features/QUALITY_FEATURE.md) | CLI Quality 테스트 기능 |
| [`CLI DATASET_FEATURE.md`](../../project-interface-cli/features/DATASET_FEATURE.md) | CLI Dataset 기능 (TBD) |
| [`CLI METRIC_FEATURE.md`](../../project-interface-cli/features/METRIC_FEATURE.md) | CLI Metric 기능 (TBD) |

---

## Appendix B: Review Feedback

> **Reviewed by:** expert-spring-kotlin Agent | **Date:** 2026-01-01 | **Rating:** 4.5/5

### Strengths
- Excellent coroutine integration with `suspend fun`
- Proper use of `coEvery` and `runTest` for coroutine testing
- Good caching strategy with ETag support
- Clean WebClient usage with `awaitBody()` extension
- Well-designed rule caching with `@Cacheable`/`@CacheEvict`

### Issues to Address

| Priority | Issue | Recommendation |
|----------|-------|----------------|
| **High** | `@Cacheable` with suspend functions requires special handling | Use CacheMonoAdapter or manual caching |
| **Medium** | Missing timeout configuration for WebClient | Add `.timeout(Duration.ofSeconds(30))` |
| **Medium** | Dialect detection using string contains is fragile | Use parser service for dialect detection |
| **Low** | Missing `@OptIn(ExperimentalCoroutinesApi::class)` in tests | Add annotation to test classes |
| **Low** | Missing `@Version` for optimistic locking on rule updates | Add for concurrency control |

### Required Changes Before Implementation
1. Fix `@Cacheable` to work with suspend functions (use manual caching or cache adapters)
2. Add timeout configuration for WebClient calls
3. Improve dialect detection to use parser service
4. Add `@OptIn(ExperimentalCoroutinesApi::class)` to test classes
