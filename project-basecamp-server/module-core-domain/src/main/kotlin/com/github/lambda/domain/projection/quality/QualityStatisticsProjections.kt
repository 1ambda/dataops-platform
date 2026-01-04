package com.github.lambda.domain.projection.quality

import com.github.lambda.domain.model.quality.ResourceType

/**
 * Quality Spec 통계 정보 Projection
 * Repository DSL getQualitySpecStatistics 메소드의 리턴값으로 사용
 */
data class QualitySpecStatisticsProjection(
    val totalSpecs: Long,
    val enabledSpecs: Long,
    val disabledSpecs: Long,
    val specsByResourceType: Map<ResourceType, Long>,
    val specsByOwner: Map<String, Long>,
    val specsByTeam: Map<String, Long>,
    val specsWithSchedule: Long,
    val specsWithTests: Long,
)

/**
 * 리소스 타입별 quality spec 개수 Projection
 * Repository DSL getQualitySpecCountByResourceType 메소드의 리턴값으로 사용
 */
data class QualitySpecCountByResourceTypeProjection(
    val resourceType: ResourceType,
    val count: Long,
)

/**
 * 소유자별 quality spec 개수 Projection
 * Repository DSL getQualitySpecCountByOwner 메소드의 리턴값으로 사용
 */
data class QualitySpecCountByOwnerProjection(
    val owner: String,
    val count: Long,
)

/**
 * 태그별 quality spec 개수 Projection
 * Repository DSL getQualitySpecCountByTag 메소드의 리턴값으로 사용
 */
data class QualitySpecCountByTagProjection(
    val tag: String,
    val count: Long,
)

/**
 * 팀별 quality spec 개수 Projection
 * Repository DSL getQualitySpecCountByTeam 메소드의 리턴값으로 사용
 */
data class QualitySpecCountByTeamProjection(
    val team: String,
    val count: Long,
)