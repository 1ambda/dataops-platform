# Catalog API Feature Specification

> ✅ **COMPLETED**: This feature has been implemented. See [CATALOG_RELEASE.md](./CATALOG_RELEASE.md) for implementation details.

> **Version:** 1.0.0 | **Status:** Implemented | **Priority:** P1 High
> **CLI Commands:** `dli catalog list/search/get` | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Timeline:** Week 3
>
> **📦 Data Source:** ✅ Self-managed JPA | ~~External API (BigQuery/Trino)~~
> **Entities:** `CatalogTableEntity`, `CatalogColumnEntity`, `SampleQueryEntity`

---

## Table of Contents

1. [Overview](#1-overview)
2. [CLI Command Mapping](#2-cli-command-mapping)
3. [API Specifications](#3-api-specifications)
4. [Domain Model](#4-domain-model)
5. [External Integrations](#5-external-integrations)
6. [PII Masking Policy](#6-pii-masking-policy)
7. [Caching Strategy](#7-caching-strategy)
8. [Testing Requirements](#8-testing-requirements)
9. [Related Documents](#9-related-documents)

---

## 1. Overview

### 1.1 Purpose

The Catalog API provides data discovery capabilities for the DataOps Platform, enabling users to:
- Browse and list tables from BigQuery/Trino data catalogs
- Search across table names, columns, and metadata
- Retrieve detailed table information including schema, ownership, and quality metrics
- Access sample queries associated with tables

### 1.2 API Summary

| Endpoint | Method | Purpose | CLI Command |
|----------|--------|---------|-------------|
| `/api/v1/catalog/tables` | GET | List tables with filters | `dli catalog list` |
| `/api/v1/catalog/search` | GET | Search tables by keyword | `dli catalog search` |
| `/api/v1/catalog/tables/{table_ref}` | GET | Get table details | `dli catalog get` |
| `/api/v1/catalog/tables/{table_ref}/queries` | GET | Get sample queries | `dli catalog get --queries` |

### 1.3 Implementation Dependencies

```
P0 Metrics/Datasets (Complete) --> Catalog Metadata --> Lineage Graph
                                        |
BigQuery/Trino Integration <-- PII Masking Policy <-- Sample Data
```

### 1.4 External System Requirements

| System | Purpose | Required Access |
|--------|---------|----------------|
| **BigQuery API** | Table/column metadata | `bigquery.tables.get`, `bigquery.datasets.list` |
| **Trino API** | Alternative metadata source | INFORMATION_SCHEMA access |
| **Redis** | Metadata caching | Standard cache operations |

---

## 2. CLI Command Mapping

### 2.1 Command Overview

| CLI Command | API Endpoint | Description |
|-------------|--------------|-------------|
| `dli catalog list` | `GET /api/v1/catalog/tables` | List tables with optional filters |
| `dli catalog list --project my-project` | `GET /api/v1/catalog/tables?project=my-project` | Filter by project |
| `dli catalog list --owner user@example.com` | `GET /api/v1/catalog/tables?owner=user@example.com` | Filter by owner |
| `dli catalog list --tags tier::critical,pii` | `GET /api/v1/catalog/tables?tags=tier::critical,pii` | Filter by tags |
| `dli catalog search user` | `GET /api/v1/catalog/search?keyword=user` | Search tables |
| `dli catalog get project.dataset.table` | `GET /api/v1/catalog/tables/{table_ref}` | Get table details |
| `dli catalog get project.dataset.table --sample` | `GET /api/v1/catalog/tables/{table_ref}?include_sample=true` | Include sample data |

### 2.2 CLI Library API Integration

```python
from dli import CatalogAPI, ExecutionContext, ExecutionMode

# Server mode (production)
ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
api = CatalogAPI(context=ctx)

# List tables
tables = api.list_tables(project="my-project", limit=50)

# Search tables
results = api.search(keyword="user", project="my-project")

# Get table details
table = api.get(table_ref="my-project.analytics.users", include_sample=True)
```

### 2.3 Error Code Mapping

| Server Error Code | CLI Error Code | CLI Command Context |
|-------------------|----------------|---------------------|
| `TABLE_NOT_FOUND` | DLI-501 | `dli catalog get` |
| `CATALOG_SERVICE_ERROR` | DLI-502 | `dli catalog *` |
| `CATALOG_TIMEOUT` | DLI-503 | `dli catalog *` |
| `INVALID_TABLE_REFERENCE` | DLI-504 | `dli catalog get` |

---

## 3. API Specifications

### 3.1 List Tables

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
| `team` | string | No | - | Filter by team (e.g., `@data-eng`) |
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
  },
  {
    "name": "my-project.analytics.events",
    "engine": "bigquery",
    "owner": "data-team@example.com",
    "team": "@data-eng",
    "tags": ["tier::high", "domain::analytics"],
    "row_count": 50000000,
    "last_updated": "2026-01-01T09:30:00Z",
    "match_context": null
  }
]
```

**Error Responses:**

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 400 | `VALIDATION_ERROR` | Invalid query parameters |
| 401 | `UNAUTHORIZED` | Missing or invalid authentication |
| 502 | `CATALOG_SERVICE_ERROR` | BigQuery/Trino API error |
| 504 | `CATALOG_TIMEOUT` | External catalog service timeout |

---

### 3.2 Search Tables

#### `GET /api/v1/catalog/search`

**Purpose**: Search tables by keyword for `dli catalog search`

**Request:**
```http
GET /api/v1/catalog/search?keyword=user&project=my-project&limit=20
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `keyword` | string | **Yes** | - | Search keyword (min 2 characters) |
| `project` | string | No | - | Limit search to project |
| `limit` | int | No | 20 | Max results (1-100) |

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
  },
  {
    "name": "my-project.raw.user_events",
    "engine": "bigquery",
    "owner": "ingestion@example.com",
    "team": "@data-platform",
    "tags": ["raw", "source"],
    "row_count": 100000000,
    "last_updated": "2026-01-01T10:00:00Z",
    "match_context": "Table name match: user_events"
  }
]
```

**Search Scope:**
- Table names (exact and partial matches)
- Column names within tables
- Table descriptions and metadata
- Tag values

**Error Responses:**

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 400 | `VALIDATION_ERROR` | Missing or invalid keyword |
| 401 | `UNAUTHORIZED` | Missing or invalid authentication |
| 502 | `CATALOG_SERVICE_ERROR` | BigQuery/Trino API error |

---

### 3.3 Get Table Details

#### `GET /api/v1/catalog/tables/{table_ref}`

**Purpose**: Get detailed table information for `dli catalog get`

**Request:**
```http
GET /api/v1/catalog/tables/my-project.analytics.users?include_sample=true
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `table_ref` | string | **Yes** | Fully qualified table reference (project.dataset.table) |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `include_sample` | bool | No | false | Include sample data (PII-masked) |

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
    },
    {
      "name": "name",
      "data_type": "STRING",
      "description": "User full name",
      "is_pii": true,
      "fill_rate": 0.95,
      "distinct_count": 1425000
    },
    {
      "name": "created_at",
      "data_type": "TIMESTAMP",
      "description": "Account creation timestamp",
      "is_pii": false,
      "fill_rate": 1.0,
      "distinct_count": 1000000
    },
    {
      "name": "country",
      "data_type": "STRING",
      "description": "User country code",
      "is_pii": false,
      "fill_rate": 0.92,
      "distinct_count": 195
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
      },
      {
        "test_name": "email_format",
        "test_type": "regex",
        "status": "pass",
        "failed_rows": 0
      }
    ]
  },
  "sample_data": [
    {
      "user_id": "user_001",
      "email": "***",
      "name": "***",
      "created_at": "2024-01-15T10:30:00Z",
      "country": "US"
    },
    {
      "user_id": "user_002",
      "email": "***",
      "name": "***",
      "created_at": "2024-02-20T14:45:00Z",
      "country": "UK"
    }
  ]
}
```

**Error Responses:**

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 400 | `INVALID_TABLE_REFERENCE` | Invalid table reference format |
| 401 | `UNAUTHORIZED` | Missing or invalid authentication |
| 404 | `TABLE_NOT_FOUND` | Table not found in catalog |
| 502 | `CATALOG_SERVICE_ERROR` | BigQuery/Trino API error |
| 504 | `CATALOG_TIMEOUT` | External catalog service timeout |

---

### 3.4 Get Sample Queries

#### `GET /api/v1/catalog/tables/{table_ref}/queries`

**Purpose**: Get sample queries for a table

**Request:**
```http
GET /api/v1/catalog/tables/my-project.analytics.users/queries?limit=5
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `table_ref` | string | **Yes** | Fully qualified table reference |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `limit` | int | No | 5 | Max queries to return (1-20) |

**Response (200 OK):**
```json
[
  {
    "title": "Active users by country",
    "sql": "SELECT country, COUNT(*) as user_count FROM `my-project.analytics.users` WHERE last_login > DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) GROUP BY 1 ORDER BY 2 DESC",
    "author": "analyst@example.com",
    "run_count": 156,
    "last_run": "2026-01-01T09:00:00Z"
  },
  {
    "title": "New user signups by week",
    "sql": "SELECT DATE_TRUNC(created_at, WEEK) as week, COUNT(*) as signups FROM `my-project.analytics.users` GROUP BY 1 ORDER BY 1 DESC LIMIT 12",
    "author": "data-team@example.com",
    "run_count": 89,
    "last_run": "2025-12-28T15:30:00Z"
  }
]
```

**Error Responses:**

| HTTP Status | Error Code | Description |
|-------------|------------|-------------|
| 404 | `TABLE_NOT_FOUND` | Table not found in catalog |
| 401 | `UNAUTHORIZED` | Missing or invalid authentication |

---

## 4. Domain Model

### 4.1 Entity Diagram

```
+-------------------+       +-------------------+       +-------------------+
|    TableInfo      |       |   ColumnInfo      |       |   TableOwnership  |
+-------------------+       +-------------------+       +-------------------+
| - name: String    |       | - name: String    |       | - owner: String   |
| - engine: String  |  1:N  | - dataType: String|       | - team: String    |
| - owner: String   |------>| - description:    |       | - stewards: List  |
| - team: String    |       |     String?       |       | - consumers: List |
| - tags: Set       |       | - isPii: Boolean  |       +-------------------+
| - rowCount: Long? |       | - fillRate: Double|
| - lastUpdated:    |       | - distinctCount:  |       +-------------------+
|     Instant?      |       |     Long?         |       |  TableFreshness   |
| - matchContext:   |       +-------------------+       +-------------------+
|     String?       |                                   | - lastUpdated:    |
+-------------------+                                   |     Instant       |
         |                                              | - avgLagHours:    |
         | 1:1                                          |     Double        |
         v                                              | - updateFrequency:|
+-------------------+                                   |     String        |
|   TableDetail     |                                   | - isStale: Boolean|
+-------------------+                                   | - staleThreshold: |
| - name: String    |                                   |     Int           |
| - engine: String  |                                   +-------------------+
| - description:    |
|     String?       |       +-------------------+
| - columns: List   |       |   TableQuality    |
| - ownership:      |       +-------------------+
|     Ownership     |       | - score: Int      |
| - freshness:      |       | - totalTests: Int |
|     Freshness     |       | - passedTests: Int|
| - quality: Quality|       | - failedTests: Int|
| - sampleData: List|       | - warnings: Int   |
| - basecampUrl:    |       | - recentTests:    |
|     String?       |       |     List          |
+-------------------+       +-------------------+
```

### 4.2 Entity Classes

```kotlin
// Domain Layer: module-core-domain/src/main/kotlin/domain/catalog/

data class TableInfo(
    val name: String,
    val engine: String,
    val owner: String,
    val team: String?,
    val tags: Set<String> = emptySet(),
    val rowCount: Long? = null,
    val lastUpdated: Instant? = null,
    val matchContext: String? = null,
)

data class ColumnInfo(
    val name: String,
    val dataType: String,
    val description: String? = null,
    val isPii: Boolean = false,
    val fillRate: Double? = null,
    val distinctCount: Long? = null,
)

data class TableDetail(
    val name: String,
    val engine: String,
    val owner: String,
    val team: String?,
    val description: String? = null,
    val tags: Set<String> = emptySet(),
    val rowCount: Long? = null,
    val lastUpdated: Instant? = null,
    val basecampUrl: String? = null,
    val columns: List<ColumnInfo> = emptyList(),
    val ownership: TableOwnership? = null,
    val freshness: TableFreshness? = null,
    val quality: TableQuality? = null,
    val sampleData: List<Map<String, Any>> = emptyList(),
)

data class TableOwnership(
    val owner: String,
    val team: String?,
    val stewards: List<String> = emptyList(),
    val consumers: List<String> = emptyList(),
)

data class TableFreshness(
    val lastUpdated: Instant,
    val avgUpdateLagHours: Double? = null,
    val updateFrequency: String? = null,
    val isStale: Boolean = false,
    val staleThresholdHours: Int = 24,
)

data class TableQuality(
    val score: Int,
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val warnings: Int = 0,
    val recentTests: List<QualityTestResult> = emptyList(),
)

data class QualityTestResult(
    val testName: String,
    val testType: String,
    val status: TestStatus,
    val failedRows: Long = 0,
)

enum class TestStatus {
    PASS, FAIL, WARNING, SKIPPED
}

data class SampleQuery(
    val title: String,
    val sql: String,
    val author: String,
    val runCount: Int,
    val lastRun: Instant?,
)
```

### 4.3 Repository Interfaces

```kotlin
// Domain Layer: module-core-domain/src/main/kotlin/domain/catalog/repository/

interface CatalogRepository {
    fun listTables(filters: CatalogFilters): List<TableInfo>
    fun searchTables(keyword: String, project: String?, limit: Int): List<TableInfo>
    fun getTableDetail(tableRef: String): TableDetail?
    fun getTableColumns(tableRef: String): List<ColumnInfo>
    fun getSampleData(tableRef: String, limit: Int): List<Map<String, Any>>
}

interface SampleQueryRepository {
    fun findByTableRef(tableRef: String, limit: Int): List<SampleQuery>
    fun save(tableRef: String, query: SampleQuery): SampleQuery
    fun incrementRunCount(tableRef: String, title: String)
}

data class CatalogFilters(
    val project: String? = null,
    val dataset: String? = null,
    val owner: String? = null,
    val team: String? = null,
    val tags: Set<String> = emptySet(),
    val limit: Int = 50,
    val offset: Int = 0,
)
```

---

## 5. External Integrations

### 5.1 BigQuery Integration

```kotlin
// Infrastructure Layer: module-core-infra/src/main/kotlin/infra/external/catalog/

@Component
class BigQueryCatalogClient(
    private val bigQuery: BigQuery,
    @Value("\${catalog.bigquery.projects}") private val projectIds: List<String>,
) : CatalogRepository {

    companion object {
        private val logger = LoggerFactory.getLogger(BigQueryCatalogClient::class.java)
        private val PII_PATTERNS = listOf(
            "email", "phone", "ssn", "credit_card", "passport",
            "address", "ip_address", "name", "birth", "gender",
            "salary", "income", "bank", "account"
        )
    }

    override fun listTables(filters: CatalogFilters): List<TableInfo> {
        logger.debug("Listing tables with filters: {}", filters)

        val targetProjects = filters.project?.let { listOf(it) } ?: projectIds

        return targetProjects.flatMap { projectId ->
            listTablesFromProject(projectId, filters)
        }.sortedByDescending { it.lastUpdated }
         .drop(filters.offset)
         .take(filters.limit)
    }

    private fun listTablesFromProject(projectId: String, filters: CatalogFilters): List<TableInfo> {
        val datasets = bigQuery.listDatasets(projectId).iterateAll()

        return datasets
            .filter { filters.dataset == null || it.datasetId.dataset == filters.dataset }
            .flatMap { dataset ->
                bigQuery.listTables(dataset.datasetId).iterateAll().map { table ->
                    val fullTable = bigQuery.getTable(table.tableId)
                    TableInfo(
                        name = "${projectId}.${dataset.datasetId.dataset}.${table.tableId.table}",
                        engine = "bigquery",
                        owner = fullTable.labels?.get("owner") ?: "unknown",
                        team = fullTable.labels?.get("team"),
                        tags = extractTags(fullTable.labels),
                        rowCount = fullTable.numRows?.toLong(),
                        lastUpdated = fullTable.lastModifiedTime?.let { Instant.ofEpochMilli(it) },
                    )
                }
            }
            .filter { table ->
                (filters.owner == null || table.owner.contains(filters.owner, ignoreCase = true)) &&
                (filters.team == null || table.team == filters.team) &&
                (filters.tags.isEmpty() || filters.tags.all { it in table.tags })
            }
    }

    override fun getTableDetail(tableRef: String): TableDetail? {
        logger.debug("Getting table detail for: {}", tableRef)

        return try {
            val (projectId, datasetId, tableId) = parseTableRef(tableRef)
            val table = bigQuery.getTable(TableId.of(projectId, datasetId, tableId))
                ?: return null

            val columns = table.definition.schema?.fields?.map { field ->
                ColumnInfo(
                    name = field.name,
                    dataType = field.type.toString(),
                    description = field.description,
                    isPii = detectPII(field.name, field.description),
                )
            } ?: emptyList()

            TableDetail(
                name = tableRef,
                engine = "bigquery",
                owner = table.labels?.get("owner") ?: "unknown",
                team = table.labels?.get("team"),
                description = table.description,
                tags = extractTags(table.labels),
                rowCount = table.numRows?.toLong(),
                lastUpdated = table.lastModifiedTime?.let { Instant.ofEpochMilli(it) },
                basecampUrl = generateBasecampUrl(tableRef),
                columns = columns,
            )
        } catch (ex: BigQueryException) {
            when (ex.code) {
                404 -> null
                else -> throw CatalogServiceException("Failed to fetch table: $tableRef", ex)
            }
        }
    }

    override fun getSampleData(tableRef: String, limit: Int): List<Map<String, Any>> {
        val (projectId, datasetId, tableId) = parseTableRef(tableRef)
        val query = "SELECT * FROM `$projectId.$datasetId.$tableId` LIMIT $limit"

        val queryConfig = QueryJobConfiguration.newBuilder(query)
            .setUseLegacySql(false)
            .build()

        val results = bigQuery.query(queryConfig)

        return results.iterateAll().map { row ->
            row.schema.fields.associate { field ->
                field.name to (row.get(field.name)?.value ?: "null")
            }
        }
    }

    private fun detectPII(columnName: String, description: String?): Boolean {
        val text = "$columnName ${description ?: ""}".lowercase()
        return PII_PATTERNS.any { pattern -> text.contains(pattern) }
    }

    private fun extractTags(labels: Map<String, String>?): Set<String> {
        return labels?.entries
            ?.filter { it.key.startsWith("tag_") }
            ?.map { "${it.key.removePrefix("tag_")}::${it.value}" }
            ?.toSet() ?: emptySet()
    }

    private fun parseTableRef(tableRef: String): Triple<String, String, String> {
        val parts = tableRef.split(".")
        if (parts.size != 3) {
            throw InvalidTableReferenceException(tableRef)
        }
        return Triple(parts[0], parts[1], parts[2])
    }

    private fun generateBasecampUrl(tableRef: String): String {
        return "https://basecamp.example.com/catalog/$tableRef"
    }
}
```

### 5.2 Trino Integration

```kotlin
@Component
@ConditionalOnProperty("catalog.trino.enabled", havingValue = "true")
class TrinoCatalogClient(
    private val trinoClient: TrinoClient,
    @Value("\${catalog.trino.catalogs}") private val catalogs: List<String>,
) : CatalogRepository {

    companion object {
        private val logger = LoggerFactory.getLogger(TrinoCatalogClient::class.java)
    }

    override fun listTables(filters: CatalogFilters): List<TableInfo> {
        val targetCatalogs = filters.project?.let { listOf(it) } ?: catalogs

        return targetCatalogs.flatMap { catalog ->
            val query = buildListQuery(catalog, filters)
            trinoClient.execute(query).map { row ->
                TableInfo(
                    name = "${row["table_catalog"]}.${row["table_schema"]}.${row["table_name"]}",
                    engine = "trino",
                    owner = row["owner"]?.toString() ?: "unknown",
                    team = row["team"]?.toString(),
                    tags = emptySet(),
                    rowCount = null,
                    lastUpdated = null,
                )
            }
        }
    }

    private fun buildListQuery(catalog: String, filters: CatalogFilters): String {
        val whereConditions = mutableListOf("table_catalog = '$catalog'")

        filters.dataset?.let { whereConditions.add("table_schema = '$it'") }
        filters.owner?.let { whereConditions.add("owner LIKE '%$it%'") }

        return """
            SELECT table_catalog, table_schema, table_name, owner, team
            FROM $catalog.information_schema.tables
            WHERE ${whereConditions.joinToString(" AND ")}
            LIMIT ${filters.limit}
            OFFSET ${filters.offset}
        """.trimIndent()
    }

    override fun getTableDetail(tableRef: String): TableDetail? {
        val (catalog, schema, table) = parseTableRef(tableRef)

        val columnsQuery = """
            SELECT column_name, data_type, comment
            FROM $catalog.information_schema.columns
            WHERE table_catalog = '$catalog'
              AND table_schema = '$schema'
              AND table_name = '$table'
            ORDER BY ordinal_position
        """.trimIndent()

        val columns = trinoClient.execute(columnsQuery).map { row ->
            ColumnInfo(
                name = row["column_name"].toString(),
                dataType = row["data_type"].toString(),
                description = row["comment"]?.toString(),
                isPii = detectPII(row["column_name"].toString(), row["comment"]?.toString()),
            )
        }

        if (columns.isEmpty()) return null

        return TableDetail(
            name = tableRef,
            engine = "trino",
            owner = "unknown",
            team = null,
            columns = columns,
        )
    }

    // ... other implementations
}
```

### 5.3 Composite Catalog Service

```kotlin
@Service
class CatalogService(
    private val bigQueryClient: BigQueryCatalogClient,
    private val trinoClient: TrinoCatalogClient?,
    private val piiMaskingService: PIIMaskingService,
    private val cacheService: CatalogCacheService,
    @Value("\${catalog.default-engine:bigquery}") private val defaultEngine: String,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(CatalogService::class.java)
    }

    fun listTables(filters: CatalogFilters): List<TableInfo> {
        val cacheKey = "tables:list:${filters.hashCode()}"
        val cached = cacheService.getCachedTableList(cacheKey)
        if (cached != null) {
            logger.debug("Cache hit for table list: {}", cacheKey)
            return cached
        }

        val tables = getClient(defaultEngine).listTables(filters)
        cacheService.cacheTableList(cacheKey, tables)

        return tables
    }

    fun searchTables(keyword: String, project: String?, limit: Int): List<TableInfo> {
        val cacheKey = "tables:search:$keyword:$project:$limit"
        val cached = cacheService.getCachedSearchResult(cacheKey)
        if (cached != null) return cached

        val results = getClient(defaultEngine).searchTables(keyword, project, limit)
        cacheService.cacheSearchResult(cacheKey, results)

        return results
    }

    fun getTableDetail(tableRef: String, includeSample: Boolean): TableDetail {
        val cacheKey = "tables:detail:$tableRef"
        val cached = cacheService.getCachedTableDetail(cacheKey)

        val detail = cached ?: run {
            val fetched = getClient(defaultEngine).getTableDetail(tableRef)
                ?: throw TableNotFoundException(tableRef)
            cacheService.cacheTableDetail(cacheKey, fetched)
            fetched
        }

        return if (includeSample) {
            val rawSample = getClient(defaultEngine).getSampleData(tableRef, 10)
            val maskedSample = piiMaskingService.maskSampleData(detail.columns, rawSample)
            detail.copy(sampleData = maskedSample)
        } else {
            detail
        }
    }

    private fun getClient(engine: String): CatalogRepository {
        return when (engine) {
            "bigquery" -> bigQueryClient
            "trino" -> trinoClient ?: throw UnsupportedEngineException("trino")
            else -> throw UnsupportedEngineException(engine)
        }
    }
}
```

---

## 6. PII Masking Policy

### 6.1 Policy Overview

**Requirement**: Columns with `is_pii: true` MUST have their sample data values masked with `***`

### 6.2 PII Detection Rules

| Category | Column Name Patterns | Description |
|----------|---------------------|-------------|
| **Identity** | `email`, `phone`, `ssn`, `passport`, `driver_license` | Personal identifiers |
| **Name** | `name`, `first_name`, `last_name`, `full_name` | Personal names |
| **Address** | `address`, `street`, `city`, `zip`, `postal` | Location data |
| **Financial** | `credit_card`, `bank_account`, `salary`, `income` | Financial data |
| **Health** | `medical`, `health`, `diagnosis`, `prescription` | Health information |
| **Behavioral** | `ip_address`, `device_id`, `session_id` | Tracking identifiers |

### 6.3 Implementation

```kotlin
@Service
class PIIMaskingService(
    @Value("\${catalog.pii.patterns}") private val additionalPatterns: List<String> = emptyList(),
) {
    companion object {
        private val DEFAULT_PII_PATTERNS = listOf(
            // Identity
            "email", "phone", "mobile", "ssn", "social_security",
            "passport", "driver_license", "national_id",
            // Name
            "name", "first_name", "last_name", "full_name", "username",
            // Address
            "address", "street", "city", "zip", "postal", "state", "country",
            // Financial
            "credit_card", "bank_account", "routing", "salary", "income", "payment",
            // Health
            "medical", "health", "diagnosis", "prescription", "insurance",
            // Behavioral
            "ip_address", "device_id", "session_id", "cookie", "fingerprint",
            // Demographics
            "birth", "dob", "age", "gender", "race", "ethnicity", "religion",
        )
        private const val MASK_VALUE = "***"
    }

    private val piiPatterns: Set<String> by lazy {
        (DEFAULT_PII_PATTERNS + additionalPatterns).map { it.lowercase() }.toSet()
    }

    fun maskSampleData(
        columns: List<ColumnInfo>,
        sampleData: List<Map<String, Any>>,
    ): List<Map<String, Any>> {
        val piiColumns = columns
            .filter { it.isPii }
            .map { it.name }
            .toSet()

        return sampleData.map { row ->
            row.mapValues { (columnName, value) ->
                if (columnName in piiColumns) MASK_VALUE else value
            }
        }
    }

    fun detectPII(columnName: String, description: String?): Boolean {
        val text = "$columnName ${description ?: ""}".lowercase()
        return piiPatterns.any { pattern ->
            text.contains(pattern) || text.matches(Regex(".*\\b$pattern\\b.*"))
        }
    }

    fun getPIIColumns(columns: List<ColumnInfo>): List<String> {
        return columns.filter { it.isPii }.map { it.name }
    }
}
```

### 6.4 Configuration

```yaml
# application.yaml
catalog:
  pii:
    enabled: true
    mask-value: "***"
    patterns:
      - custom_sensitive_field
      - proprietary_id
```

---

## 7. Caching Strategy

### 7.1 Cache Configuration

| Cache Key Pattern | TTL | Purpose |
|-------------------|-----|---------|
| `catalog:list:{filters_hash}` | 15 minutes | Table list results |
| `catalog:search:{keyword}:{project}:{limit}` | 5 minutes | Search results |
| `catalog:detail:{table_ref}` | 30 minutes | Table details (without sample) |
| `catalog:columns:{table_ref}` | 1 hour | Column metadata |

### 7.2 Implementation

```kotlin
@Service
class CatalogCacheService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val TABLE_LIST_TTL = Duration.ofMinutes(15)
        private val SEARCH_RESULT_TTL = Duration.ofMinutes(5)
        private val TABLE_DETAIL_TTL = Duration.ofMinutes(30)
        private val COLUMN_METADATA_TTL = Duration.ofHours(1)
        private val logger = LoggerFactory.getLogger(CatalogCacheService::class.java)
    }

    fun cacheTableList(key: String, tables: List<TableInfo>) {
        try {
            val json = objectMapper.writeValueAsString(tables)
            redisTemplate.opsForValue().set(key, json, TABLE_LIST_TTL)
            logger.debug("Cached table list: {} ({} items)", key, tables.size)
        } catch (ex: Exception) {
            logger.warn("Failed to cache table list: {}", key, ex)
        }
    }

    fun getCachedTableList(key: String): List<TableInfo>? {
        return try {
            redisTemplate.opsForValue().get(key)?.let { json ->
                objectMapper.readValue(json, object : TypeReference<List<TableInfo>>() {})
            }
        } catch (ex: Exception) {
            logger.warn("Failed to read cached table list: {}", key, ex)
            null
        }
    }

    fun cacheTableDetail(key: String, detail: TableDetail) {
        try {
            val json = objectMapper.writeValueAsString(detail)
            redisTemplate.opsForValue().set(key, json, TABLE_DETAIL_TTL)
            logger.debug("Cached table detail: {}", key)
        } catch (ex: Exception) {
            logger.warn("Failed to cache table detail: {}", key, ex)
        }
    }

    fun getCachedTableDetail(key: String): TableDetail? {
        return try {
            redisTemplate.opsForValue().get(key)?.let { json ->
                objectMapper.readValue(json, TableDetail::class.java)
            }
        } catch (ex: Exception) {
            logger.warn("Failed to read cached table detail: {}", key, ex)
            null
        }
    }

    fun cacheSearchResult(key: String, results: List<TableInfo>) {
        try {
            val json = objectMapper.writeValueAsString(results)
            redisTemplate.opsForValue().set(key, json, SEARCH_RESULT_TTL)
        } catch (ex: Exception) {
            logger.warn("Failed to cache search result: {}", key, ex)
        }
    }

    fun getCachedSearchResult(key: String): List<TableInfo>? {
        return try {
            redisTemplate.opsForValue().get(key)?.let { json ->
                objectMapper.readValue(json, object : TypeReference<List<TableInfo>>() {})
            }
        } catch (ex: Exception) {
            null
        }
    }

    fun invalidateTable(tableRef: String) {
        val pattern = "catalog:*:$tableRef*"
        val keys = redisTemplate.keys(pattern)
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
            logger.info("Invalidated {} cache entries for table: {}", keys.size, tableRef)
        }
    }

    fun invalidateAll() {
        val keys = redisTemplate.keys("catalog:*")
        if (keys.isNotEmpty()) {
            redisTemplate.delete(keys)
            logger.info("Invalidated all {} catalog cache entries", keys.size)
        }
    }
}
```

### 7.3 Cache Invalidation Events

```kotlin
@Component
class CatalogCacheInvalidator(
    private val cacheService: CatalogCacheService,
) {
    @EventListener
    fun onTableMetadataUpdated(event: TableMetadataUpdatedEvent) {
        cacheService.invalidateTable(event.tableRef)
    }

    @Scheduled(cron = "0 0 * * * *") // Every hour
    fun scheduledCacheRefresh() {
        // Optionally pre-warm cache for frequently accessed tables
    }
}
```

---

## 8. Testing Requirements

### 8.1 Unit Tests

```kotlin
@ExtendWith(MockitoExtension::class)
class CatalogServiceTest {

    @Mock
    private lateinit var bigQueryClient: BigQueryCatalogClient

    @Mock
    private lateinit var piiMaskingService: PIIMaskingService

    @Mock
    private lateinit var cacheService: CatalogCacheService

    @InjectMocks
    private lateinit var catalogService: CatalogService

    @Test
    fun `should list tables with filters`() {
        // Given
        val filters = CatalogFilters(project = "my-project", limit = 10)
        val expectedTables = listOf(
            TableInfo(
                name = "my-project.dataset.table1",
                engine = "bigquery",
                owner = "owner@example.com",
                team = "@data-eng",
            )
        )

        given(cacheService.getCachedTableList(any())).willReturn(null)
        given(bigQueryClient.listTables(filters)).willReturn(expectedTables)

        // When
        val result = catalogService.listTables(filters)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("my-project.dataset.table1")
        verify(cacheService).cacheTableList(any(), eq(expectedTables))
    }

    @Test
    fun `should return cached table list`() {
        // Given
        val filters = CatalogFilters(project = "my-project")
        val cachedTables = listOf(
            TableInfo(name = "cached.table", engine = "bigquery", owner = "owner@example.com", team = null)
        )

        given(cacheService.getCachedTableList(any())).willReturn(cachedTables)

        // When
        val result = catalogService.listTables(filters)

        // Then
        assertThat(result).isEqualTo(cachedTables)
        verify(bigQueryClient, never()).listTables(any())
    }

    @Test
    fun `should throw TableNotFoundException when table not found`() {
        // Given
        val tableRef = "project.dataset.nonexistent"
        given(cacheService.getCachedTableDetail(any())).willReturn(null)
        given(bigQueryClient.getTableDetail(tableRef)).willReturn(null)

        // When & Then
        assertThrows<TableNotFoundException> {
            catalogService.getTableDetail(tableRef, includeSample = false)
        }
    }
}

@ExtendWith(MockitoExtension::class)
class PIIMaskingServiceTest {

    private lateinit var piiMaskingService: PIIMaskingService

    @BeforeEach
    fun setUp() {
        piiMaskingService = PIIMaskingService()
    }

    @Test
    fun `should mask PII columns in sample data`() {
        // Given
        val columns = listOf(
            ColumnInfo(name = "user_id", dataType = "STRING", isPii = false),
            ColumnInfo(name = "email", dataType = "STRING", isPii = true),
            ColumnInfo(name = "name", dataType = "STRING", isPii = true),
            ColumnInfo(name = "country", dataType = "STRING", isPii = false),
        )
        val sampleData = listOf(
            mapOf("user_id" to "user_001", "email" to "john@example.com", "name" to "John Doe", "country" to "US"),
            mapOf("user_id" to "user_002", "email" to "jane@example.com", "name" to "Jane Doe", "country" to "UK"),
        )

        // When
        val masked = piiMaskingService.maskSampleData(columns, sampleData)

        // Then
        assertThat(masked[0]["user_id"]).isEqualTo("user_001")
        assertThat(masked[0]["email"]).isEqualTo("***")
        assertThat(masked[0]["name"]).isEqualTo("***")
        assertThat(masked[0]["country"]).isEqualTo("US")
    }

    @Test
    fun `should detect PII from column name patterns`() {
        assertThat(piiMaskingService.detectPII("email", null)).isTrue()
        assertThat(piiMaskingService.detectPII("user_email", null)).isTrue()
        assertThat(piiMaskingService.detectPII("phone_number", null)).isTrue()
        assertThat(piiMaskingService.detectPII("created_at", null)).isFalse()
        assertThat(piiMaskingService.detectPII("amount", null)).isFalse()
    }
}
```

### 8.2 Integration Tests

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CatalogControllerIntegrationTest {

    @Container
    companion object {
        val redis = RedisContainer(DockerImageName.parse("redis:7-alpine"))
    }

    @MockBean
    private lateinit var bigQueryClient: BigQuery

    @Autowired
    private lateinit var testRestTemplate: TestRestTemplate

    @DynamicPropertySource
    companion object {
        @JvmStatic
        fun redisProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.redis.host") { redis.host }
            registry.add("spring.redis.port") { redis.firstMappedPort }
        }
    }

    @Test
    fun `GET catalog tables should return table list with filters`() {
        // Given
        mockBigQueryWithTables()

        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/catalog/tables?project=my-project&limit=10",
            Array<TableInfo>::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).isNotEmpty
    }

    @Test
    fun `GET catalog table detail should mask PII in sample data`() {
        // Given
        val tableRef = "my-project.dataset.users"
        mockBigQueryTableWithPII(tableRef)

        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/catalog/tables/$tableRef?include_sample=true",
            TableDetail::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val sampleData = response.body!!.sampleData
        assertThat(sampleData).isNotEmpty
        assertThat(sampleData[0]["email"]).isEqualTo("***")
        assertThat(sampleData[0]["user_id"]).isNotEqualTo("***")
    }

    @Test
    fun `GET catalog table should return 404 for nonexistent table`() {
        // Given
        val tableRef = "project.dataset.nonexistent"
        mockBigQueryTableNotFound(tableRef)

        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/catalog/tables/$tableRef",
            ErrorResponse::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.error?.code).isEqualTo("TABLE_NOT_FOUND")
    }

    @Test
    fun `GET catalog search should return matching tables`() {
        // Given
        mockBigQueryWithTables()

        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/catalog/search?keyword=user&limit=20",
            Array<TableInfo>::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.all { it.matchContext != null }).isTrue()
    }
}
```

### 8.3 Performance Tests

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

@Test
fun `cached catalog list should respond within 100ms`() {
    // Given
    val filters = CatalogFilters(project = "my-project")
    catalogService.listTables(filters) // Prime cache

    // When
    val startTime = System.currentTimeMillis()
    catalogService.listTables(filters) // Hit cache
    val duration = System.currentTimeMillis() - startTime

    // Then
    assertThat(duration).isLessThan(100) // 100ms for cache hit
}
```

