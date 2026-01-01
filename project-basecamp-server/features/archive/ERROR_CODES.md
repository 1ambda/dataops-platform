# Error Codes & Response Handling

> **Target Audience:** Backend developers implementing error handling
> **Purpose:** Standardized error response format and CLI compatibility
> **Cross-Reference:** [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md) for CLI error code mapping

---

## 📋 Table of Contents

1. [Standard Error Response Format](#1-standard-error-response-format)
2. [HTTP Status Codes](#2-http-status-codes)
3. [Server Error Codes](#3-server-error-codes)
4. [CLI Error Code Mapping](#4-cli-error-code-mapping)
5. [Implementation Patterns](#5-implementation-patterns)

---

## 1. Standard Error Response Format

### 1.1 Error Response Structure

All error responses follow this format:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error message",
    "details": {
      "field": "Additional context",
      "value": "Invalid value"
    }
  }
}
```

### 1.2 Field Descriptions

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `error` | object | Yes | Error container |
| `error.code` | string | Yes | Machine-readable error code |
| `error.message` | string | Yes | Human-readable message |
| `error.details` | object | No | Additional context data |

### 1.3 Response Examples

**Success Response (200 OK):**
```json
{
  "name": "iceberg.analytics.users",
  "type": "Dataset",
  "owner": "data@example.com"
}
```

**Error Response (404 Not Found):**
```json
{
  "error": {
    "code": "METRIC_NOT_FOUND",
    "message": "Metric 'iceberg.reporting.invalid' not found",
    "details": {
      "metric_name": "iceberg.reporting.invalid"
    }
  }
}
```

**Validation Error Response (400 Bad Request):**
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed",
    "details": {
      "name": "Name must follow pattern: catalog.schema.name",
      "owner": "Owner must be a valid email address"
    }
  }
}
```

---

## 2. HTTP Status Codes

### 2.1 Success Codes

| HTTP Status | Usage | Example Endpoints |
|-------------|-------|------------------|
| **200 OK** | Successful GET, PUT, POST operations | `GET /metrics`, `POST /metrics/{name}/run` |
| **201 Created** | Successful resource creation | `POST /metrics`, `POST /workflows/register` |
| **202 Accepted** | Asynchronous operation accepted | `POST /workflows/{name}/run` |
| **204 No Content** | Successful DELETE operations | `DELETE /workflows/{name}` |

### 2.2 Client Error Codes

| HTTP Status | Usage | Typical Error Codes |
|-------------|-------|-------------------|
| **400 Bad Request** | Invalid request parameters | `VALIDATION_ERROR`, `INVALID_CRON` |
| **401 Unauthorized** | Missing/invalid authentication | `UNAUTHORIZED` |
| **403 Forbidden** | Insufficient permissions | `FORBIDDEN`, `WORKFLOW_PERMISSION_DENIED` |
| **404 Not Found** | Resource not found | `*_NOT_FOUND` |
| **408 Request Timeout** | Operation timed out | `*_TIMEOUT` |
| **409 Conflict** | Resource already exists | `*_ALREADY_EXISTS` |
| **413 Payload Too Large** | Request/result too large | `RESULT_SIZE_LIMIT_EXCEEDED` |

### 2.3 Server Error Codes

| HTTP Status | Usage | Typical Error Codes |
|-------------|-------|-------------------|
| **500 Internal Server Error** | Unexpected server errors | `INTERNAL_ERROR` |
| **502 Bad Gateway** | External service error | `EXTERNAL_SERVICE_ERROR` |
| **503 Service Unavailable** | Dependent service unavailable | `SERVICE_UNAVAILABLE` |
| **504 Gateway Timeout** | External service timeout | `EXTERNAL_SERVICE_TIMEOUT` |

---

## 3. Server Error Codes

### 3.1 P0 Critical APIs Error Codes

#### Health API Errors

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `SERVICE_UNAVAILABLE` | 503 | One or more health components failed |

#### Metrics API Errors

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `METRIC_NOT_FOUND` | 404 | Metric not found by name |
| `METRIC_ALREADY_EXISTS` | 409 | Metric name already registered |
| `METRIC_EXECUTION_TIMEOUT` | 408 | Metric execution exceeded timeout |
| `METRIC_VALIDATION_ERROR` | 400 | Invalid metric specification |

#### Datasets API Errors

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `DATASET_NOT_FOUND` | 404 | Dataset not found by name |
| `DATASET_ALREADY_EXISTS` | 409 | Dataset name already registered |
| `DATASET_EXECUTION_TIMEOUT` | 408 | Dataset execution exceeded timeout |
| `DATASET_VALIDATION_ERROR` | 400 | Invalid dataset specification |

### 3.2 P1 High APIs Error Codes

#### Catalog API Errors

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `TABLE_NOT_FOUND` | 404 | Catalog table not found |
| `CATALOG_SERVICE_ERROR` | 502 | BigQuery/Trino API error |
| `CATALOG_TIMEOUT` | 504 | External catalog service timeout |
| `INVALID_TABLE_REFERENCE` | 400 | Invalid table reference format |

#### Lineage API Errors

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `LINEAGE_NOT_FOUND` | 404 | No lineage found for resource |
| `LINEAGE_GRAPH_ERROR` | 500 | Failed to build lineage graph |
| `INVALID_LINEAGE_DEPTH` | 400 | Invalid depth parameter |

### 3.3 P2 Medium APIs Error Codes

#### Workflow API Errors

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `WORKFLOW_NOT_FOUND` | 404 | Workflow not found |
| `WORKFLOW_ALREADY_EXISTS` | 409 | Workflow already registered |
| `WORKFLOW_PERMISSION_DENIED` | 403 | Cannot modify CODE workflow via API |
| `WORKFLOW_INVALID_STATE` | 400 | Invalid workflow state for operation |
| `INVALID_CRON` | 400 | Invalid cron expression |
| `AIRFLOW_API_ERROR` | 502 | Airflow service error |
| `WORKFLOW_RUN_NOT_FOUND` | 404 | Workflow run not found |
| `WORKFLOW_RUN_NOT_CANCELLABLE` | 409 | Cannot cancel completed/failed run |

### 3.4 P3 Low APIs Error Codes

#### Quality API Errors

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `QUALITY_SPEC_NOT_FOUND` | 404 | Quality specification not found |
| `QUALITY_TEST_FAILED` | 400 | Quality test execution failed |
| `INVALID_QUALITY_SPEC` | 400 | Invalid quality specification |

#### Query Metadata API Errors

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `QUERY_NOT_FOUND` | 404 | Query not found by ID |
| `QUERY_NOT_CANCELLABLE` | 409 | Cannot cancel completed query |
| `QUERY_TIMEOUT` | 408 | Query execution timed out |
| `INSUFFICIENT_QUERY_PERMISSIONS` | 403 | Cannot access query scope |

#### Transpile API Errors

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `TRANSPILE_RULE_NOT_FOUND` | 404 | Transpile rules not found |
| `SQL_TRANSPILE_ERROR` | 400 | Failed to transpile SQL |
| `UNSUPPORTED_DIALECT` | 400 | Unsupported SQL dialect |

#### Run API Errors

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `QUERY_EXECUTION_TIMEOUT` | 408 | Ad-hoc query timed out |
| `RESULT_SIZE_LIMIT_EXCEEDED` | 413 | Query result too large |
| `RATE_LIMIT_EXCEEDED` | 429 | Too many queries in time period |
| `INVALID_SQL_SYNTAX` | 400 | SQL syntax error |
| `UNSUPPORTED_ENGINE` | 400 | Unsupported query engine |

### 3.5 Common Error Codes

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `VALIDATION_ERROR` | 400 | Request validation failed |
| `UNAUTHORIZED` | 401 | Missing or invalid authentication |
| `FORBIDDEN` | 403 | Insufficient permissions |
| `NOT_FOUND` | 404 | Generic resource not found |
| `INTERNAL_ERROR` | 500 | Internal server error |
| `EXTERNAL_SERVICE_ERROR` | 502 | External service error |
| `SERVICE_UNAVAILABLE` | 503 | Service temporarily unavailable |

---

## 4. CLI Error Code Mapping

### 4.1 Server to CLI Error Mapping

| Server Error Code | CLI Error Code | CLI Command Context |
|-------------------|----------------|-------------------|
| `METRIC_NOT_FOUND` | DLI-201 | `dli metric get/run` |
| `METRIC_ALREADY_EXISTS` | DLI-202 | `dli metric register` |
| `METRIC_EXECUTION_TIMEOUT` | DLI-203 | `dli metric run` |
| `DATASET_NOT_FOUND` | DLI-301 | `dli dataset get/run` |
| `DATASET_ALREADY_EXISTS` | DLI-302 | `dli dataset register` |
| `DATASET_EXECUTION_TIMEOUT` | DLI-303 | `dli dataset run` |
| `WORKFLOW_NOT_FOUND` | DLI-401 | `dli workflow *` |
| `WORKFLOW_PERMISSION_DENIED` | DLI-402 | `dli workflow register/unregister` |
| `WORKFLOW_INVALID_STATE` | DLI-403 | `dli workflow pause/unpause` |
| `TABLE_NOT_FOUND` | DLI-501 | `dli catalog get` |
| `CATALOG_SERVICE_ERROR` | DLI-502 | `dli catalog *` |
| `LINEAGE_NOT_FOUND` | DLI-601 | `dli lineage show` |
| `QUALITY_SPEC_NOT_FOUND` | DLI-701 | `dli quality get/run` |
| `QUERY_NOT_FOUND` | DLI-801 | `dli query show/cancel` |
| `QUERY_TIMEOUT` | DLI-802 | `dli query *` |
| `RATE_LIMIT_EXCEEDED` | DLI-901 | `dli run` |
| `VALIDATION_ERROR` | DLI-001 | All commands |
| `UNAUTHORIZED` | DLI-002 | All commands |
| `FORBIDDEN` | DLI-003 | All commands |
| `INTERNAL_ERROR` | DLI-999 | All commands |

### 4.2 Error Message Formatting for CLI

**Server Error Response:**
```json
{
  "error": {
    "code": "METRIC_NOT_FOUND",
    "message": "Metric 'iceberg.reporting.invalid' not found",
    "details": {
      "metric_name": "iceberg.reporting.invalid"
    }
  }
}
```

**CLI Error Display:**
```
Error (DLI-201): Metric not found
└─ Metric 'iceberg.reporting.invalid' not found

Suggestion: Check available metrics with 'dli metric list'
```

---

## 5. Implementation Patterns

### 5.1 Exception Hierarchy

```kotlin
// Base exception with error code
sealed class BasecampException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    abstract val errorCode: String
    abstract val httpStatus: HttpStatus
}

// P0 Critical API exceptions
class MetricNotFoundException(metricName: String) : BasecampException(
    "Metric '$metricName' not found"
) {
    override val errorCode = "METRIC_NOT_FOUND"
    override val httpStatus = HttpStatus.NOT_FOUND
}

class MetricAlreadyExistsException(metricName: String) : BasecampException(
    "Metric '$metricName' already exists"
) {
    override val errorCode = "METRIC_ALREADY_EXISTS"
    override val httpStatus = HttpStatus.CONFLICT
}

class MetricExecutionTimeoutException(metricName: String, timeout: Int) : BasecampException(
    "Metric '$metricName' execution timed out after $timeout seconds"
) {
    override val errorCode = "METRIC_EXECUTION_TIMEOUT"
    override val httpStatus = HttpStatus.REQUEST_TIMEOUT
}

// Validation exception with field details
class ValidationException(
    message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
) : BasecampException(message) {
    override val errorCode = "VALIDATION_ERROR"
    override val httpStatus = HttpStatus.BAD_REQUEST
}

// External service exceptions
class ExternalServiceException(
    serviceName: String,
    cause: Throwable,
) : BasecampException(
    "External service '$serviceName' error: ${cause.message}",
    cause
) {
    override val errorCode = "EXTERNAL_SERVICE_ERROR"
    override val httpStatus = HttpStatus.BAD_GATEWAY
}
```

### 5.2 Global Exception Handler

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {
    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(BasecampException::class)
    fun handleBasecampException(ex: BasecampException): ResponseEntity<ErrorResponse> {
        logger.warn("Basecamp exception: {} - {}", ex.errorCode, ex.message)

        return ResponseEntity
            .status(ex.httpStatus)
            .body(ErrorResponse(
                error = ErrorDetails(
                    code = ex.errorCode,
                    message = ex.message ?: "Unknown error"
                )
            ))
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ErrorResponse> {
        logger.warn("Validation error: {}", ex.message)

        return ResponseEntity
            .status(ex.httpStatus)
            .body(ErrorResponse(
                error = ErrorDetails(
                    code = ex.errorCode,
                    message = ex.message ?: "Validation failed",
                    details = ex.fieldErrors.ifEmpty { null }
                )
            ))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { error ->
            error.field to (error.defaultMessage ?: "Invalid value")
        }

        logger.warn("Request validation failed: {}", fieldErrors)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = ErrorDetails(
                    code = "VALIDATION_ERROR",
                    message = "Request validation failed",
                    details = fieldErrors
                )
            ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", ex)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                error = ErrorDetails(
                    code = "INTERNAL_ERROR",
                    message = "An unexpected error occurred"
                )
            ))
    }
}

data class ErrorResponse(
    val error: ErrorDetails,
)

data class ErrorDetails(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null,
)
```

### 5.3 Error Response Builder

```kotlin
object ErrorResponseBuilder {
    fun notFound(resourceType: String, identifier: String): ErrorResponse {
        return ErrorResponse(
            error = ErrorDetails(
                code = "${resourceType.uppercase()}_NOT_FOUND",
                message = "$resourceType '$identifier' not found",
                details = mapOf("${resourceType.lowercase()}_name" to identifier)
            )
        )
    }

    fun alreadyExists(resourceType: String, identifier: String): ErrorResponse {
        return ErrorResponse(
            error = ErrorDetails(
                code = "${resourceType.uppercase()}_ALREADY_EXISTS",
                message = "$resourceType '$identifier' already exists",
                details = mapOf("${resourceType.lowercase()}_name" to identifier)
            )
        )
    }

    fun timeout(resourceType: String, identifier: String, timeoutSeconds: Int): ErrorResponse {
        return ErrorResponse(
            error = ErrorDetails(
                code = "${resourceType.uppercase()}_EXECUTION_TIMEOUT",
                message = "$resourceType '$identifier' execution timed out after $timeoutSeconds seconds",
                details = mapOf(
                    "${resourceType.lowercase()}_name" to identifier,
                    "timeout_seconds" to timeoutSeconds
                )
            )
        )
    }

    fun validation(fieldErrors: Map<String, String>): ErrorResponse {
        return ErrorResponse(
            error = ErrorDetails(
                code = "VALIDATION_ERROR",
                message = "Request validation failed",
                details = fieldErrors
            )
        )
    }
}

// Usage in service layer
@Service
class MetricService {
    fun getMetric(name: String): MetricDto {
        return metricRepository.findByName(name)
            ?: throw MetricNotFoundException(name)
    }
}

// Usage in controller layer
@RestController
class MetricController {
    @GetMapping("/{name}")
    fun getMetric(@PathVariable name: String): ResponseEntity<MetricDto> {
        val metric = metricService.getMetric(name)
        return ResponseEntity.ok(metric)
    }
}
```

### 5.4 External Service Error Handling

```kotlin
@Service
class CatalogService(
    private val bigQueryClient: BigQuery,
) {
    fun getTableDetails(tableRef: String): TableDetailDto {
        return try {
            val table = bigQueryClient.getTable(parseTableRef(tableRef))
            TableDetailMapper.toDto(table)
        } catch (ex: BigQueryException) {
            when (ex.code) {
                404 -> throw TableNotFoundException(tableRef)
                403 -> throw CatalogPermissionException(tableRef)
                else -> throw CatalogServiceException("Failed to fetch table details", ex)
            }
        } catch (ex: Exception) {
            logger.error("Unexpected error fetching table details for: $tableRef", ex)
            throw CatalogServiceException("Unexpected error", ex)
        }
    }
}

class TableNotFoundException(tableRef: String) : BasecampException(
    "Table '$tableRef' not found in catalog"
) {
    override val errorCode = "TABLE_NOT_FOUND"
    override val httpStatus = HttpStatus.NOT_FOUND
}

class CatalogServiceException(
    message: String,
    cause: Throwable,
) : BasecampException(message, cause) {
    override val errorCode = "CATALOG_SERVICE_ERROR"
    override val httpStatus = HttpStatus.BAD_GATEWAY
}
```

---

## 🔗 Related Documentation

- **CLI Error Code Mapping**: [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md)
- **Integration Patterns**: [`INTEGRATION_PATTERNS.md`](./INTEGRATION_PATTERNS.md)
- **API Specifications**: [`METRIC_FEATURE.md`](./METRIC_FEATURE.md), [`DATASET_FEATURE.md`](./DATASET_FEATURE.md)
- **Architecture Overview**: [`BASECAMP_OVERVIEW.md`](./BASECAMP_OVERVIEW.md)

---

*This document provides standardized error handling patterns ensuring consistency between server responses and CLI error codes for optimal user experience.*