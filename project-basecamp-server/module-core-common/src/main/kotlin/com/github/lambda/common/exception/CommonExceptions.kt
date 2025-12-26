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
