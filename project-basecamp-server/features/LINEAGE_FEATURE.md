# Lineage API Feature Specification

> **Version:** 1.0.0 | **Status:** ✅ Implemented | **Priority:** P1 High
> **CLI Commands:** `dli lineage show/upstream/downstream` | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Completed:** 2026-01-03 | **Release Documentation:** [`LINEAGE_RELEASE.md`](./LINEAGE_RELEASE.md)

> **📦 Data Source:** ✅ Self-managed JPA (lineage 저장) + ✅ External API (SQL 파싱)
> **Entities:** `LineageNodeEntity`, `LineageEdgeEntity`
> **External:** `SQLLineageParser` → basecamp-parser `/api/v1/parse/dependencies`

---

## 1. Overview

### 1.1 Purpose

The Lineage API provides table-level dependency tracking for datasets and metrics, enabling data engineers and analysts to understand data flow within the platform. It answers two fundamental questions:

1. **Upstream (What does this depend on?):** Identify all data sources that feed into a resource
2. **Downstream (What depends on this?):** Identify all consumers of a resource

### 1.2 Key Features

| Feature | Description |
|---------|-------------|
| **Graph Traversal** | BFS-based traversal with configurable depth |
| **Bidirectional Query** | Support for upstream, downstream, or both directions |
| **Multi-type Support** | Track lineage across Datasets, Metrics, External Tables |
| **SQL Dependency Parsing** | Automatic extraction of dependencies from SQL queries |

### 1.3 Scope

**In Scope:**
- Table-level lineage (dataset → dataset, metric → dataset)
- Server-based lineage lookup for registered resources
- Automatic SQL dependency extraction during resource registration
- Graph traversal with depth limiting

**Out of Scope (Future):**
- Column-level lineage
- Real-time lineage updates
- External system lineage (Airflow DAGs, dbt models)

---

## 2. CLI Command Mapping

### 2.1 Command Overview

| CLI Command | HTTP Method | API Endpoint | Description |
|-------------|-------------|--------------|-------------|
| `dli lineage show <name>` | GET | `/api/v1/lineage/{resource_name}?direction=both` | Full lineage graph |
| `dli lineage upstream <name>` | GET | `/api/v1/lineage/{resource_name}?direction=upstream` | Upstream dependencies |
| `dli lineage downstream <name>` | GET | `/api/v1/lineage/{resource_name}?direction=downstream` | Downstream dependents |

### 2.2 CLI Parameter Mapping

| CLI Flag | API Parameter | Type | Default | Description |
|----------|---------------|------|---------|-------------|
| `--depth <n>`, `-d` | `depth` | int | -1 | Traversal depth (-1 = unlimited) |
| `--format <fmt>`, `-f` | N/A | string | table | Output format (table/json) |
| `--path <dir>`, `-p` | N/A | Path | Current dir | Project path |

### 2.3 CLI Usage Examples

```bash
# Show full lineage (both directions)
dli lineage show iceberg.analytics.daily_clicks

# Show upstream dependencies only
dli lineage upstream iceberg.analytics.daily_clicks --depth 3

# Show downstream dependents with JSON output
dli lineage downstream iceberg.analytics.daily_clicks --format json

# Limit traversal depth
dli lineage show iceberg.analytics.daily_clicks --depth 2
```

### 2.4 CLI Output Format

**Tree Format (default):**
```
Upstream (depends on)
└── iceberg.raw.clicks (Dataset)
    └── external.source.clickstream (External)

Resource: iceberg.analytics.daily_clicks
  Type: Dataset
  Owner: engineer@example.com
  Team: @data-eng

Downstream (depended by)
└── iceberg.reporting.clicks_report (Dataset)
    └── iceberg.bi.weekly_summary (Metric)

Summary: 2 upstream, 2 downstream
```

