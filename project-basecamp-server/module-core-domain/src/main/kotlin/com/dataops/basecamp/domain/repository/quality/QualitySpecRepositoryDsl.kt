package com.dataops.basecamp.domain.repository.quality

import com.dataops.basecamp.common.enums.ResourceType
import com.dataops.basecamp.domain.entity.quality.QualitySpecEntity
import com.dataops.basecamp.domain.projection.quality.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Quality Spec Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * QualitySpec에 대한 복잡한 쿼리 및 집계 작업을 정의합니다.
 * QueryDSL이나 기타 복잡한 쿼리 기술에 대한 추상화를 제공합니다.
 */
interface QualitySpecRepositoryDsl {
    /**
     * 복합 필터 조건으로 quality spec 검색
     *
     * @param resourceType 리소스 타입 필터 (정확히 일치)
     * @param resourceName 리소스 이름 필터 (정확히 일치)
     * @param tag 태그 필터 (정확히 일치하는 태그 포함)
     * @param owner 소유자 필터 (부분 일치)
     * @param team 팀 필터 (정확히 일치)
     * @param enabled 활성화 상태 필터
     * @param search 이름 및 설명 검색 (부분 일치)
     * @param pageable 페이지네이션 정보
     * @return 필터 조건에 맞는 quality spec 목록
     */
    fun findByFilters(
        resourceType: ResourceType? = null,
        resourceName: String? = null,
        tag: String? = null,
        owner: String? = null,
        team: String? = null,
        enabled: Boolean? = null,
        search: String? = null,
        pageable: Pageable,
    ): Page<QualitySpecEntity>

    /**
     * 필터 조건에 맞는 quality spec 개수 조회
     *
     * @param resourceType 리소스 타입 필터 (정확히 일치)
     * @param resourceName 리소스 이름 필터 (정확히 일치)
     * @param tag 태그 필터 (정확히 일치하는 태그 포함)
     * @param owner 소유자 필터 (부분 일치)
     * @param team 팀 필터 (정확히 일치)
     * @param enabled 활성화 상태 필터
     * @param search 이름 및 설명 검색 (부분 일치)
     * @return 조건에 맞는 quality spec 개수
     */
    fun countByFilters(
        resourceType: ResourceType? = null,
        resourceName: String? = null,
        tag: String? = null,
        owner: String? = null,
        team: String? = null,
        enabled: Boolean? = null,
        search: String? = null,
    ): Long

    /**
     * Quality Spec 통계 정보 조회
     *
     * @param resourceType 특정 리소스 타입으로 제한 (null이면 전체)
     * @param owner 특정 소유자로 제한 (null이면 전체)
     * @return 통계 정보 (총 개수, 활성화/비활성화 개수, 리소스 타입별 개수 등)
     */
    fun getQualitySpecStatistics(
        resourceType: ResourceType? = null,
        owner: String? = null,
    ): QualitySpecStatisticsProjection

    /**
     * 리소스 타입별 quality spec 개수 조회
     *
     * @return 리소스 타입별 quality spec 개수 맵
     */
    fun getQualitySpecCountByResourceType(): List<QualitySpecCountByResourceTypeProjection>

    /**
     * 소유자별 quality spec 개수 조회
     *
     * @return 소유자별 quality spec 개수 맵
     */
    fun getQualitySpecCountByOwner(): List<QualitySpecCountByOwnerProjection>

    /**
     * 태그별 quality spec 개수 조회
     *
     * @return 태그별 quality spec 개수 맵
     */
    fun getQualitySpecCountByTag(): List<QualitySpecCountByTagProjection>

    /**
     * 팀별 quality spec 개수 조회
     *
     * @return 팀별 quality spec 개수 맵
     */
    fun getQualitySpecCountByTeam(): List<QualitySpecCountByTeamProjection>

    /**
     * 최근에 업데이트된 quality spec 조회
     *
     * @param limit 조회할 개수
     * @param daysSince 몇 일 전부터
     * @return 최근 업데이트된 quality spec 목록
     */
    fun findRecentlyUpdatedQualitySpecs(
        limit: Int,
        daysSince: Int,
    ): List<QualitySpecEntity>

    /**
     * 스케줄이 설정된 quality spec들 조회
     *
     * @param cronPattern cron 패턴 (부분 일치)
     * @return 스케줄이 설정된 quality spec 목록
     */
    fun findScheduledQualitySpecs(cronPattern: String? = null): List<QualitySpecEntity>

    /**
     * 특정 리소스와 관련된 모든 quality spec 조회
     *
     * @param resourceName 리소스 이름
     * @param resourceType 리소스 타입
     * @param includeDisabled 비활성화된 스펙도 포함할지 여부
     * @return 해당 리소스와 관련된 quality spec 목록
     */
    fun findQualitySpecsByResource(
        resourceName: String,
        resourceType: ResourceType,
        includeDisabled: Boolean = false,
    ): List<QualitySpecEntity>

    /**
     * 활성화된 quality spec 중 특정 조건에 맞는 것들 조회
     *
     * @param hasSchedule 스케줄이 설정된 것만 조회할지 여부
     * @param hasTests 테스트가 설정된 것만 조회할지 여부
     * @return 조건에 맞는 활성화된 quality spec 목록
     */
    fun findActiveQualitySpecs(
        hasSchedule: Boolean? = null,
        hasTests: Boolean? = null,
    ): List<QualitySpecEntity>
}
