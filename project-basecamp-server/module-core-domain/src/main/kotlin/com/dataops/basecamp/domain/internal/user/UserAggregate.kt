package com.dataops.basecamp.domain.internal.user

import com.dataops.basecamp.domain.entity.user.UserAuthorityEntity
import com.dataops.basecamp.domain.entity.user.UserEntity

/**
 * 사용자 집합 루트
 *
 * UserEntity와 관련 권한들을 함께 관리하는 도메인 집합체입니다.
 */
class UserAggregate(
    private val user: UserEntity,
    private val authorities: List<UserAuthorityEntity>,
) {
    /**
     * 사용자의 모든 권한명을 반환합니다.
     * 기본 역할과 추가 권한들을 모두 포함합니다.
     */
    fun getAuthorityNames(): Set<String> {
        val authorityNames = mutableSetOf<String>()

        // 기본 역할 추가
        authorityNames.add(user.role.name)

        // 추가 권한들 추가
        authorities.forEach { authority ->
            authorityNames.add(authority.authority)
        }

        return authorityNames
    }

    /**
     * 사용자 활성화 상태를 반환합니다.
     */
    fun isEnabled(): Boolean = user.enabled

    /**
     * 사용자 ID를 반환합니다.
     */
    fun getId(): Long? = user.id

    /**
     * 사용자 엔티티를 반환합니다.
     */
    fun getUser(): UserEntity = user

    /**
     * 사용자 권한 목록을 반환합니다.
     */
    fun getAuthorities(): List<UserAuthorityEntity> = authorities
}
