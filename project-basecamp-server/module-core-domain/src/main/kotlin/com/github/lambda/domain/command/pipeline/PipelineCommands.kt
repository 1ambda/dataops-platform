package com.github.lambda.domain.command.pipeline

import com.github.lambda.domain.entity.pipeline.PipelineStatus

/**
 * 파이프라인 생성 명령
 */
data class CreatePipelineCommand(
    val name: String,
    val description: String?,
    val owner: String,
    val scheduleExpression: String?,
    val isActive: Boolean = true,
) {
    init {
        require(name.isNotBlank()) { "Pipeline name cannot be blank" }
        require(name.length <= 100) { "Pipeline name must not exceed 100 characters" }
        require(owner.isNotBlank()) { "Owner cannot be blank" }
        description?.let {
            require(it.length <= 500) { "Description must not exceed 500 characters" }
        }
    }
}

/**
 * 파이프라인 수정 명령
 */
data class UpdatePipelineCommand(
    val id: Long,
    val name: String,
    val description: String?,
    val scheduleExpression: String?,
) {
    init {
        require(name.isNotBlank()) { "Pipeline name cannot be blank" }
        require(name.length <= 100) { "Pipeline name must not exceed 100 characters" }
        description?.let {
            require(it.length <= 500) { "Description must not exceed 500 characters" }
        }
    }
}

/**
 * 파이프라인 상태 변경 명령
 */
data class UpdatePipelineStatusCommand(
    val id: Long,
    val status: PipelineStatus,
)

/**
 * 파이프라인 활성화 토글 명령
 */
data class TogglePipelineActiveCommand(
    val id: Long,
)

/**
 * 파이프라인 실행 명령
 */
data class ExecutePipelineCommand(
    val id: Long,
    val triggeredBy: String? = null,
    val parameters: Map<String, Any> = emptyMap(),
)

/**
 * 파이프라인 실행 중지 명령
 */
data class StopPipelineExecutionCommand(
    val pipelineId: Long,
    val executionId: String,
    val reason: String? = null,
)

/**
 * 파이프라인 삭제 명령 (소프트 삭제)
 */
data class DeletePipelineCommand(
    val id: Long,
    val deletedBy: String,
    val reason: String? = null,
)
