package com.github.lambda.domain.repository

import com.github.lambda.domain.model.pipeline.JobEntity

/**
 * 작업 Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * 복잡한 쿼리 및 집계 작업을 정의합니다.
 * QueryDSL이나 기타 복잡한 쿼리 기술에 대한 추상화를 제공합니다.
 */
interface JobRepositoryDsl {
    // 복잡한 쿼리 작업
    fun findLatestJobByPipelineId(pipelineId: Long): JobEntity?
}
