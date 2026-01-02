package com.github.lambda.domain.external

import com.github.lambda.common.exception.BusinessException

/**
 * Quality Rule Engine 예외
 *
 * Quality Rule Engine 시스템과의 통신에서 발생하는 예외
 */
class QualityRuleEngineException(
    message: String,
    cause: Throwable? = null,
) : BusinessException(
        message = message,
        errorCode = "QUALITY_RULE_ENGINE_ERROR",
        cause = cause,
    )
