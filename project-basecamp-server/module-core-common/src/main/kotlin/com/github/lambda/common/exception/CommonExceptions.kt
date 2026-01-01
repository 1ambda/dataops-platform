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
