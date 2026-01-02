package com.github.lambda.domain.external

import com.github.lambda.common.exception.BusinessException

/**
 * Airflow 연결 예외
 *
 * Airflow 시스템과의 통신에서 발생하는 예외
 */
class AirflowConnectionException(
    val operation: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Airflow connection error during operation: $operation",
        errorCode = "AIRFLOW_CONNECTION_ERROR",
        cause = cause,
    )
