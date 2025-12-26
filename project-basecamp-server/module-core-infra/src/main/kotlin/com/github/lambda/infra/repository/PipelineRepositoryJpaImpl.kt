package com.github.lambda.infra.repository

import com.github.lambda.domain.model.pipeline.PipelineEntity
import com.github.lambda.domain.model.pipeline.PipelineStatus
import com.github.lambda.domain.repository.PipelineRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

/**
 * 파이프라인 JPA Repository 구현 클래스
 *
 * Domain PipelineRepositoryJpa 인터페이스를 구현하고 Spring Data JPA를 조합으로 사용합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("pipelineRepositoryJpa")
class PipelineRepositoryJpaImpl(
    private val springDataRepository: PipelineRepositoryJpaSpringData,
) : PipelineRepositoryJpa {
    // 기본 CRUD 작업
    override fun save(pipeline: PipelineEntity): PipelineEntity = springDataRepository.save(pipeline)

    override fun findById(id: Long): PipelineEntity? = springDataRepository.findById(id).orElse(null)

    override fun deleteById(id: Long) = springDataRepository.deleteById(id)

    override fun existsById(id: Long): Boolean = springDataRepository.existsById(id)

    // 도메인 특화 조회 메서드
    override fun findByIdAndIsActiveTrue(id: Long): PipelineEntity? = springDataRepository.findByIdAndIsActiveTrue(id)

    override fun findAllByIsActive(isActive: Boolean): List<PipelineEntity> =
        springDataRepository.findAllByIsActive(isActive)

    override fun findByOwner(owner: String): List<PipelineEntity> = springDataRepository.findByOwner(owner)

    override fun findByStatus(status: PipelineStatus): List<PipelineEntity> = springDataRepository.findByStatus(status)

    // 페이지네이션 조회
    override fun findByOwnerOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<PipelineEntity> = springDataRepository.findByOwnerOrderByUpdatedAtDesc(owner, pageable)

    override fun findByOwnerAndIsActiveTrueOrderByUpdatedAtDesc(
        owner: String,
        pageable: Pageable,
    ): Page<PipelineEntity> = springDataRepository.findByOwnerAndIsActiveTrueOrderByUpdatedAtDesc(owner, pageable)

    override fun findByStatusOrderByUpdatedAtDesc(
        status: PipelineStatus,
        pageable: Pageable,
    ): Page<PipelineEntity> = springDataRepository.findByStatusOrderByUpdatedAtDesc(status, pageable)

    override fun findByStatusAndIsActiveTrueOrderByUpdatedAtDesc(
        status: PipelineStatus,
        pageable: Pageable,
    ): Page<PipelineEntity> = springDataRepository.findByStatusAndIsActiveTrueOrderByUpdatedAtDesc(status, pageable)

    // 상태 업데이트
    override fun updateStatus(
        id: Long,
        status: PipelineStatus,
    ): Boolean = springDataRepository.updateStatusById(id, status, LocalDateTime.now()) > 0

    override fun toggleActive(id: Long): Boolean {
        val pipeline = findById(id) ?: return false
        return springDataRepository.updateIsActiveById(id, !pipeline.isActive, LocalDateTime.now()) > 0
    }

    // 통계 및 집계
    override fun countByStatus(status: PipelineStatus): Long = springDataRepository.countByStatus(status)

    override fun countByOwner(owner: String): Long = springDataRepository.countByOwner(owner)
}
