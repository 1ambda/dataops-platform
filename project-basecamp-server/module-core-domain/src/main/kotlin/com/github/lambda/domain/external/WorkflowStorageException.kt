package com.github.lambda.domain.external

import com.github.lambda.common.exception.BusinessException

/**
 * Workflow 저장소 연결 예외
 *
 * 워크플로우 YAML 파일 저장/조회에서 발생하는 예외
 */
class WorkflowStorageException(
    val operation: String,
    cause: Throwable? = null,
) : BusinessException(
        message = "Workflow storage error during operation: $operation",
        errorCode = "WORKFLOW_STORAGE_ERROR",
        cause = cause,
    )
