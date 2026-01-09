package com.dataops.basecamp.domain.external.flag

import com.dataops.basecamp.common.enums.SubjectType
import com.dataops.basecamp.domain.entity.flag.FlagEntity
import com.dataops.basecamp.domain.projection.flag.FlagTargetWithKeyProjection

/**
 * Feature Flag 캐시 포트 인터페이스
 *
 * Flag 정보 캐싱을 위한 추상화 인터페이스입니다.
 */
interface FlagCachePort {
    /**
     * Flag 캐시 조회
     */
    fun getFlag(flagKey: String): FlagEntity?

    /**
     * Flag 캐시 저장
     */
    fun setFlag(
        flagKey: String,
        flag: FlagEntity,
    )

    /**
     * Flag 캐시 삭제
     */
    fun evictFlag(flagKey: String)

    /**
     * FlagTarget 캐시 조회
     *
     * Subject(사용자/토큰)에 대한 모든 Flag Target 정보를 조회합니다.
     */
    fun getTargets(
        subjectType: SubjectType,
        subjectId: Long,
    ): List<FlagTargetWithKeyProjection>?

    /**
     * FlagTarget 캐시 저장
     */
    fun setTargets(
        subjectType: SubjectType,
        subjectId: Long,
        targets: List<FlagTargetWithKeyProjection>,
    )

    /**
     * FlagTarget 캐시 삭제
     */
    fun evictTargets(
        subjectType: SubjectType,
        subjectId: Long,
    )

    /**
     * 전체 캐시 삭제
     */
    fun evictAll()
}
