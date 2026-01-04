package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.catalog.SampleQueryEntity
import com.github.lambda.domain.model.catalog.SampleQuery
import com.github.lambda.domain.repository.SampleQueryRepositoryDsl
import com.github.lambda.domain.repository.SampleQueryRepositoryJpa
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

/**
 * Sample Query Repository Implementation
 *
 * Adapter that implements the SampleQueryRepositoryDsl interface using JPA entities.
 * Handles conversion between domain models (SampleQuery) and entities (SampleQueryEntity).
 */
@Repository
class SampleQueryRepositoryDslImpl(
    private val sampleQueryRepositoryJpa: SampleQueryRepositoryJpa,
) : SampleQueryRepositoryDsl {
    override fun findByTableRef(
        tableRef: String,
        limit: Int,
    ): List<SampleQuery> {
        val pageable = PageRequest.of(0, limit)
        val entities = sampleQueryRepositoryJpa.findByTableRefOrderByRunCountDesc(tableRef, pageable)
        return entities.map { toDomainModel(it) }
    }

    @Transactional
    override fun save(
        tableRef: String,
        query: SampleQuery,
    ): SampleQuery {
        // Check if already exists
        val existing = sampleQueryRepositoryJpa.findByTableRefAndTitle(tableRef, query.title)

        val entity =
            existing?.copy(
                sql = query.sql,
                author = query.author,
            ) ?: toEntity(tableRef, query)

        val saved = sampleQueryRepositoryJpa.save(entity)
        return toDomainModel(saved)
    }

    @Transactional
    override fun incrementRunCount(
        tableRef: String,
        title: String,
    ) {
        val entity = sampleQueryRepositoryJpa.findByTableRefAndTitle(tableRef, title)
        entity?.let {
            it.incrementRunCount()
            sampleQueryRepositoryJpa.save(it)
        }
    }

    // === Entity <-> Domain Model Conversion ===

    private fun toDomainModel(entity: SampleQueryEntity): SampleQuery =
        SampleQuery(
            title = entity.title,
            sql = entity.sql,
            author = entity.author,
            runCount = entity.runCount,
            lastRun = entity.lastRun,
        )

    private fun toEntity(
        tableRef: String,
        query: SampleQuery,
    ): SampleQueryEntity =
        SampleQueryEntity(
            tableRef = tableRef,
            title = query.title,
            sql = query.sql,
            author = query.author,
            runCount = query.runCount,
            lastRun = query.lastRun,
        )
}

/**
 * Extension to copy SampleQueryEntity with modifications
 * (Since SampleQueryEntity is not a data class, we need explicit copy)
 */
private fun SampleQueryEntity.copy(
    sql: String = this.sql,
    author: String = this.author,
): SampleQueryEntity =
    SampleQueryEntity(
        id = this.id,
        tableRef = this.tableRef,
        title = this.title,
        sql = sql,
        author = author,
        runCount = this.runCount,
        lastRun = this.lastRun,
        description = this.description,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