**JSON Format:**
```json
{
  "root": {"name": "iceberg.analytics.daily_clicks", "type": "Dataset"},
  "nodes": [...],
  "edges": [...],
  "summary": {"total_upstream": 2, "total_downstream": 2}
}
```

---

## 3. API Specifications

### 3.1 Get Resource Lineage

#### `GET /api/v1/lineage/{resource_name}`

**Purpose:** Retrieve lineage graph for a resource (dataset, metric, or table)

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `resource_name` | string | Yes | Fully qualified resource name (e.g., `iceberg.analytics.daily_clicks`) |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `direction` | string | No | `both` | `upstream`, `downstream`, or `both` |
| `depth` | int | No | -1 | Max traversal depth (-1 = unlimited) |

**Request Example:**
```http
GET /api/v1/lineage/iceberg.analytics.daily_clicks?direction=both&depth=-1
Accept: application/json
Authorization: Bearer <oauth2-token>
```

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

**Error Responses:**

| Status | Error Code | Description |
|--------|------------|-------------|
| 404 | `LINEAGE_RESOURCE_NOT_FOUND` | Resource does not exist |
| 400 | `LINEAGE_INVALID_DIRECTION` | Invalid direction parameter |
| 400 | `LINEAGE_INVALID_DEPTH` | Depth must be -1 or positive integer |
| 500 | `LINEAGE_GRAPH_ERROR` | Graph traversal failed |

---

## 4. Domain Model

### 4.1 Entity Diagram

```
┌─────────────────┐         ┌─────────────────┐
│  LineageNode    │         │  LineageEdge    │
├─────────────────┤         ├─────────────────┤
│ name: String    │────────→│ source: String  │
│ type: NodeType  │         │ target: String  │
│ owner: String?  │←────────│ edgeType: Type  │
│ team: String?   │         │ createdAt: Date │
│ description?    │         └─────────────────┘
│ tags: List      │
│ depth: Int      │
└─────────────────┘
         ↓
┌─────────────────┐
│  LineageGraph   │
├─────────────────┤
│ root: Node      │
│ nodes: List     │
│ edges: List     │
│ totalUpstream   │
│ totalDownstream │
└─────────────────┘
```

### 4.2 Domain Entities

```kotlin
// module-core-domain/entity/LineageNodeEntity.kt
@Entity
@Table(name = "lineage_nodes")
class LineageNodeEntity(
    @Id
    val name: String,  // Fully qualified name (PK)

    @Enumerated(EnumType.STRING)
    val type: LineageNodeType,

    val owner: String? = null,
    val team: String? = null,
    val description: String? = null,

    @ElementCollection
    @CollectionTable(name = "lineage_node_tags")
    val tags: Set<String> = emptySet(),

    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
)

enum class LineageNodeType {
    DATASET,   // Registered datasets
    METRIC,    // Registered metrics
    TABLE,     // External tables (discovered)
    VIEW       // Database views
}
```

```kotlin
// module-core-domain/entity/LineageEdgeEntity.kt
@Entity
@Table(
    name = "lineage_edges",
    uniqueConstraints = [UniqueConstraint(columnNames = ["source", "target"])]
)
class LineageEdgeEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    val source: String,  // Upstream node name

    @Column(nullable = false)
    val target: String,  // Downstream node name

    @Enumerated(EnumType.STRING)
    val edgeType: LineageEdgeType = LineageEdgeType.DIRECT,

    val createdAt: LocalDateTime = LocalDateTime.now(),
)

enum class LineageEdgeType {
    DIRECT,    // Explicit SQL dependency (FROM, JOIN)
    INDIRECT,  // Inferred through shared columns/tables
    MANUAL     // Manually declared dependency
}
```

### 4.3 DTOs

