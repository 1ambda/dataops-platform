package com.dataops.basecamp.domain.projection.flag

import com.dataops.basecamp.common.enums.SubjectType

/**
 * FlagTarget 조회 시 flagKey를 포함하는 Projection
 *
 * N+1 쿼리 방지를 위해 JOIN으로 flagKey를 함께 조회합니다.
 */
data class FlagTargetWithKeyProjection(
    val flagKey: String,
    val flagId: Long,
    val subjectType: SubjectType,
    val subjectId: Long,
    val enabled: Boolean,
    val permissions: String?,
)
