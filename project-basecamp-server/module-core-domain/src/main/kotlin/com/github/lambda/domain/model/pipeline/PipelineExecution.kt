package com.github.lambda.domain.model.pipeline

import java.time.LocalDateTime

/**
 * 파이프라인 실행 결과 도메인 객체
 * 헥사고날 아키텍처 원칙에 따라 도메인 레이어에서 정의된 값 객체입니다.
 */
data class PipelineExecution(
    val executionId: String,
    val pipelineId: Long,
    val pipelineName: String,
    val status: String,
    val startedAt: LocalDateTime,
    val message: String? = null,
    val parameters: Map<String, Any> = emptyMap(),
)