```kotlin
// module-server-api/dto/LineageDto.kt
data class LineageNodeDto(
    val name: String,
    val type: String,
    val owner: String?,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    val depth: Int,  // -N = upstream, +N = downstream, 0 = root
)

data class LineageEdgeDto(
    val source: String,
    val target: String,
    val edgeType: String,
)

data class LineageGraphDto(
    val root: LineageNodeDto,
    val nodes: List<LineageNodeDto>,
    val edges: List<LineageEdgeDto>,
    val totalUpstream: Int,
    val totalDownstream: Int,
)
```

---

## 5. Graph Storage Options

### 5.1 Option A: In-Memory Graph (MVP - Recommended for Phase 1)

**Pros:**
- Simple implementation, no additional infrastructure
- Fast traversal for small-medium graphs (< 10,000 nodes)
- Easy to test and debug

**Cons:**
- Memory consumption grows with graph size
- Lost on server restart (requires rebuild)
- Not suitable for distributed deployments

**Implementation:**

```kotlin
@Component
class InMemoryLineageStorage : LineageGraphStorage {
    // Adjacency lists for bidirectional traversal
    private val upstreamEdges = ConcurrentHashMap<String, MutableSet<LineageEdge>>()
    private val downstreamEdges = ConcurrentHashMap<String, MutableSet<LineageEdge>>()
    private val nodes = ConcurrentHashMap<String, LineageNode>()

    override fun storeLineage(resource: String, dependencies: List<LineageEdge>) {
        dependencies.forEach { edge ->
            upstreamEdges.getOrPut(edge.target) { ConcurrentHashMap.newKeySet() }.add(edge)
            downstreamEdges.getOrPut(edge.source) { ConcurrentHashMap.newKeySet() }.add(edge)
        }
    }

    override fun getLineageGraph(
        resource: String,
        direction: LineageDirection,
        depth: Int,
    ): LineageGraph {
        val visited = mutableSetOf<String>()
        val resultNodes = mutableListOf<LineageNode>()
        val resultEdges = mutableListOf<LineageEdge>()

        // BFS traversal
        when (direction) {
            LineageDirection.UPSTREAM -> traverseUpstream(resource, depth, 0, visited, resultNodes, resultEdges)
            LineageDirection.DOWNSTREAM -> traverseDownstream(resource, depth, 0, visited, resultNodes, resultEdges)
            LineageDirection.BOTH -> {
                traverseUpstream(resource, depth, 0, visited.toMutableSet(), resultNodes, resultEdges)
                traverseDownstream(resource, depth, 0, visited.toMutableSet(), resultNodes, resultEdges)
            }
        }

        return LineageGraph(
            root = nodes[resource] ?: LineageNode(name = resource),
            nodes = resultNodes,
            edges = resultEdges,
        )
    }

    private fun traverseUpstream(
        node: String,
        maxDepth: Int,
        currentDepth: Int,
        visited: MutableSet<String>,
        nodes: MutableList<LineageNode>,
        edges: MutableList<LineageEdge>,
    ) {
        if (node in visited) return
        if (maxDepth != -1 && currentDepth > maxDepth) return
        visited.add(node)

        upstreamEdges[node]?.forEach { edge ->
            edges.add(edge)
            this.nodes[edge.source]?.let { sourceNode ->
                nodes.add(sourceNode.copy(depth = -(currentDepth + 1)))
            }
            traverseUpstream(edge.source, maxDepth, currentDepth + 1, visited, nodes, edges)
        }
    }

    private fun traverseDownstream(
        node: String,
        maxDepth: Int,
        currentDepth: Int,
        visited: MutableSet<String>,
        nodes: MutableList<LineageNode>,
        edges: MutableList<LineageEdge>,
    ) {
        if (node in visited) return
        if (maxDepth != -1 && currentDepth > maxDepth) return
        visited.add(node)

        downstreamEdges[node]?.forEach { edge ->
            edges.add(edge)
            this.nodes[edge.target]?.let { targetNode ->
                nodes.add(targetNode.copy(depth = currentDepth + 1))
            }
            traverseDownstream(edge.target, maxDepth, currentDepth + 1, visited, nodes, edges)
        }
    }
}
```

