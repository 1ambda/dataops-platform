package com.github.lambda.domain.repository

import com.github.lambda.domain.entity.dataset.DatasetEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * Dataset Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * Dataset에 대한 복잡한 쿼리 및 집계 작업을 정의합니다.
 * QueryDSL이나 기타 복잡한 쿼리 기술에 대한 추상화를 제공합니다.
 */
interface DatasetRepositoryDsl {
    /**
     * 복합 필터 조건으로 dataset 검색
     *
     * @param tag 태그 필터 (정확히 일치하는 태그 포함)
     * @param owner 소유자 필터 (부분 일치)
     * @param search 이름 및 설명 검색 (부분 일치)
     * @param pageable 페이지네이션 정보
     * @return 필터 조건에 맞는 dataset 목록
     */
    fun findByFilters(
        tag: String? = null,
        owner: String? = null,
        search: String? = null,
        pageable: Pageable,
    ): Page<DatasetEntity>

    /**
     * 필터 조건에 맞는 dataset 개수 조회
     *
     * @param tag 태그 필터 (정확히 일치하는 태그 포함)
     * @param owner 소유자 필터 (부분 일치)
     * @param search 이름 및 설명 검색 (부분 일치)
     * @return 조건에 맞는 dataset 개수
     */
    fun countByFilters(
        tag: String? = null,
        owner: String? = null,
        search: String? = null,
    ): Long

    /**
     * Dataset 통계 정보 조회
     *
     * @param owner 특정 소유자로 제한 (null이면 전체)
     * @return 통계 정보 (총 개수, 소유자별 개수, 태그별 개수 등)
     */
    fun getDatasetStatistics(owner: String? = null): Map<String, Any>

    /**
     * 소유자별 dataset 개수 조회
     *
     * @return 소유자별 dataset 개수 맵
     */
    fun getDatasetCountByOwner(): List<Map<String, Any>>

    /**
     * 태그별 dataset 개수 조회
     *
     * @return 태그별 dataset 개수 맵
     */
    fun getDatasetCountByTag(): List<Map<String, Any>>

    /**
     * 최근에 업데이트된 dataset 조회
     *
     * @param limit 조회할 개수
     * @param daysSince 몇 일 전부터
     * @return 최근 업데이트된 dataset 목록
     */
    fun findRecentlyUpdatedDatasets(
        limit: Int,
        daysSince: Int,
    ): List<DatasetEntity>

    /**
     * 의존성이 있는 dataset들 조회
     *
     * @param dependency 의존하는 dataset 이름
     * @return 해당 dataset에 의존하는 dataset 목록
     */
    fun findDatasetsByDependency(dependency: String): List<DatasetEntity>

    /**
     * 스케줄이 설정된 dataset들 조회
     *
     * @param cronPattern cron 패턴 (부분 일치)
     * @return 스케줄이 설정된 dataset 목록
     */
    fun findScheduledDatasets(cronPattern: String? = null): List<DatasetEntity>
}