### 8.4 CLI Integration Tests

```bash
# Test catalog browsing
dli catalog list --project my-project --server-url http://localhost:8081
dli catalog list --owner data-team@example.com --format json --server-url http://localhost:8081
dli catalog list --tags tier::critical,pii --server-url http://localhost:8081

# Test catalog search
dli catalog search user --server-url http://localhost:8081
dli catalog search "click event" --project my-project --server-url http://localhost:8081

# Test catalog get
dli catalog get my-project.analytics.users --server-url http://localhost:8081
dli catalog get my-project.analytics.users --sample --server-url http://localhost:8081
dli catalog get my-project.analytics.users --queries --server-url http://localhost:8081

# Verify PII masking
dli catalog get my-project.analytics.users --sample --format json --server-url http://localhost:8081 | jq '.sample_data[0].email'
# Expected output: "***"
```

---

## 9. Related Documents

### 9.1 Internal Documentation

| Document | Description |
|----------|-------------|
| [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) | Phase 2 implementation timeline |
| [`BASECAMP_OVERVIEW.md`](./BASECAMP_OVERVIEW.md) | Architecture overview |
| [`ERROR_CODES.md`](./ERROR_CODES.md) | Error handling patterns |
| [`INTEGRATION_PATTERNS.md`](./INTEGRATION_PATTERNS.md) | Spring Boot + Kotlin patterns |
| [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md) | CLI to API endpoint mapping |
| [`archive/P1_HIGH_APIS.md`](./archive/P1_HIGH_APIS.md) | P1 API specifications |

