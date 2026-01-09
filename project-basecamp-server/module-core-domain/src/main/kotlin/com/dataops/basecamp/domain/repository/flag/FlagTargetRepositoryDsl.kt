package com.dataops.basecamp.domain.repository.flag

import com.dataops.basecamp.common.enums.SubjectType
import com.dataops.basecamp.domain.entity.flag.FlagTargetEntity
import com.dataops.basecamp.domain.projection.flag.FlagTargetWithKeyProjection

/**
 * FlagTarget Repository DSL 인터페이스 (복합 쿼리용)
 *
 * FlagTarget에 대한 복합 쿼리 작업을 정의합니다.
 */
interface FlagTargetRepositoryDsl {
    /**
     * Flag ID와 Subject로 FlagTarget 조회
     */
    fun findByFlagIdAndSubject(
        flagId: Long,
        subjectType: SubjectType,
        subjectId: Long,
    ): FlagTargetEntity?

    /**
     * Subject로 FlagTarget 조회 (Flag Key 포함)
     *
     * N+1 쿼리 방지를 위해 JOIN으로 flagKey를 함께 조회합니다.
     */
    fun findBySubjectWithFlagKey(
        subjectType: SubjectType,
        subjectId: Long,
    ): List<FlagTargetWithKeyProjection>
}
