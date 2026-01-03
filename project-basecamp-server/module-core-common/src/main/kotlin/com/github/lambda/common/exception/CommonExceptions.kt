package com.github.lambda.common.exception

/*
 * 공통 예외 클래스들
 */

/**
 * 비즈니스 로직 예외의 기본 클래스
 */
abstract class BusinessException(
    message: String,
    val errorCode: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * 리소스를 찾을 수 없는 경우
 */
class ResourceNotFoundException(
    resourceType: String,
    identifier: Any,
    cause: Throwable? = null,
) : BusinessException(
        message = "$resourceType not found: $identifier",
        errorCode = "RESOURCE_NOT_FOUND",
        cause = cause,
    )

/**
 * 잘못된 요청 파라미터
 */
class InvalidParameterException(
    parameterName: String,
    value: Any?,
    reason: String? = null,
    cause: Throwable? = null,
) : BusinessException(
        message =
            "Invalid parameter '$parameterName' = '$value'" +
                if (reason != null) ": $reason" else "",
        errorCode = "INVALID_PARAMETER",
        cause = cause,
    )

/**
 * 비즈니스 규칙 위반
 */
class BusinessRuleViolationException(
    rule: String,
    details: String? = null,
    cause: Throwable? = null,
) : BusinessException(
        message =
            "Business rule violation: $rule" +
                if (details != null) " - $details" else "",
        errorCode = "BUSINESS_RULE_VIOLATION",
        cause = cause,
    )

/**
 * 외부 시스템 연동 오류
 */
class ExternalSystemException(
    system: String,
    operation: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "External system error: $system - $operation",
        errorCode = "EXTERNAL_SYSTEM_ERROR",
        cause = cause,
    )

// ============= Metric Exceptions =============

/**
 * Exception thrown when a metric is not found
 */
class MetricNotFoundException(
    name: String,
) : BusinessException(
        message = "Metric '$name' not found",
        errorCode = "METRIC_NOT_FOUND",
    )

/**
 * Exception thrown when trying to create a metric that already exists
 */
class MetricAlreadyExistsException(
    name: String,
) : BusinessException(
        message = "Metric '$name' already exists",
        errorCode = "METRIC_ALREADY_EXISTS",
    )

/**
 * Exception thrown when metric execution times out
 */
class MetricExecutionTimeoutException(
    name: String,
    timeoutSeconds: Int,
) : BusinessException(
        message = "Metric '$name' execution timed out after $timeoutSeconds seconds",
        errorCode = "METRIC_EXECUTION_TIMEOUT",
    )

/**
 * Exception thrown when metric execution fails
 */
class MetricExecutionException(
    name: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Metric '$name' execution failed",
        errorCode = "METRIC_EXECUTION_FAILED",
        cause = cause,
    )

// ============= Dataset Exceptions =============

/**
 * Dataset을 찾을 수 없는 경우
 */
class DatasetNotFoundException(
    val datasetName: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Dataset '$datasetName' not found",
        errorCode = "DATASET_NOT_FOUND",
        cause = cause,
    )

/**
 * Dataset이 이미 존재하는 경우
 */
class DatasetAlreadyExistsException(
    val datasetName: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Dataset '$datasetName' already exists",
        errorCode = "DATASET_ALREADY_EXISTS",
        cause = cause,
    )

/**
 * Dataset 실행이 타임아웃된 경우
 */
class DatasetExecutionTimeoutException(
    val datasetName: String,
    val timeoutSeconds: Int,
    cause: Throwable? = null,
) : BusinessException(
        message = "Dataset execution timed out after $timeoutSeconds seconds",
        errorCode = "DATASET_EXECUTION_TIMEOUT",
        cause = cause,
    )

/**
 * Dataset 실행이 실패한 경우
 */
class DatasetExecutionFailedException(
    val datasetName: String,
    val sqlError: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Query execution failed: $sqlError",
        errorCode = "DATASET_EXECUTION_FAILED",
        cause = cause,
    )

/**
 * Dataset 이름이 잘못된 형식인 경우
 */
class InvalidDatasetNameException(
    val datasetName: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Dataset name '$datasetName' must follow pattern: catalog.schema.name",
        errorCode = "INVALID_DATASET_NAME",
        cause = cause,
    )

/**
 * Dataset SQL이 잘못된 경우
 */
class InvalidSqlException(
    val sql: String,
    val sqlError: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Invalid SQL expression: $sqlError",
        errorCode = "INVALID_SQL",
        cause = cause,
    )

/**
 * Dataset 소유자 이메일이 잘못된 경우
 */
class InvalidOwnerEmailException(
    val email: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Invalid owner email format: $email",
        errorCode = "INVALID_OWNER_EMAIL",
        cause = cause,
    )

/**
 * Dataset 태그가 너무 많은 경우
 */
class TooManyTagsException(
    val tagCount: Int,
    val maxAllowed: Int = 10,
    cause: Throwable? = null,
) : BusinessException(
        message = "Too many tags: $tagCount (maximum $maxAllowed allowed)",
        errorCode = "TOO_MANY_TAGS",
        cause = cause,
    )

/**
 * Cron 표현식이 잘못된 경우
 */
class InvalidCronException(
    val cronExpression: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Invalid cron expression: $cronExpression",
        errorCode = "INVALID_CRON",
        cause = cause,
    )

// ============= Catalog Exceptions =============

/**
 * Table not found in catalog
 */
class TableNotFoundException(
    val tableRef: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Table '$tableRef' not found in catalog",
        errorCode = "TABLE_NOT_FOUND",
        cause = cause,
    )

/**
 * Invalid table reference format
 */
class InvalidTableReferenceException(
    val tableRef: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Invalid table reference format: '$tableRef'. Expected format: project.dataset.table",
        errorCode = "INVALID_TABLE_REFERENCE",
        cause = cause,
    )

/**
 * Catalog service error (external system failure)
 */
class CatalogServiceException(
    val operation: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Catalog service error during operation: $operation",
        errorCode = "CATALOG_SERVICE_ERROR",
        cause = cause,
    )

/**
 * Catalog operation timeout
 */
class CatalogTimeoutException(
    val tableRef: String,
    val timeoutSeconds: Int,
    cause: Throwable? = null,
) : BusinessException(
        message = "Catalog operation for '$tableRef' timed out after $timeoutSeconds seconds",
        errorCode = "CATALOG_TIMEOUT",
        cause = cause,
    )

/**
 * Unsupported catalog engine
 */
class UnsupportedEngineException(
    val engine: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Unsupported catalog engine: '$engine'",
        errorCode = "UNSUPPORTED_ENGINE",
        cause = cause,
    )

// ============= Quality Exceptions =============

/**
 * Exception thrown when a quality spec is not found
 */
class QualitySpecNotFoundException(
    val specName: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Quality spec '$specName' not found",
        errorCode = "QUALITY_SPEC_NOT_FOUND",
        cause = cause,
    )

/**
 * Exception thrown when trying to create a quality spec that already exists
 */
class QualitySpecAlreadyExistsException(
    val specName: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Quality spec '$specName' already exists",
        errorCode = "QUALITY_SPEC_ALREADY_EXISTS",
        cause = cause,
    )

/**
 * Exception thrown when a quality run is not found
 */
class QualityRunNotFoundException(
    val runId: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Quality run '$runId' not found",
        errorCode = "QUALITY_RUN_NOT_FOUND",
        cause = cause,
    )

/**
 * Exception thrown when quality test execution times out
 */
class QualityTestExecutionTimeoutException(
    val resourceName: String,
    val timeoutSeconds: Int,
    cause: Throwable? = null,
) : BusinessException(
        message = "Quality test execution for '$resourceName' timed out after $timeoutSeconds seconds",
        errorCode = "QUALITY_TEST_EXECUTION_TIMEOUT",
        cause = cause,
    )

/**
 * Exception thrown when quality test execution fails
 */
class QualityTestExecutionException(
    val resourceName: String,
    val testName: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Quality test '$testName' execution failed for resource '$resourceName'",
        errorCode = "QUALITY_TEST_EXECUTION_FAILED",
        cause = cause,
    )

/**
 * Exception thrown when quality rule engine is unavailable
 */
class QualityRuleEngineUnavailableException(
    val operation: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Quality rule engine unavailable for operation: $operation",
        errorCode = "QUALITY_RULE_ENGINE_UNAVAILABLE",
        cause = cause,
    )

// ============= Workflow Exceptions =============

/**
 * Exception thrown when a workflow is not found
 */
class WorkflowNotFoundException(
    val datasetName: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Workflow '$datasetName' not found",
        errorCode = "WORKFLOW_NOT_FOUND",
        cause = cause,
    )

/**
 * Exception thrown when trying to create a workflow that already exists
 */
class WorkflowAlreadyExistsException(
    val datasetName: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Workflow '$datasetName' already exists",
        errorCode = "WORKFLOW_ALREADY_EXISTS",
        cause = cause,
    )

/**
 * Exception thrown when a workflow run is not found
 */
class WorkflowRunNotFoundException(
    val runId: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Workflow run '$runId' not found",
        errorCode = "WORKFLOW_RUN_NOT_FOUND",
        cause = cause,
    )

/**
 * Exception thrown when workflow is in invalid state for the operation
 */
class WorkflowInvalidStateException(
    val datasetName: String,
    val currentState: String,
    val operation: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Cannot perform '$operation' on workflow '$datasetName'. Current state: $currentState",
        errorCode = "WORKFLOW_INVALID_STATE",
        cause = cause,
    )

/**
 * Exception thrown when workflow permission is denied (e.g., trying to modify CODE workflow)
 */
class WorkflowPermissionDeniedException(
    val datasetName: String,
    val operation: String,
    val reason: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Permission denied for '$operation' on workflow '$datasetName': $reason",
        errorCode = "WORKFLOW_PERMISSION_DENIED",
        cause = cause,
    )

/**
 * Exception thrown when Airflow API call fails
 */
class AirflowConnectionException(
    val operation: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Airflow connection failed for operation: $operation",
        errorCode = "AIRFLOW_CONNECTION_FAILED",
        cause = cause,
    )

/**
 * Exception thrown when workflow execution times out
 */
class WorkflowExecutionTimeoutException(
    val runId: String,
    val timeoutSeconds: Int,
    cause: Throwable? = null,
) : BusinessException(
        message = "Workflow run '$runId' timed out after $timeoutSeconds seconds",
        errorCode = "WORKFLOW_EXECUTION_TIMEOUT",
        cause = cause,
    )

/**
 * Exception thrown when workflow execution fails
 */
class WorkflowExecutionException(
    val runId: String,
    val reason: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Workflow run '$runId' failed: $reason",
        errorCode = "WORKFLOW_EXECUTION_FAILED",
        cause = cause,
    )

/**
 * Exception thrown when workflow registration fails
 */
class WorkflowRegistrationException(
    val datasetName: String,
    val reason: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Failed to register workflow '$datasetName': $reason",
        errorCode = "WORKFLOW_REGISTRATION_FAILED",
        cause = cause,
    )

/**
 * Exception thrown when workflow unregistration fails
 */
class WorkflowUnregistrationException(
    val datasetName: String,
    val reason: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Failed to unregister workflow '$datasetName': $reason",
        errorCode = "WORKFLOW_UNREGISTRATION_FAILED",
        cause = cause,
    )

/**
 * Exception thrown when workflow storage operation fails (YAML file save/load)
 */
class WorkflowStorageException(
    val operation: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Workflow storage error during operation: $operation",
        errorCode = "WORKFLOW_STORAGE_ERROR",
        cause = cause,
    )

// ============= External System Exceptions =============

/**
 * Exception thrown when Quality Rule Engine communication fails
 */
class QualityRuleEngineException(
    message: String,
    cause: Throwable? = null,
) : BusinessException(
        message = message,
        errorCode = "QUALITY_RULE_ENGINE_ERROR",
        cause = cause,
    )

// ============= Ad-Hoc Execution Exceptions =============

/**
 * Exception thrown when rate limit is exceeded
 */
class RateLimitExceededException(
    val limitType: String,
    val limit: Int,
    val currentUsage: Int,
    val resetAt: java.time.LocalDateTime? = null,
    cause: Throwable? = null,
) : BusinessException(
        message = "Rate limit exceeded: $limit $limitType (current: $currentUsage)",
        errorCode = "RATE_LIMIT_EXCEEDED",
        cause = cause,
    )

/**
 * Exception thrown when query engine is not supported
 */
class QueryEngineNotSupportedException(
    val engine: String,
    val allowedEngines: List<String>,
    cause: Throwable? = null,
) : BusinessException(
        message = "Query engine '$engine' is not supported. Allowed engines: ${allowedEngines.joinToString(", ")}",
        errorCode = "UNSUPPORTED_ENGINE",
        cause = cause,
    )

/**
 * Exception thrown when SQL query is too large
 */
class QueryTooLargeException(
    val actualSizeBytes: Long,
    val maxSizeBytes: Long,
    cause: Throwable? = null,
) : BusinessException(
        message = "SQL query size ${actualSizeBytes}B exceeds limit ${maxSizeBytes}B",
        errorCode = "QUERY_TOO_LARGE",
        cause = cause,
    )

/**
 * Exception thrown when query execution times out
 */
class QueryExecutionTimeoutException(
    val queryId: String,
    val timeoutSeconds: Int,
    cause: Throwable? = null,
) : BusinessException(
        message = "Query execution timed out after $timeoutSeconds seconds",
        errorCode = "QUERY_EXECUTION_TIMEOUT",
        cause = cause,
    )

/**
 * Exception thrown when result size exceeds the limit
 */
class ResultSizeLimitExceededException(
    val resultSizeMb: Int,
    val limitMb: Int,
    cause: Throwable? = null,
) : BusinessException(
        message = "Result size ${resultSizeMb}MB exceeds limit ${limitMb}MB",
        errorCode = "RESULT_SIZE_LIMIT_EXCEEDED",
        cause = cause,
    )

/**
 * Exception thrown when result is not found for download
 */
class ResultNotFoundException(
    val queryId: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Result not found for query: $queryId",
        errorCode = "RESULT_NOT_FOUND",
        cause = cause,
    )

/**
 * Exception thrown when ad-hoc execution fails
 */
class AdHocExecutionException(
    val queryId: String,
    val reason: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Ad-hoc execution failed for query '$queryId': $reason",
        errorCode = "ADHOC_EXECUTION_FAILED",
        cause = cause,
    )

/**
 * Exception thrown when download token is invalid or expired
 */
class InvalidDownloadTokenException(
    val queryId: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Invalid or expired download token for query: $queryId",
        errorCode = "INVALID_DOWNLOAD_TOKEN",
        cause = cause,
    )
