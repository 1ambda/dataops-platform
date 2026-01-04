package com.github.lambda.infra.repository

import com.github.lambda.domain.entity.pipeline.JobEntity
import com.github.lambda.domain.repository.JobRepositoryDsl
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/**
 * JobEntity DSL 리포지토리 구현체
 */
@Repository("jobRepositoryDsl")
class JobRepositoryDslImpl : JobRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findLatestJobByPipelineId(pipelineId: Long): JobEntity? {
        val query =
            entityManager.createQuery(
                "SELECT j FROM JobEntity j WHERE j.pipelineId = :pipelineId ORDER BY j.createdAt DESC",
                JobEntity::class.java,
            )
        query.setParameter("pipelineId", pipelineId)
        query.maxResults = 1

        return query.resultList.firstOrNull()
    }
}