### 5.2 Option B: Neo4j Graph Database (Future - Large Scale)

**Pros:**
- Optimized for graph traversal queries
- Handles millions of nodes efficiently
- Native Cypher query language for complex lineage queries

**Cons:**
- Additional infrastructure to manage
- Learning curve for Cypher
- More complex deployment

**Future Implementation (Reference):**

```kotlin
@Component
@Profile("neo4j")
class Neo4jLineageStorage(
    private val driver: Driver,
) : LineageGraphStorage {

    override fun getLineageGraph(
        resource: String,
        direction: LineageDirection,
        depth: Int,
    ): LineageGraph {
        val cypherQuery = when (direction) {
            LineageDirection.UPSTREAM -> """
                MATCH path = (n:Resource {name: ${'$'}resource})<-[:DEPENDS_ON*1..${depth.coerceAtLeast(1)}]-(upstream)
                RETURN nodes(path), relationships(path)
            """.trimIndent()
            LineageDirection.DOWNSTREAM -> """
                MATCH path = (n:Resource {name: ${'$'}resource})-[:DEPENDS_ON*1..${depth.coerceAtLeast(1)}]->(downstream)
                RETURN nodes(path), relationships(path)
            """.trimIndent()
            LineageDirection.BOTH -> """
                MATCH upPath = (n:Resource {name: ${'$'}resource})<-[:DEPENDS_ON*0..]-(upstream)
                MATCH downPath = (n)-[:DEPENDS_ON*0..]->(downstream)
                RETURN nodes(upPath) + nodes(downPath), relationships(upPath) + relationships(downPath)
            """.trimIndent()
        }

        // Execute and map results
        driver.session().use { session ->
            val result = session.run(cypherQuery, mapOf("resource" to resource))
            return mapNeo4jResult(result)
        }
    }
}
```

### 5.3 Storage Strategy Decision

| Criteria | In-Memory | Neo4j |
|----------|-----------|-------|
| **Implementation Complexity** | Low | High |
| **Infrastructure Cost** | None | Additional DB |
| **Scale (nodes)** | < 10,000 | Millions |
| **Query Complexity** | Simple BFS | Complex Cypher |
| **Recommended Phase** | Phase 1 (MVP) | Phase 3+ |

**Decision:** Start with **In-Memory** storage, migrate to **Neo4j** when:
- Node count exceeds 10,000
- Complex lineage queries required (e.g., shortest path, impact analysis)
- Distributed deployment needed

---

## 6. SQL Dependency Parsing

### 6.1 Integration with basecamp-parser

The SQL dependency extraction leverages the existing `basecamp-parser` service (Python + SQLGlot) for accurate parsing.

**Parser API Call:**

```http
POST http://basecamp-parser:5000/api/v1/parse/dependencies
Content-Type: application/json

{
  "sql": "SELECT a.*, b.name FROM iceberg.raw.clicks a JOIN iceberg.dim.users b ON a.user_id = b.id",
  "dialect": "bigquery"
}
```

**Parser Response:**

```json
{
  "tables": [
    "iceberg.raw.clicks",
    "iceberg.dim.users"
  ],
  "columns": [
    {"table": "iceberg.raw.clicks", "column": "*"},
    {"table": "iceberg.dim.users", "column": "name"}
  ]
}
```

### 6.2 Service Integration

```kotlin
@Service
class SQLLineageParser(
    @Value("\${basecamp.parser.url}") private val parserUrl: String,
    private val restTemplate: RestTemplate,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(SQLLineageParser::class.java)
    }

    fun extractTableDependencies(sql: String, dialect: String = "bigquery"): List<String> {
        return try {
            val request = DependencyRequest(sql = sql, dialect = dialect)
            val response = restTemplate.postForEntity(
                "$parserUrl/api/v1/parse/dependencies",
                request,
                DependencyResponse::class.java
            )
            response.body?.tables ?: emptyList()
        } catch (e: Exception) {
            logger.warn("Failed to parse SQL for dependencies: ${e.message}")
            emptyList()
        }
    }
}

data class DependencyRequest(val sql: String, val dialect: String)
data class DependencyResponse(val tables: List<String>, val columns: List<ColumnRef>?)
data class ColumnRef(val table: String, val column: String)
```

