package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.exception.ProjectAlreadyExistsException
import com.dataops.basecamp.common.exception.ProjectNotFoundException
import com.dataops.basecamp.domain.entity.project.ProjectEntity
import com.dataops.basecamp.domain.repository.project.ProjectRepositoryDsl
import com.dataops.basecamp.domain.repository.project.ProjectRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Project 서비스
 *
 * Pure Hexagonal Architecture 패턴을 적용한 도메인 서비스입니다.
 * - Services는 concrete classes (no interfaces)
 * - 명령과 조회가 명확히 분리됨
 * - Domain Entity를 직접 반환 (DTO 변환은 API layer에서 처리)
 * - 소프트 삭제(deletedAt)를 지원합니다
 */
@Service
@Transactional(readOnly = true)
class ProjectService(
    private val projectRepositoryJpa: ProjectRepositoryJpa,
    private val projectRepositoryDsl: ProjectRepositoryDsl,
) {
    // === 명령(Command) 처리 ===

    /**
     * Project 생성 명령 처리
     *
     * @param project 생성할 Project Entity
     * @return 생성된 Project Entity
     * @throws ProjectAlreadyExistsException Project가 이미 존재하는 경우
     */
    @Transactional
    fun createProject(project: ProjectEntity): ProjectEntity {
        // 중복 체크 (soft delete 된 것도 포함하여 체크)
        if (projectRepositoryJpa.existsByName(project.name)) {
            throw ProjectAlreadyExistsException(project.name)
        }

        return projectRepositoryJpa.save(project)
    }

    /**
     * Project 수정 명령 처리
     *
     * @param id Project ID
     * @param displayName 수정할 displayName (null이면 변경 안 함)
     * @param description 수정할 description (null이면 변경 안 함)
     * @return 수정된 Project Entity
     * @throws ProjectNotFoundException Project를 찾을 수 없는 경우
     */
    @Transactional
    fun updateProject(
        id: Long,
        displayName: String? = null,
        description: String? = null,
    ): ProjectEntity {
        val existing =
            projectRepositoryJpa.findByIdAndDeletedAtIsNull(id)
                ?: throw ProjectNotFoundException(id)

        existing.update(displayName, description)

        return projectRepositoryJpa.save(existing)
    }

    /**
     * Project 삭제 명령 처리 (Soft Delete)
     *
     * @param id Project ID
     * @throws ProjectNotFoundException Project를 찾을 수 없는 경우
     */
    @Transactional
    fun deleteProject(id: Long) {
        val project =
            projectRepositoryJpa.findByIdAndDeletedAtIsNull(id)
                ?: throw ProjectNotFoundException(id)

        project.deletedAt = LocalDateTime.now()
        projectRepositoryJpa.save(project)
    }

    // === 조회(Query) 처리 ===

    /**
     * Project 단건 조회 (by ID)
     *
     * @param id Project ID
     * @return Project Entity (없으면 null)
     */
    fun getProjectById(id: Long): ProjectEntity? = projectRepositoryJpa.findByIdAndDeletedAtIsNull(id)

    /**
     * Project 단건 조회 (by ID, Not Null)
     *
     * @param id Project ID
     * @return Project Entity
     * @throws ProjectNotFoundException Project를 찾을 수 없는 경우
     */
    fun getProjectByIdOrThrow(id: Long): ProjectEntity = getProjectById(id) ?: throw ProjectNotFoundException(id)

    /**
     * Project 단건 조회 (by Name)
     *
     * @param name Project name
     * @return Project Entity (없으면 null)
     */
    fun getProjectByName(name: String): ProjectEntity? = projectRepositoryJpa.findByNameAndDeletedAtIsNull(name)

    /**
     * Project 목록 조회 (필터링 및 페이지네이션)
     *
     * @param search 이름 및 displayName 검색 (부분 일치)
     * @param pageable 페이지네이션 정보
     * @return 필터 조건에 맞는 Project 목록 (soft delete 제외)
     */
    fun listProjects(
        search: String? = null,
        pageable: Pageable,
    ): Page<ProjectEntity> = projectRepositoryDsl.findByFilters(search, pageable)

    /**
     * 전체 Project 개수 조회 (soft delete 제외)
     *
     * @return Project 개수
     */
    fun countProjects(): Long = projectRepositoryJpa.countByDeletedAtIsNull()

    // === Project 존재 확인 ===

    /**
     * Project 존재 여부 확인 (by name, soft delete 제외)
     *
     * @param name Project name
     * @return 존재 여부
     */
    fun existsProject(name: String): Boolean = projectRepositoryJpa.existsByNameAndDeletedAtIsNull(name)
}
