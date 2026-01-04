package com.github.lambda.domain.service

import com.github.lambda.common.exception.BusinessRuleViolationException
import com.github.lambda.domain.entity.user.UserEntity
import com.github.lambda.domain.model.user.UserAggregate
import com.github.lambda.domain.repository.UserAuthorityRepositoryJpa
import com.github.lambda.domain.repository.UserRepositoryDsl
import com.github.lambda.domain.repository.UserRepositoryJpa
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 사용자 서비스
 */
@Service
@Transactional
class UserService(
    private val userRepositoryJpa: UserRepositoryJpa,
    private val userRepositoryDsl: UserRepositoryDsl,
    private val userAuthorityRepository: UserAuthorityRepositoryJpa,
) {
    /**
     * 이메일로 사용자를 조회하거나 예외를 던집니다.
     */
    fun findByEmailOrThrow(email: String): UserEntity =
        userRepositoryJpa.findByEmail(email)
            ?: throw BusinessRuleViolationException("User not found")

    /**
     * OIDC 추가 정보를 동기화합니다.
     */
    fun syncOidcAdditional(
        userAggregate: UserAggregate,
        email: String,
    ) {
        val user = userAggregate.getUser()
        user.sync(email)
        userRepositoryJpa.save(user)
    }

    /**
     * 이메일로 사용자 집합체를 조회하거나 예외를 던집니다.
     */
    fun findAggregateByEmailOrThrow(email: String): UserAggregate =
        userRepositoryDsl.findAggregateByEmail(email)
            ?: throw BusinessRuleViolationException("User not found")
}