### 6.3 Lineage Build on Registration

```kotlin
@Service
@Transactional(readOnly = true)
class LineageGraphService(
    private val lineageStorage: LineageGraphStorage,
    private val sqlParser: SQLLineageParser,
    private val nodeRepositoryJpa: LineageNodeRepositoryJpa,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(LineageGraphService::class.java)
    }

    /**
     * Build lineage from SQL during dataset/metric registration.
     * Called by DatasetService.register() and MetricService.register()
     */
    @Transactional
    fun buildLineageFromSQL(resourceName: String, resourceType: LineageNodeType, sql: String) {
        logger.info("Building lineage for resource: {}", resourceName)

        // 1. Extract dependencies from SQL
        val dependencies = sqlParser.extractTableDependencies(sql)
        logger.debug("Found {} dependencies: {}", dependencies.size, dependencies)

        // 2. Create edges (dependency -> resource)
        val edges = dependencies.map { dep ->
            LineageEdge(
                source = dep,
                target = resourceName,
                edgeType = LineageEdgeType.DIRECT
            )
        }

        // 3. Ensure nodes exist
        ensureNodeExists(resourceName, resourceType)
        dependencies.forEach { dep ->
            ensureNodeExists(dep, inferNodeType(dep))
        }

        // 4. Store edges
        lineageStorage.storeLineage(resourceName, edges)
        logger.info("Stored {} lineage edges for {}", edges.size, resourceName)
    }

    private fun ensureNodeExists(name: String, type: LineageNodeType) {
        if (nodeRepositoryJpa.findByName(name) == null) {
            val node = LineageNodeEntity(
                name = name,
                type = type,
            )
            nodeRepositoryJpa.save(node)
        }
    }

    private fun inferNodeType(tableName: String): LineageNodeType {
        // Check if it's a registered dataset or metric
        // If not found, treat as external TABLE
        return LineageNodeType.TABLE
    }
}
```

---

## 7. Testing Requirements

### 7.1 Unit Tests

```kotlin
@ExtendWith(MockitoExtension::class)
class LineageGraphServiceTest {

    @Mock
    private lateinit var lineageStorage: LineageGraphStorage

    @Mock
    private lateinit var sqlParser: SQLLineageParser

    @InjectMocks
    private lateinit var lineageService: LineageGraphService

    @Test
    fun `should extract dependencies from SQL and build lineage`() {
        // Given
        val sql = "SELECT * FROM iceberg.raw.clicks JOIN iceberg.dim.users"
        val dependencies = listOf("iceberg.raw.clicks", "iceberg.dim.users")
        given(sqlParser.extractTableDependencies(sql)).willReturn(dependencies)

        // When
        lineageService.buildLineageFromSQL("iceberg.analytics.report", LineageNodeType.DATASET, sql)

        // Then
        verify(lineageStorage).storeLineage(
            eq("iceberg.analytics.report"),
            argThat { edges ->
                edges.size == 2 &&
                edges.all { it.target == "iceberg.analytics.report" } &&
                edges.map { it.source }.containsAll(dependencies)
            }
        )
    }

    @Test
    fun `should handle SQL parsing failure gracefully`() {
        // Given
        given(sqlParser.extractTableDependencies(any())).willReturn(emptyList())

        // When
        lineageService.buildLineageFromSQL("resource", LineageNodeType.DATASET, "INVALID SQL")

        // Then
        verify(lineageStorage).storeLineage(eq("resource"), eq(emptyList()))
    }
}
```

### 7.2 Integration Tests

