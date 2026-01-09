# Project Basecamp Server - Code Structure & Architecture

## 1. Current Controller Structure (module-server-api)

### API Pattern
All controllers follow REST conventions:
- **MetricController**: `POST /api/v1/metrics/{name}/run` → RunMetricRequest → MetricExecutionResultDto
- **DatasetController**: `POST /api/v1/datasets/{name}/run` → ExecuteDatasetRequest → ExecutionResultDto  
- **QualityController** (deprecated): `POST /api/v1/quality/test/{resource_name}` → ExecuteQualityTestRequest → QualityRunResultDto
- **RunController**: `POST /api/v1/run/execute` → ExecuteSqlRequest → ExecutionResultResponseDto

### New Pattern Proposed for Quality API
Change from `/test/{resource_name}` to `/{name}/run` to match Metric/Dataset pattern:
- `POST /api/v1/quality/{name}/run` → QualityRunRequest → QualityRunResultDto
- Where {name} = quality spec name (not resource name)

## 2. DTO Patterns

### Execution Request DTOs
Common structure across services:
```kotlin
// Metric
data class RunMetricRequest(
    val parameters: Map<String, Any> = emptyMap(),
    val limit: Int? = null,           // 1-10000
    val timeout: Int = 300,            // 1-3600 seconds
)

// Dataset
data class ExecuteDatasetRequest(
    val parameters: Map<String, Any> = emptyMap(),
    val limit: Int = 1000,
    val timeout: Int = 600,
)

// Quality (current - deprecated)
data class ExecuteQualityTestRequest(
    val qualitySpecName: String? = null,
    val testNames: List<String> = emptyList(),
    val timeout: Int = 300,
    val executedBy: String? = null,
)

// Suggested new Quality pattern
data class QualityRunRequest(
    val testNames: List<String>? = null,  // Optional filter for specific tests
    val timeout: Int = 300,
    val executedBy: String? = null,
    val parameters: Map<String, Any> = emptyMap(),  // For template parameters if needed
)
```

### Execution Response DTOs
```kotlin
// Metric result
data class MetricExecutionResultDto(
    val rows: List<Map<String, Any>>,
    val rowCount: Int,
    val durationSeconds: Double,
    val renderedSql: String,
)

// Quality run result
data class QualityRunResultDto(
    val runId: String,
    val resourceName: String,
    val qualitySpecName: String,
    val status: String,                // "PENDING", "RUNNING", "SUCCESS", "FAILED", "STOPPED", "TIMEOUT"
    val overallStatus: String?,        // "PASSED", "FAILED", null if running
    val passedTests: Int,
    val failedTests: Int,
    val totalTests: Int,
    val durationSeconds: Double?,
    val startedAt: Instant,
    val completedAt: Instant?,
    val executedBy: String,
    val testResults: List<TestResultSummaryDto> = emptyList(),
)
```

## 3. Service Layer Architecture

### QualityService Methods
- `getQualitySpecs()` - List specs with filters
- `getQualitySpec(name)` - Get by name
- `executeQualityTests()` - Main execution method (currently handles v2.0 workflow)
  - Creates QualityRunEntity with WorkflowRunStatus
  - Executes individual tests
  - Tracks pass/fail counts
- `runQuality()` - Delegates to ExecutionService
- `getQualityRuns()` - Historical queries
- `getQualityRun()` - Get specific run

### MetricService Methods  
- `listMetrics()` - List with filters
- `getMetricOrThrow()` - Get by name
- `executeMetric()` - Executes metric SQL
  - Returns MetricExecutionProjection
  - Mock execution currently
  
### ExecutionService Methods
- `executeDataset()` - Full implementation with:
  - Parser client for SQL transpilation
  - Query engine client for execution
  - History & result storage
  - v2.0 Execution workflow
- `executeQuality()` - Placeholder for phase 3
- `executeRawSql()` - Ad-hoc SQL execution

## 4. Entity & Repository Structure

### QualityRunEntity (v2.0)
- Fields: runId, qualitySpecId, specName (denormalized), targetResource, targetResourceType
- Status: WorkflowRunStatus enum (PENDING, RUNNING, SUCCESS, FAILED, STOPPED, TIMEOUT)
- RunType: WorkflowRunType enum (MANUAL, SCHEDULED, BACKFILL)
- Tracking: triggeredBy, startedAt, endedAt, stoppedBy, stoppedAt
- Metrics: totalTests, passedTests, failedTests
- Methods: start(), complete(), fail(), stop(), getDurationSeconds(), etc.

### Repositories
Domain interfaces (in module-core-domain):
- QualitySpecRepositoryJpa/Dsl
- QualityRunRepositoryJpa
- QualityTestRepositoryJpa
- ExecutionHistoryRepositoryJpa/Dsl

## 5. Mapper Patterns (QualityMapper)

```kotlin
// Entity → List DTO
fun toSummaryDto(entity: QualitySpecEntity): QualitySpecSummaryDto

// Entity → Detail DTO  
fun toDetailDto(
    entity: QualitySpecEntity,
    tests: List<QualityTestEntity>,
    recentRuns: List<QualityRunEntity>,
): QualitySpecDetailDto

// Entity → Run Result DTO (for responses)
fun toRunResultDto(
    entity: QualityRunEntity,
    testResults: List<TestResultSummaryDto>,
): QualityRunResultDto

// Key field mappings:
// targetResource → resourceName
// endedAt → completedAt
// triggeredBy → executedBy
// WorkflowRunStatus.RUNNING/PENDING → overallStatus: null
// failedTests > 0 → overallStatus: "FAILED"
// passedTests > 0 → overallStatus: "PASSED"
```

## 6. Request/Response Patterns

### Common Annotations
```kotlin
// Validation
@NotBlank, @Email, @Pattern, @Size, @Min, @Max
@Valid @RequestBody

// Jackson
@JsonProperty (snake_case to camelCase)
@JsonFormat (date formatting with pattern/timezone)
@JsonInclude(JsonInclude.Include.NON_NULL)

// OpenAPI
@Operation(summary, description)
@SwaggerApiResponse(responseCode, description)
@Parameter(description)
@PathVariable, @RequestParam, @RequestBody
```

### Response Headers
- HTTP 200 OK for success
- HTTP 201 CREATED for resource creation
- HTTP 404 NOT_FOUND for missing resources
- HTTP 400 BAD_REQUEST for validation errors
- HTTP 408 REQUEST_TIMEOUT for execution timeout
- HTTP 409 CONFLICT for conflicts (e.g., already exists)

## 7. Key Differences from Metric/Dataset Pattern

Current Quality API:
- Uses `/test/{resource_name}` (resource-oriented)
- ExecuteQualityTestRequest lacks standard limit/timeout structure
- Response includes test results inline

Proposed Quality API (v2.0):
- Use `/{name}/run` (spec-oriented, consistent with Metric/Dataset)
- Use QualityRunRequest with standard timeout/parameters
- Keep test results in separate field for consistency
- Map new entity fields to existing DTO structure via mapper
