package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.common.exception.SqlSnippetAlreadyExistsException
import com.dataops.basecamp.common.exception.SqlSnippetNotFoundException
import com.dataops.basecamp.domain.entity.sql.SqlSnippetEntity
import com.dataops.basecamp.domain.repository.sql.SqlSnippetRepositoryDsl
import com.dataops.basecamp.domain.repository.sql.SqlSnippetRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * SQL Snippet 서비스
 *
 * Pure Hexagonal Architecture 패턴을 적용한 도메인 서비스입니다.
 * - Services는 concrete classes (no interfaces)
 * - 명령과 조회가 명확히 분리됨
 * - Domain Entity를 직접 반환 (DTO 변환은 API layer에서 처리)
 * - 소프트 삭제(deletedAt)를 지원합니다
 */
@Service
@Transactional(readOnly = true)
class SqlSnippetService(
    private val sqlSnippetRepositoryJpa: SqlSnippetRepositoryJpa,
    private val sqlSnippetRepositoryDsl: SqlSnippetRepositoryDsl,
    private val sqlFolderService: SqlFolderService,
) {
    // === 명령(Command) 처리 ===

    /**
     * SQL Snippet 생성 명령 처리
     *
     * @param projectId Project ID
     * @param folderId Folder ID
     * @param name 스니펫 이름
     * @param description 스니펫 설명
     * @param sqlText SQL 쿼리 텍스트
     * @param dialect SQL dialect
     * @return 생성된 SQL Snippet Entity
     * @throws SqlFolderNotFoundException Folder가 존재하지 않는 경우
     * @throws SqlSnippetAlreadyExistsException 스니펫 이름이 이미 존재하는 경우
     */
    @Transactional
    fun createSnippet(
        projectId: Long,
        folderId: Long,
        name: String,
        description: String? = null,
        sqlText: String,
        dialect: SqlDialect = SqlDialect.BIGQUERY,
    ): SqlSnippetEntity {
        // Folder 존재 확인 (projectId와 folderId 둘 다 검증)
        sqlFolderService.getFolderByIdOrThrow(projectId, folderId)

        // 이름 중복 체크 (Folder 내에서 unique)
        if (sqlSnippetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(name, folderId)) {
            throw SqlSnippetAlreadyExistsException(name, folderId)
        }

        val snippet =
            SqlSnippetEntity(
                folderId = folderId,
                name = name,
                description = description,
                sqlText = sqlText,
                dialect = dialect,
            )

        return sqlSnippetRepositoryJpa.save(snippet)
    }

    /**
     * SQL Snippet 수정 명령 처리
     *
     * @param projectId Project ID
     * @param folderId Folder ID
     * @param snippetId Snippet ID
     * @param name 스니펫 이름 (null이면 변경 없음)
     * @param description 스니펫 설명 (null이면 변경 없음)
     * @param sqlText SQL 쿼리 텍스트 (null이면 변경 없음)
     * @param dialect SQL dialect (null이면 변경 없음)
     * @return 수정된 SQL Snippet Entity
     * @throws SqlFolderNotFoundException Folder가 존재하지 않는 경우
     * @throws SqlSnippetNotFoundException Snippet을 찾을 수 없는 경우
     * @throws SqlSnippetAlreadyExistsException 새 이름이 이미 존재하는 경우
     */
    @Transactional
    fun updateSnippet(
        projectId: Long,
        folderId: Long,
        snippetId: Long,
        name: String? = null,
        description: String? = null,
        sqlText: String? = null,
        dialect: SqlDialect? = null,
    ): SqlSnippetEntity {
        // Folder 존재 확인
        sqlFolderService.getFolderByIdOrThrow(projectId, folderId)

        val snippet = getSnippetByIdOrThrow(folderId, snippetId)

        // 이름 변경 시 중복 체크
        name?.let { newName ->
            if (newName != snippet.name &&
                sqlSnippetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(newName, folderId)
            ) {
                throw SqlSnippetAlreadyExistsException(newName, folderId)
            }
        }

        snippet.update(name, description, sqlText, dialect)
        return sqlSnippetRepositoryJpa.save(snippet)
    }

    /**
     * SQL Snippet 삭제 명령 처리 (Soft Delete)
     *
     * @param projectId Project ID
     * @param folderId Folder ID
     * @param snippetId Snippet ID
     * @throws SqlFolderNotFoundException Folder가 존재하지 않는 경우
     * @throws SqlSnippetNotFoundException Snippet을 찾을 수 없는 경우
     */
    @Transactional
    fun deleteSnippet(
        projectId: Long,
        folderId: Long,
        snippetId: Long,
    ) {
        // Folder 존재 확인
        sqlFolderService.getFolderByIdOrThrow(projectId, folderId)

        val snippet = getSnippetByIdOrThrow(folderId, snippetId)
        snippet.deletedAt = LocalDateTime.now()
        sqlSnippetRepositoryJpa.save(snippet)
    }

    // === 조회(Query) 처리 ===

    /**
     * SQL Snippet 단건 조회 (by ID and Folder ID)
     *
     * @param folderId Folder ID
     * @param snippetId Snippet ID
     * @return SQL Snippet Entity (없으면 null)
     */
    fun getSnippetById(
        folderId: Long,
        snippetId: Long,
    ): SqlSnippetEntity? = sqlSnippetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(snippetId, folderId)

    /**
     * SQL Snippet 단건 조회 (by ID and Folder ID, Not Null)
     *
     * @param folderId Folder ID
     * @param snippetId Snippet ID
     * @return SQL Snippet Entity
     * @throws SqlSnippetNotFoundException Snippet을 찾을 수 없는 경우
     */
    fun getSnippetByIdOrThrow(
        folderId: Long,
        snippetId: Long,
    ): SqlSnippetEntity =
        getSnippetById(folderId, snippetId)
            ?: throw SqlSnippetNotFoundException(snippetId, folderId)

    /**
     * Project 내 SQL Snippet 목록 조회 (조건 검색, 페이징)
     *
     * @param projectId Project ID
     * @param folderId 특정 폴더로 필터링 (null인 경우 전체)
     * @param searchText 이름/설명/SQL 검색 (null인 경우 전체)
     * @param starred starred 필터 (null인 경우 전체)
     * @param dialect SQL dialect 필터 (null인 경우 전체)
     * @param pageable 페이징 정보
     * @return 페이징된 SQL Snippet 목록 (soft delete 제외)
     * @throws ProjectNotFoundException Project가 존재하지 않는 경우
     */
    fun listSnippets(
        projectId: Long,
        folderId: Long?,
        searchText: String?,
        starred: Boolean?,
        dialect: SqlDialect?,
        pageable: Pageable,
    ): Page<SqlSnippetEntity> =
        sqlSnippetRepositoryDsl.findByConditions(
            projectId = projectId,
            folderId = folderId,
            searchText = searchText,
            starred = starred,
            dialect = dialect,
            pageable = pageable,
        )

    /**
     * Folder 내 SQL Snippet 개수 조회
     *
     * @param folderId Folder ID
     * @return Snippet 개수 (soft delete 제외)
     */
    fun countSnippets(folderId: Long): Long = sqlSnippetRepositoryJpa.countByFolderIdAndDeletedAtIsNull(folderId)

    /**
     * 여러 Folder의 Snippet 개수 조회
     *
     * @param folderIds Folder ID 목록
     * @return Map<FolderId, Count>
     */
    fun countSnippetsByFolderIds(folderIds: List<Long>): Map<Long, Long> =
        sqlSnippetRepositoryDsl.countByFolderIds(folderIds)
}