```kotlin
@SpringBootTest
@Testcontainers
class LineageControllerIntegrationTest {

    @Autowired
    private lateinit var testRestTemplate: TestRestTemplate

    @Autowired
    private lateinit var lineageStorage: LineageGraphStorage

    @BeforeEach
    fun setup() {
        // Setup test lineage graph
        // A -> B -> C (upstream flow)
        // B -> D (downstream flow)
        lineageStorage.storeLineage("B", listOf(
            LineageEdge(source = "A", target = "B", edgeType = LineageEdgeType.DIRECT)
        ))
        lineageStorage.storeLineage("C", listOf(
            LineageEdge(source = "B", target = "C", edgeType = LineageEdgeType.DIRECT)
        ))
        lineageStorage.storeLineage("D", listOf(
            LineageEdge(source = "B", target = "D", edgeType = LineageEdgeType.DIRECT)
        ))
    }

    @Test
    fun `should return upstream dependencies`() {
        val response = testRestTemplate.getForEntity(
            "/api/v1/lineage/C?direction=upstream",
            LineageGraphDto::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.totalUpstream).isEqualTo(2)
        assertThat(response.body!!.nodes.map { it.name }).containsExactlyInAnyOrder("A", "B")
    }

    @Test
    fun `should return downstream dependents`() {
        val response = testRestTemplate.getForEntity(
            "/api/v1/lineage/B?direction=downstream",
            LineageGraphDto::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.totalDownstream).isEqualTo(2)
        assertThat(response.body!!.nodes.map { it.name }).containsExactlyInAnyOrder("C", "D")
    }

    @Test
    fun `should respect depth limit`() {
        val response = testRestTemplate.getForEntity(
            "/api/v1/lineage/C?direction=upstream&depth=1",
            LineageGraphDto::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body!!.nodes).hasSize(1)
        assertThat(response.body!!.nodes[0].name).isEqualTo("B")
    }

    @Test
    fun `should return 404 for non-existent resource`() {
        val response = testRestTemplate.getForEntity(
            "/api/v1/lineage/non.existent.resource",
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
    }
}
```

### 7.3 CLI Integration Tests

```bash
#!/bin/bash
# test_lineage_cli.sh

SERVER_URL="http://localhost:8081"

# Test 1: Show full lineage
echo "Test 1: Show full lineage"
dli lineage show iceberg.analytics.daily_clicks --server-url $SERVER_URL
[ $? -eq 0 ] && echo "PASS" || echo "FAIL"

# Test 2: Upstream only
echo "Test 2: Upstream dependencies"
dli lineage upstream iceberg.analytics.daily_clicks --server-url $SERVER_URL
[ $? -eq 0 ] && echo "PASS" || echo "FAIL"

# Test 3: Downstream only
echo "Test 3: Downstream dependents"
dli lineage downstream iceberg.analytics.daily_clicks --depth 2 --server-url $SERVER_URL
[ $? -eq 0 ] && echo "PASS" || echo "FAIL"

# Test 4: JSON output
echo "Test 4: JSON output format"
dli lineage show iceberg.analytics.daily_clicks --format json --server-url $SERVER_URL | jq .
[ $? -eq 0 ] && echo "PASS" || echo "FAIL"

# Test 5: Non-existent resource
echo "Test 5: Non-existent resource (should fail gracefully)"
dli lineage show non.existent.resource --server-url $SERVER_URL 2>&1 | grep -q "not found"
[ $? -eq 0 ] && echo "PASS" || echo "FAIL"
```

### 7.4 Performance Tests

```kotlin
@Test
fun `lineage query should complete within 500ms for depth 5`() {
    // Given: Graph with 1000 nodes, max depth 5
    setupLargeGraph(nodeCount = 1000, maxDepth = 5)

    // When
    val startTime = System.currentTimeMillis()
    val response = testRestTemplate.getForEntity(
        "/api/v1/lineage/root.node?direction=both&depth=5",
        LineageGraphDto::class.java
    )
    val duration = System.currentTimeMillis() - startTime

    // Then
    assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
    assertThat(duration).isLessThan(500L) // 500ms SLA
}
```

