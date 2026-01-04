package com.github.lambda.domain.repository.user

import com.github.lambda.domain.entity.user.UserAuthorityEntity

/**
 * 사용자 권한 리포지토리 DSL 인터페이스
 */
interface UserAuthorityRepositoryDsl {
    /**
     * 사용자 ID로 권한 목록을 조회합니다.
     */
    fun findByUserId(userId: Long): List<UserAuthorityEntity>
}