### 9.2 External References

| Resource | URL |
|----------|-----|
| BigQuery API Reference | https://cloud.google.com/bigquery/docs/reference/rest |
| Trino Documentation | https://trino.io/docs/current/ |
| Spring Boot 4.0 | https://docs.spring.io/spring-boot/docs/current/reference/html/ |

### 9.3 Related Features

| Feature | Dependency Relationship |
|---------|------------------------|
| **Lineage API** | Uses catalog metadata for node enrichment |
| **Quality API** | Provides quality scores for table details |
| **Metrics/Datasets** | Registered resources appear in catalog search |

---

## Appendix: Exception Classes

```kotlin
// module-core-domain/src/main/kotlin/domain/catalog/exception/

class TableNotFoundException(tableRef: String) : BasecampException(
    "Table '$tableRef' not found in catalog"
) {
    override val errorCode = "TABLE_NOT_FOUND"
    override val httpStatus = HttpStatus.NOT_FOUND
}

class InvalidTableReferenceException(tableRef: String) : BasecampException(
    "Invalid table reference format: '$tableRef'. Expected format: project.dataset.table"
) {
    override val errorCode = "INVALID_TABLE_REFERENCE"
    override val httpStatus = HttpStatus.BAD_REQUEST
}

class CatalogServiceException(
    message: String,
    cause: Throwable? = null,
) : BasecampException(message, cause) {
    override val errorCode = "CATALOG_SERVICE_ERROR"
    override val httpStatus = HttpStatus.BAD_GATEWAY
}

class CatalogTimeoutException(tableRef: String, timeoutSeconds: Int) : BasecampException(
    "Catalog operation for '$tableRef' timed out after $timeoutSeconds seconds"
) {
    override val errorCode = "CATALOG_TIMEOUT"
    override val httpStatus = HttpStatus.GATEWAY_TIMEOUT
}

class UnsupportedEngineException(engine: String) : BasecampException(
    "Unsupported catalog engine: '$engine'"
) {
    override val errorCode = "UNSUPPORTED_ENGINE"
    override val httpStatus = HttpStatus.BAD_REQUEST
}
```