---

## 8. Related Documents

### 8.1 Internal References

| Document | Description |
|----------|-------------|
| [`archive/P1_HIGH_APIS.md`](./archive/P1_HIGH_APIS.md) | P1 API specifications (Catalog + Lineage) |
| [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md) | Complete CLI to API mapping |
| [`ERROR_CODES.md`](./ERROR_CODES.md) | Error code definitions |
| [`INTEGRATION_PATTERNS.md`](./INTEGRATION_PATTERNS.md) | Spring Boot + Kotlin patterns |
| [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) | Full implementation timeline |

### 8.2 CLI Implementation References

| File | Description |
|------|-------------|
| `project-interface-cli/src/dli/commands/lineage.py` | CLI command implementation |
| `project-interface-cli/src/dli/core/lineage/__init__.py` | Domain models (LineageNode, LineageEdge, LineageResult) |
| `project-interface-cli/src/dli/core/lineage/client.py` | LineageClient for server communication |

### 8.3 External References

| Resource | URL |
|----------|-----|
| Neo4j Spring Data | https://spring.io/projects/spring-data-neo4j |
| SQLGlot Documentation | https://sqlglot.com/sqlglot.html |
| Apache Calcite (Alternative) | https://calcite.apache.org/ |

---

## Appendix: Implementation Checklist

### Phase 1 (Week 5) - ✅ Completed (2026-01-03)

- [x] Create domain entities (`LineageNodeEntity`, `LineageEdgeEntity`)
- [x] Implement RDB-based lineage storage with JPA repositories
- [x] Implement `LineageService` with BFS graph traversal
- [x] Create `LineageController` with GET /api/v1/lineage/{resource_name} endpoint
- [x] Integrate with `BasecampParserClient` (mock implementation)
- [x] Database migration V3__Add_lineage_tables.sql with sample data
- [x] Unit tests for graph traversal (35+ test scenarios)
- [x] Integration tests for API endpoint
- [x] CLI-ready API implementation

### Phase 2 (Future)

- [ ] Neo4j storage implementation
- [ ] Column-level lineage support
- [ ] Real-time lineage updates
- [ ] Impact analysis API

---

*This document provides implementation-ready specifications for the Lineage API feature, enabling data dependency tracking within the DataOps Platform.*

---

## Appendix C: Review Feedback

> **Reviewed by:** feature-basecamp-server Agent | **Date:** 2026-01-01 | **Rating:** 4.0/5

### Strengths
- Good graph storage strategy with clear migration path (In-Memory -> Neo4j)
- Excellent SQL dependency parsing integration with basecamp-parser
- Well-designed domain model with `LineageNodeEntity` and `LineageEdgeEntity`
- BFS traversal implementation is correct and efficient

### Issues to Address

| Priority | Issue | Recommendation |
|----------|-------|----------------|
| **High** | Missing controller implementation pattern | Add `LineageController` with `@GetMapping("/{resourceName}")` |
| **High** | Missing exception class definitions | Add `LineageNotFoundException` extending `BasecampException` |
| **Medium** | `InMemoryLineageStorage` has thread safety issues with mutable concurrent state | Use `CopyOnWriteArraySet` or add synchronization |
| **Medium** | Error code uses `LINEAGE_RESOURCE_NOT_FOUND` but ERROR_CODES.md shows `LINEAGE_NOT_FOUND` | Align error code naming |
| **Low** | `LineageGraphService` in domain layer has `@Transactional` - violates hexagonal pattern | Move to infrastructure or split service |

### Required Changes Before Implementation
1. Add controller implementation with proper exception handling
2. Add exception classes following `BasecampException` pattern
3. Fix thread safety for graph updates
4. Align error codes with ERROR_CODES.md
