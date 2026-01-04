package com.github.lambda.domain.service

import com.github.lambda.common.exception.*
import com.github.lambda.domain.entity.dataset.DatasetEntity
import com.github.lambda.domain.repository.dataset.DatasetRepositoryDsl
import com.github.lambda.domain.repository.dataset.DatasetRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Dataset 서비스
 *
 * Pure Hexagonal Architecture 패턴을 적용한 도메인 서비스입니다.
 * - Services는 concrete classes (no interfaces)
 * - 명령과 조회가 명확히 분리됨
 * - Domain Entity를 직접 반환 (DTO 변환은 API layer에서 처리)
 * - 비즈니스 로직과 데이터 접근 로직 분리
 */
@Service
@Transactional(readOnly = true) // 기본값은 읽기 전용
class DatasetService(
    private val datasetRepositoryJpa: DatasetRepositoryJpa,
    private val datasetRepositoryDsl: DatasetRepositoryDsl,
) {
    // === 명령(Command) 처리 ===

    /**
     * Dataset 등록 명령 처리
     *
     * @param dataset 등록할 Dataset Entity
     * @return 등록된 Dataset Entity
     * @throws DatasetAlreadyExistsException Dataset이 이미 존재하는 경우
     */
    @Transactional
    fun registerDataset(dataset: DatasetEntity): DatasetEntity {
        // 중복 체크
        if (datasetRepositoryJpa.existsByName(dataset.name)) {
            throw DatasetAlreadyExistsException(dataset.name)
        }

        // 비즈니스 규칙 검증
        validateDataset(dataset)

        // 저장
        return datasetRepositoryJpa.save(dataset)
    }

    /**
     * Dataset 수정 명령 처리
     *
     * @param name Dataset 이름
     * @param updatedDataset 수정할 Dataset Entity
     * @return 수정된 Dataset Entity
     * @throws DatasetNotFoundException Dataset을 찾을 수 없는 경우
     */
    @Transactional
    fun updateDataset(
        name: String,
        updatedDataset: DatasetEntity,
    ): DatasetEntity {
        val existing =
            datasetRepositoryJpa.findByName(name)
                ?: throw DatasetNotFoundException(name)

        // 이름은 변경할 수 없음 (이미 name으로 조회했으므로)
        // DatasetEntity는 클래스이므로 새 인스턴스 생성
        val updated =
            DatasetEntity(
                id = existing.id,
                name = existing.name, // 이름 변경 불가
                owner = updatedDataset.owner,
                team = updatedDataset.team,
                description = updatedDataset.description,
                sql = updatedDataset.sql,
                tags = updatedDataset.tags,
                dependencies = updatedDataset.dependencies,
                scheduleCron = updatedDataset.scheduleCron,
                scheduleTimezone = updatedDataset.scheduleTimezone,
                createdAt = existing.createdAt, // 생성일은 유지
            )

        validateDataset(updated)

        return datasetRepositoryJpa.save(updated)
    }

    /**
     * Dataset 삭제 명령 처리
     *
     * @param name Dataset 이름
     * @throws DatasetNotFoundException Dataset을 찾을 수 없는 경우
     */
    @Transactional
    fun deleteDataset(name: String) {
        if (!datasetRepositoryJpa.existsByName(name)) {
            throw DatasetNotFoundException(name)
        }

        datasetRepositoryJpa.deleteByName(name)
    }

    // === 조회(Query) 처리 ===

    /**
     * Dataset 단건 조회
     *
     * @param name Dataset 이름
     * @return Dataset Entity (없으면 null)
     */
    fun getDataset(name: String): DatasetEntity? = datasetRepositoryJpa.findByName(name)

    /**
     * Dataset 단건 조회 (Not Null)
     *
     * @param name Dataset 이름
     * @return Dataset Entity
     * @throws DatasetNotFoundException Dataset을 찾을 수 없는 경우
     */
    fun getDatasetOrThrow(name: String): DatasetEntity = getDataset(name) ?: throw DatasetNotFoundException(name)

    /**
     * Dataset 목록 조회 (필터링 및 페이지네이션)
     *
     * @param tag 태그 필터 (정확히 일치하는 태그 포함)
     * @param owner 소유자 필터 (부분 일치)
     * @param search 이름 및 설명 검색 (부분 일치)
     * @param pageable 페이지네이션 정보
     * @return 필터 조건에 맞는 Dataset 목록
     */
    fun listDatasets(
        tag: String? = null,
        owner: String? = null,
        search: String? = null,
        pageable: Pageable,
    ): Page<DatasetEntity> = datasetRepositoryDsl.findByFilters(tag, owner, search, pageable)

    /**
     * 소유자별 Dataset 조회
     *
     * @param owner 소유자
     * @param pageable 페이지네이션 정보
     * @return 소유자의 Dataset 목록
     */
    fun getDatasetsByOwner(
        owner: String,
        pageable: Pageable,
    ): Page<DatasetEntity> = datasetRepositoryJpa.findByOwnerOrderByUpdatedAtDesc(owner, pageable)

    /**
     * 태그별 Dataset 조회
     *
     * @param tag 태그
     * @return 해당 태그를 가진 Dataset 목록
     */
    fun getDatasetsByTag(tag: String): List<DatasetEntity> = datasetRepositoryJpa.findByTagsContaining(tag)

    /**
     * Dataset 통계 조회
     *
     * @param owner 특정 소유자로 제한 (null이면 전체)
     * @return 통계 정보
     */
    fun getDatasetStatistics(owner: String? = null): Map<String, Any> = datasetRepositoryDsl.getDatasetStatistics(owner)

    /**
     * 최근에 업데이트된 Dataset 조회
     *
     * @param limit 조회할 개수
     * @param daysSince 몇 일 전부터
     * @return 최근 업데이트된 Dataset 목록
     */
    fun getRecentlyUpdatedDatasets(
        limit: Int,
        daysSince: Int,
    ): List<DatasetEntity> = datasetRepositoryDsl.findRecentlyUpdatedDatasets(limit, daysSince)

    /**
     * 의존성이 있는 Dataset들 조회
     *
     * @param dependency 의존하는 Dataset 이름
     * @return 해당 Dataset에 의존하는 Dataset 목록
     */
    fun getDatasetsByDependency(dependency: String): List<DatasetEntity> =
        datasetRepositoryDsl.findDatasetsByDependency(dependency)

    /**
     * 스케줄이 설정된 Dataset들 조회
     *
     * @param cronPattern cron 패턴 (부분 일치, null이면 전체)
     * @return 스케줄이 설정된 Dataset 목록
     */
    fun getScheduledDatasets(cronPattern: String? = null): List<DatasetEntity> =
        datasetRepositoryDsl.findScheduledDatasets(cronPattern)

    // === Dataset 존재 확인 ===

    /**
     * Dataset 존재 여부 확인
     *
     * @param name Dataset 이름
     * @return 존재 여부
     */
    fun existsDataset(name: String): Boolean = datasetRepositoryJpa.existsByName(name)

    // === 비즈니스 규칙 검증 ===

    /**
     * Dataset 비즈니스 규칙 검증
     *
     * @param dataset 검증할 Dataset Entity
     * @throws InvalidDatasetNameException Dataset 이름이 잘못된 경우
     * @throws TooManyTagsException 태그가 너무 많은 경우
     * @throws InvalidOwnerEmailException 소유자 이메일이 잘못된 경우
     * @throws InvalidCronException Cron 표현식이 잘못된 경우
     */
    private fun validateDataset(dataset: DatasetEntity) {
        // Dataset 이름 패턴 검증 (catalog.schema.name)
        if (!isValidDatasetName(dataset.name)) {
            throw InvalidDatasetNameException(dataset.name)
        }

        // 태그 개수 제한 검증
        if (dataset.tags.size > 10) {
            throw TooManyTagsException(dataset.tags.size)
        }

        // 소유자 이메일 형식 검증
        if (!isValidEmail(dataset.owner)) {
            throw InvalidOwnerEmailException(dataset.owner)
        }

        // Cron 표현식 검증 (설정된 경우에만)
        dataset.scheduleCron?.let { cron ->
            if (!isValidCronExpression(cron)) {
                throw InvalidCronException(cron)
            }
        }
    }

    /**
     * Dataset 이름 유효성 검증
     */
    private fun isValidDatasetName(name: String): Boolean {
        val pattern = Regex("^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*$")
        return pattern.matches(name)
    }

    /**
     * 이메일 형식 유효성 검증
     */
    private fun isValidEmail(email: String): Boolean {
        val pattern = Regex("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
        return pattern.matches(email)
    }

    /**
     * Cron 표현식 유효성 검증 (기본적인 형태만 체크)
     */
    private fun isValidCronExpression(cron: String): Boolean {
        // 기본적인 cron 형태 체크 (5개 또는 6개 필드)
        val parts = cron.trim().split("\\s+".toRegex())
        return parts.size == 5 || parts.size == 6
    }
}
