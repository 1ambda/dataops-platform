package com.github.lambda.infra.repository.user

import com.github.lambda.domain.entity.user.UserAuthorityEntity
import com.github.lambda.domain.entity.user.UserEntity
import com.github.lambda.domain.model.user.UserAggregate
import com.github.lambda.domain.repository.user.UserRepositoryDsl
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/**
 * 사용자 리포지토리 DSL 구현
 *
 * Domain UserRepositoryDsl 인터페이스를 구현합니다.
 */
@Repository("userRepositoryDsl")
class UserRepositoryDslImpl : UserRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findAggregateByEmail(email: String): UserAggregate? {
        // 사용자 조회
        val jpqlUser = "SELECT u FROM UserEntity u WHERE u.email = :email"
        val user =
            try {
                entityManager
                    .createQuery(jpqlUser, UserEntity::class.java)
                    .setParameter("email", email)
                    .singleResult
            } catch (e: Exception) {
                return null
            }

        // 권한 목록 조회
        val jpql = "SELECT ua FROM UserAuthorityEntity ua WHERE ua.userId = :userId"
        val authorities =
            entityManager
                .createQuery(jpql, UserAuthorityEntity::class.java)
                .setParameter("userId", user.id)
                .resultList

        return UserAggregate(
            user = user,
            authorities = authorities,
        )
    }

    override fun findAggregateById(id: Long): UserAggregate? {
        // 사용자 조회
        val jpqlUser = "SELECT u FROM UserEntity u WHERE u.id = :id"
        val user =
            try {
                entityManager
                    .createQuery(jpqlUser, UserEntity::class.java)
                    .setParameter("id", id)
                    .singleResult
            } catch (e: Exception) {
                return null
            }

        // 권한 목록 조회
        val jpql = "SELECT ua FROM UserAuthorityEntity ua WHERE ua.userId = :userId"
        val authorities =
            entityManager
                .createQuery(jpql, UserAuthorityEntity::class.java)
                .setParameter("userId", user.id)
                .resultList

        return UserAggregate(
            user = user,
            authorities = authorities,
        )
    }
}
