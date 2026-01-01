package com.github.lambda.infra.repository

import com.github.lambda.domain.model.catalog.SampleQueryEntity
import com.github.lambda.domain.repository.SampleQueryRepositoryJpa
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * Sample Query JPA Repository Implementation
 *
 * Uses the Simplified Pattern: single interface extending both domain interface and JpaRepository.
 */
@Repository("sampleQueryRepositoryJpa")
interface SampleQueryRepositoryJpaImpl :
    SampleQueryRepositoryJpa,
    JpaRepository<SampleQueryEntity, Long> {
    // === Basic CRUD Operations ===
    // save() is provided by JpaRepository

    // === Table Reference Based Queries ===

    override fun findByTableRef(tableRef: String): List<SampleQueryEntity>

    @Query(
        """
        SELECT s FROM SampleQueryEntity s
        WHERE s.tableRef = :tableRef
        ORDER BY s.runCount DESC
        """,
    )
    override fun findByTableRefOrderByRunCountDesc(
        @Param("tableRef") tableRef: String,
        pageable: Pageable,
    ): List<SampleQueryEntity>

    override fun findByTableRefAndTitle(
        tableRef: String,
        title: String,
    ): SampleQueryEntity?

    override fun existsByTableRefAndTitle(
        tableRef: String,
        title: String,
    ): Boolean

    // === Author Based Queries ===

    override fun findByAuthor(author: String): List<SampleQueryEntity>

    // === Statistics ===

    override fun countByTableRef(tableRef: String): Long

    // === Custom Update Queries ===

    @Modifying
    @Query(
        """
        UPDATE SampleQueryEntity s
        SET s.runCount = s.runCount + 1, s.lastRun = :lastRun, s.updatedAt = CURRENT_TIMESTAMP
        WHERE s.tableRef = :tableRef AND s.title = :title
        """,
    )
    fun incrementRunCountByTableRefAndTitle(
        @Param("tableRef") tableRef: String,
        @Param("title") title: String,
        @Param("lastRun") lastRun: Instant,
    ): Int

    // === Top Queries ===

    @Query(
        """
        SELECT s FROM SampleQueryEntity s
        ORDER BY s.runCount DESC
        """,
    )
    fun findTopByRunCount(pageable: Pageable): List<SampleQueryEntity>
}

/**
 * Extension function to get sample queries with limit
 */
fun SampleQueryRepositoryJpaImpl.findByTableRefWithLimit(
    tableRef: String,
    limit: Int,
): List<SampleQueryEntity> = findByTableRefOrderByRunCountDesc(tableRef, PageRequest.of(0, limit))