---

*This document provides implementation-ready specifications for the Catalog API, enabling data discovery functionality with BigQuery/Trino integration and PII protection.*

---

## Appendix C: Review Feedback

> **Reviewed by:** feature-basecamp-server Agent | **Date:** 2026-01-01 | **Rating:** 4.5/5

### Strengths
- Excellent external integration patterns for BigQuery and Trino
- Well-designed PII masking policy with configurable patterns
- Comprehensive caching strategy with proper TTL configuration
- Domain model correctly uses data classes (not JPA entities for external data)
- Exception classes use `httpStatus` property correctly

### Issues to Address

| Priority | Issue | Recommendation |
|----------|-------|----------------|
| **Medium** | Domain models don't follow `*Dto` suffix convention | Rename to `TableInfoDto`, `ColumnInfoDto`, etc. OR clarify as domain value objects |
| **Medium** | `CatalogService` mixes caching logic with business logic | Consider extracting to `CachingCatalogService` decorator |
| **Low** | Missing `@Repository` bean naming for infrastructure implementations | Add `@Repository("catalogRepository")` annotations |
| **Low** | Missing controller implementation pattern section | Add controller implementation example |

### Required Changes Before Implementation
1. Add `@Repository("beanName")` annotations to implementations
2. Consider extracting caching to separate decorator
3. Add controller layer implementation pattern for completeness
