package com.github.lambda.domain.exception

import com.github.lambda.common.exception.BusinessException

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
