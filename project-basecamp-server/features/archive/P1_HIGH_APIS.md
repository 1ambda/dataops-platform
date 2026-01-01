# P1 High Priority APIs - Catalog & Lineage

> **Priority:** P1 High | **Implementation Time:** 3 weeks | **CLI Impact:** Data discovery & analysis
> **Target Audience:** Backend developers implementing catalog and lineage features
> **Cross-Reference:** [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) Phase 2 details

---

## 📋 Table of Contents

1. [Implementation Overview](#1-implementation-overview)
2. [Catalog APIs](#2-catalog-apis)
3. [Lineage APIs](#3-lineage-apis)
4. [Implementation Patterns](#4-implementation-patterns)
5. [External Integrations](#5-external-integrations)
6. [Testing Requirements](#6-testing-requirements)

---

## 1. Implementation Overview

### 1.1 P1 APIs Summary

| API Group | Endpoints | CLI Commands Enabled | Implementation Timeline |
|-----------|-----------|---------------------|----------------------|
| **Catalog** | 4 endpoints | `dli catalog list/search/get` | Week 3-4 |
| **Lineage** | 1 endpoint | `dli lineage show/upstream/downstream` | Week 5 |

**Total: 5 endpoints enabling data discovery functionality**

### 1.2 Implementation Dependencies

```
P0 Metrics/Datasets (Complete) → Catalog Metadata → Lineage Graph
                                      ↓
BigQuery/Trino Integration ← PII Masking Policy ← Sample Data
```

### 1.3 External System Requirements

| System | Purpose | Required Access |
|--------|---------|----------------|
| **BigQuery API** | Table/column metadata | `bigquery.tables.get`, `bigquery.datasets.list` |
| **Trino API** | Alternative metadata source | INFORMATION_SCHEMA access |
| **Neo4j (Optional)** | Graph storage for lineage | Read/Write access |
| **Redis** | Metadata caching | Standard cache operations |

---

## 2. Catalog APIs

### 2.1 List Tables

#### `GET /api/v1/catalog/tables`

**Purpose**: List tables from the data catalog for `dli catalog list`

**Request:**
```http
GET /api/v1/catalog/tables?project=my-project&dataset=analytics&owner=data-team@example.com&team=@data-eng&tags=tier::critical,pii&limit=50&offset=0
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `project` | string | No | - | Filter by project/catalog name |
| `dataset` | string | No | - | Filter by dataset/schema name |
| `owner` | string | No | - | Filter by owner email |
| `team` | string | No | - | Filter by team |
| `tags` | string | No | - | Comma-separated tags (AND condition) |
| `limit` | int | No | 50 | Max results (1-500) |
| `offset` | int | No | 0 | Pagination offset |

**Response (200 OK):**
```json
[
  {
    "name": "my-project.analytics.users",
    "engine": "bigquery",
    "owner": "data-team@example.com",
    "team": "@data-eng",
    "tags": ["tier::critical", "domain::analytics", "pii"],
    "row_count": 1500000,
    "last_updated": "2026-01-01T08:00:00Z",
    "match_context": null
  }
]
```

> **Implementation Note:** Add `match_context` field for search result highlighting compatibility

---

### 2.2 Search Tables

#### `GET /api/v1/catalog/search`

**Purpose**: Search tables by keyword for `dli catalog search`

**Request:**
```http
GET /api/v1/catalog/search?keyword=user&project=my-project&limit=20
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `keyword` | string | **Yes** | Search keyword |
| `project` | string | No | Limit search to project |
| `limit` | int | No | Max results (default: 20) |

**Response (200 OK):**
```json
[
  {
    "name": "my-project.analytics.users",
    "engine": "bigquery",
    "owner": "data-team@example.com",
    "team": "@data-eng",
    "tags": ["tier::critical", "pii"],
    "row_count": 1500000,
    "last_updated": "2026-01-01T08:00:00Z",
    "match_context": "Column: user_id, Description: User dimension table"
  }
]
```

**Search Scope:**
- Table names (exact and partial matches)
- Column names within tables
- Table descriptions and metadata
- Tag values

---

### 2.3 Get Table Details

#### `GET /api/v1/catalog/tables/{table_ref}`

**Purpose**: Get detailed table information for `dli catalog get`

**Request:**
```http
GET /api/v1/catalog/tables/my-project.analytics.users?include_sample=true
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `include_sample` | bool | No | Include sample data (default: false) |

**Response (200 OK):**
```json
{
  "name": "my-project.analytics.users",
  "engine": "bigquery",
  "owner": "data-team@example.com",
  "team": "@data-eng",
  "description": "User dimension table with profile information",
  "tags": ["tier::critical", "domain::analytics", "pii"],
  "row_count": 1500000,
  "last_updated": "2026-01-01T08:00:00Z",
  "basecamp_url": "https://basecamp.example.com/catalog/my-project.analytics.users",
  "columns": [
    {
      "name": "user_id",
      "data_type": "STRING",
      "description": "Unique user identifier",
      "is_pii": false,
      "fill_rate": 1.0,
      "distinct_count": 1500000
    },
    {
      "name": "email",
      "data_type": "STRING",
      "description": "User email address",
      "is_pii": true,
      "fill_rate": 0.98,
      "distinct_count": 1470000
    }
  ],
  "ownership": {
    "owner": "data-team@example.com",
    "team": "@data-eng",
    "stewards": ["alice@example.com", "bob@example.com"],
    "consumers": ["@analytics", "@marketing", "@product"]
  },
  "freshness": {
    "last_updated": "2026-01-01T08:00:00Z",
    "avg_update_lag_hours": 1.5,
    "update_frequency": "hourly",
    "is_stale": false,
    "stale_threshold_hours": 6
  },
  "quality": {
    "score": 92,
    "total_tests": 15,
    "passed_tests": 14,
    "failed_tests": 1,
    "warnings": 0,
    "recent_tests": [
      {
        "test_name": "user_id_not_null",
        "test_type": "not_null",
        "status": "pass",
        "failed_rows": 0
      }
    ]
  },
  "sample_data": [
    {"user_id": "user_001", "email": "***@example.com", "name": "***", "created_at": "2024-01-15T10:30:00Z", "country": "US"}
  ]
}
```

### 2.3.1 PII Masking Policy Implementation

**Policy: PII Masking**
- Columns with `is_pii: true` MUST have their sample data values masked with `***`
- PII detection based on column metadata or configurable patterns

```kotlin
@Service
class PIIMaskingService {
    fun maskSampleData(columns: List<ColumnInfo>, sampleData: List<Map<String, Any>>): List<Map<String, Any>> {
        val piiColumns = columns.filter { it.isPii }.map { it.name }.toSet()

        return sampleData.map { row ->
            row.mapValues { (columnName, value) ->
                if (columnName in piiColumns) "***" else value
            }
        }
    }
}
```

---

### 2.4 Get Sample Queries

#### `GET /api/v1/catalog/tables/{table_ref}/queries`

**Purpose**: Get sample queries for a table

**Request:**
```http
GET /api/v1/catalog/tables/my-project.analytics.users/queries?limit=5
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Response (200 OK):**
```json
[
  {
    "title": "Active users by country",
    "sql": "SELECT country, COUNT(*) FROM `my-project.analytics.users` WHERE last_login > DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) GROUP BY 1",
    "author": "analyst@example.com",
    "run_count": 156,
    "last_run": "2026-01-01T09:00:00Z"
  }
]
```

---

## 3. Lineage APIs

### 3.1 Get Resource Lineage

#### `GET /api/v1/lineage/{resource_name}`

**Purpose**: Get lineage (dependencies and dependents) for a resource

**Request:**
```http
GET /api/v1/lineage/iceberg.analytics.daily_clicks?direction=both&depth=-1
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `direction` | string | No | `upstream`, `downstream`, or `both` (default: `both`) |
| `depth` | int | No | Max traversal depth, -1 for unlimited (default: -1) |

**Response (200 OK):**
```json
{
  "root": {
    "name": "iceberg.analytics.daily_clicks",
    "type": "Dataset",
    "owner": "engineer@example.com",
    "team": "@data-eng",
    "description": "Daily click aggregations",
    "tags": ["feed", "daily"]
  },
  "nodes": [
    {
      "name": "iceberg.raw.clicks",
      "type": "Dataset",
      "owner": "ingestion@example.com",
      "team": "@data-platform",
      "description": "Raw source data for clicks",
      "tags": ["raw", "source"],
      "depth": -1
    },
    {
      "name": "iceberg.reporting.clicks_report",
      "type": "Dataset",
      "owner": "analyst@example.com",
      "team": "@analytics",
      "description": "Reporting view for clicks",
      "tags": ["reporting", "bi"],
      "depth": 1
    }
  ],
  "edges": [
    {
      "source": "iceberg.raw.clicks",
      "target": "iceberg.analytics.daily_clicks",
      "edge_type": "direct"
    },
    {
      "source": "iceberg.analytics.daily_clicks",
      "target": "iceberg.reporting.clicks_report",
      "edge_type": "direct"
    }
  ],
  "total_upstream": 2,
  "total_downstream": 1
}
```

**Lineage Node Types:**
- `Dataset`: Registered datasets
- `Metric`: Registered metrics
- `Table`: External tables
- `View`: Database views

**Edge Types:**
- `direct`: Direct SQL dependency
- `indirect`: Inferred through shared columns/tables
- `manual`: Manually declared dependency

---

## 4. Implementation Patterns

### 4.1 Catalog Service Architecture

```kotlin
// External Integration Layer
@Service
class CatalogMetadataClient(
    private val bigQueryClient: BigQuery,
    private val trinoClient: TrinoClient?,
) {
    fun getTableMetadata(tableRef: String): TableMetadata {
        // Fetch from BigQuery or Trino
    }

    fun searchTables(keyword: String, filters: CatalogFilters): List<TableInfo> {
        // Cross-engine search implementation
    }
}

// Business Logic Layer
@Service
class TableInfoService(
    private val catalogClient: CatalogMetadataClient,
    private val piiMaskingService: PIIMaskingService,
    private val cacheService: CacheService,
) {
    fun getTableDetails(tableRef: String, includeSample: Boolean): TableDetailDto {
        val cached = cacheService.get<TableDetailDto>("table:$tableRef")
        if (cached != null) return cached

        val metadata = catalogClient.getTableMetadata(tableRef)
        val result = TableDetailDto(
            name = metadata.name,
            columns = metadata.columns,
            sampleData = if (includeSample) {
                val rawSample = catalogClient.getSampleData(tableRef)
                piiMaskingService.maskSampleData(metadata.columns, rawSample)
            } else emptyList()
        )

        cacheService.set("table:$tableRef", result, Duration.ofMinutes(30))
        return result
    }
}
```

### 4.2 Lineage Service Architecture

```kotlin
// Graph Storage
interface LineageGraphStorage {
    fun storeLineage(resource: String, dependencies: List<LineageEdge>)
    fun getLineageGraph(resource: String, direction: LineageDirection, depth: Int): LineageGraph
}

@Service
class LineageGraphService(
    private val lineageStorage: LineageGraphStorage,
    private val sqlParser: SQLParser,
) {
    fun buildLineageFromSQL(resourceName: String, sql: String) {
        val dependencies = sqlParser.extractTableDependencies(sql)
        val edges = dependencies.map { dep ->
            LineageEdge(
                source = dep,
                target = resourceName,
                edgeType = EdgeType.DIRECT
            )
        }
        lineageStorage.storeLineage(resourceName, edges)
    }

    fun getResourceLineage(
        resourceName: String,
        direction: LineageDirection,
        depth: Int,
    ): LineageGraphDto {
        val graph = lineageStorage.getLineageGraph(resourceName, direction, depth)
        return LineageGraphMapper.toDto(graph)
    }
}

// In-Memory Implementation (MVP)
@Component
class InMemoryLineageStorage : LineageGraphStorage {
    private val graph = mutableMapOf<String, MutableList<LineageEdge>>()

    override fun getLineageGraph(
        resource: String,
        direction: LineageDirection,
        depth: Int,
    ): LineageGraph {
        // BFS traversal implementation
    }
}
```

### 4.3 Caching Strategy

```kotlin
@Service
class CatalogCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
) {
    companion object {
        private val TABLE_LIST_TTL = Duration.ofMinutes(15)
        private val TABLE_DETAIL_TTL = Duration.ofMinutes(30)
        private val SEARCH_RESULT_TTL = Duration.ofMinutes(5)
    }

    fun cacheTableList(filters: String, tables: List<TableInfo>) {
        val key = "catalog:list:${filters.hashCode()}"
        redisTemplate.opsForValue().set(key, JsonUtil.toJson(tables), TABLE_LIST_TTL)
    }

    fun getCachedTableList(filters: String): List<TableInfo>? {
        val key = "catalog:list:${filters.hashCode()}"
        val cached = redisTemplate.opsForValue().get(key)
        return cached?.let { JsonUtil.fromJson<List<TableInfo>>(it) }
    }
}
```

---

## 5. External Integrations

### 5.1 BigQuery Integration

```kotlin
@Component
class BigQueryCatalogClient(
    private val bigQuery: BigQuery,
    @Value("\${catalog.bigquery.projects}") private val projectIds: List<String>,
) {
    fun listTables(filters: CatalogFilters): List<TableInfo> {
        return projectIds.flatMap { projectId ->
            val datasets = bigQuery.listDatasets(projectId)
            datasets.flatMap { dataset ->
                bigQuery.listTables(dataset.datasetId).map { table ->
                    TableInfo(
                        name = "${projectId}.${dataset.datasetId.dataset}.${table.tableId.table}",
                        engine = "bigquery",
                        rowCount = table.numRows?.toLong(),
                        lastUpdated = table.lastModifiedTime?.let { Instant.ofEpochMilli(it) },
                        // ... other fields
                    )
                }
            }
        }
    }

    fun getTableDetail(tableRef: String): TableDetail {
        val (projectId, datasetId, tableId) = parseTableRef(tableRef)
        val table = bigQuery.getTable(TableId.of(projectId, datasetId, tableId))

        return TableDetail(
            name = tableRef,
            columns = table.definition.schema?.fields?.map { field ->
                ColumnInfo(
                    name = field.name,
                    dataType = field.type.toString(),
                    description = field.description,
                    isPii = detectPII(field.name, field.description),
                )
            } ?: emptyList()
        )
    }

    private fun detectPII(columnName: String, description: String?): Boolean {
        val piiPatterns = listOf(
            "email", "phone", "ssn", "credit_card", "passport",
            "address", "ip_address", "user_id", "customer_id"
        )
        val text = "$columnName ${description ?: ""}".lowercase()
        return piiPatterns.any { pattern -> text.contains(pattern) }
    }
}
```

### 5.2 SQL Dependency Parsing

```kotlin
@Service
class SQLLineageParser(
    private val sqlParser: SqlParser, // Using Calcite or similar
) {
    fun extractTableDependencies(sql: String): List<String> {
        return try {
            val parsed = sqlParser.parse(sql)
            val visitor = TableDependencyVisitor()
            parsed.accept(visitor)
            visitor.getTableNames()
        } catch (e: Exception) {
            logger.warn("Failed to parse SQL for lineage: $sql", e)
            emptyList()
        }
    }
}

class TableDependencyVisitor : SqlBasicVisitor<Void>() {
    private val tableNames = mutableSetOf<String>()

    override fun visitTableName(ctx: SqlIdentifier): Void? {
        tableNames.add(ctx.toString())
        return super.visitTableName(ctx)
    }

    fun getTableNames(): List<String> = tableNames.toList()
}
```

---

## 6. Testing Requirements

### 6.1 Integration Tests

```kotlin
@SpringBootTest
@Testcontainers
class CatalogControllerTest {
    @Container
    companion object {
        val redis = RedisContainer(DockerImageName.parse("redis:7-alpine"))
    }

    @MockBean private lateinit var bigQueryClient: BigQuery
    @Autowired private lateinit var testRestTemplate: TestRestTemplate

    @Test
    fun `should return table list with filters`() {
        // Given
        val mockTables = listOf(
            TableInfo(name = "project.dataset.users", engine = "bigquery")
        )
        given(bigQueryClient.listDatasets(any())).willReturn(mockDatasets())

        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/catalog/tables?project=my-project",
            Array<TableInfo>::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(1)
    }

    @Test
    fun `should mask PII in sample data`() {
        // Given
        val tableRef = "project.dataset.users"
        mockTableWithPII()

        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/catalog/tables/$tableRef?include_sample=true",
            TableDetailDto::class.java
        )

        // Then
        val sampleData = response.body!!.sampleData
        assertThat(sampleData[0]["email"]).isEqualTo("***")
    }
}
```

### 6.2 Performance Tests

```kotlin
@Test
fun `catalog list should respond within 2 seconds for 1000+ tables`() {
    // Given
    mockBigQueryWith1000Tables()

    // When
    val startTime = System.currentTimeMillis()
    val response = testRestTemplate.getForEntity(
        "/api/v1/catalog/tables?limit=500",
        Array<TableInfo>::class.java
    )
    val duration = System.currentTimeMillis() - startTime

    // Then
    assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    assertThat(duration).isLessThan(2000) // 2 seconds SLA
}
```

### 6.3 CLI Integration Tests

```bash
# Test catalog browsing
dli catalog list --project my-project --server-url http://localhost:8081
dli catalog search user --server-url http://localhost:8081
dli catalog get my-project.analytics.users --server-url http://localhost:8081

# Test lineage
dli lineage show iceberg.analytics.daily_clicks --server-url http://localhost:8081
dli lineage upstream iceberg.analytics.daily_clicks --server-url http://localhost:8081
dli lineage downstream iceberg.analytics.daily_clicks --depth 2 --server-url http://localhost:8081
```

---

## 🔗 Related Documentation

- **Implementation Timeline**: [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) Phase 2
- **Architecture Overview**: [`../BASECAMP_OVERVIEW.md`](../BASECAMP_OVERVIEW.md)
- **Error Handling**: [`../ERROR_CODES.md`](../ERROR_CODES.md)
- **P0 Critical APIs**: [`P0_CRITICAL_APIS.md`](./P0_CRITICAL_APIS.md)

### Next Priority
- **P2 Medium APIs**: [`P2_MEDIUM_APIS.md`](./P2_MEDIUM_APIS.md) - Workflow Management APIs

---

*This document provides implementation-ready specifications for P1 High Priority APIs, enabling data discovery and lineage analysis within 3 weeks.*