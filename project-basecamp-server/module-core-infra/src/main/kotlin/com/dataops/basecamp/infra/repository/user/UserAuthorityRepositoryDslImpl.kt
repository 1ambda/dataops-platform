package com.dataops.basecamp.infra.repository.user

import com.dataops.basecamp.domain.entity.user.UserAuthorityEntity
import com.dataops.basecamp.domain.repository.user.UserAuthorityRepositoryDsl
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/**
 * 사용자 권한 리포지토리 DSL 구현
 */
@Repository
class UserAuthorityRepositoryDslImpl : UserAuthorityRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findByUserId(userId: Long): List<UserAuthorityEntity> {
        val jpql = "SELECT ua FROM UserAuthorityEntity ua WHERE ua.userId = :userId"

        return entityManager
            .createQuery(jpql, UserAuthorityEntity::class.java)
            .setParameter("userId", userId)
            .resultList
    }
}
