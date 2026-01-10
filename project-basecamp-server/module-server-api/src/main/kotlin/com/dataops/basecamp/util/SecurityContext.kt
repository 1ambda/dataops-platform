package com.dataops.basecamp.util

import com.dataops.basecamp.common.enums.UserRole
import com.dataops.basecamp.dto.SessionResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component
import java.security.Principal

/**
 * 보안 컨텍스트 유틸리티
 */
@Component
object SecurityContext {
    /**
     * 현재 사용자 ID를 조회합니다.
     */
    fun getCurrentUserId(): Long? {
        val context = SecurityContextHolder.getContext()
        if (context?.authentication == null) {
            return null
        }

        val principal = context.authentication?.principal
        // 현재는 임시로 1L을 반환 (실제 구현 시 CustomAuthPrincipal에서 추출)
        return if (principal != null) 1L else null
    }

    /**
     * 현재 사용자 ID를 조회하거나 예외를 던집니다.
     */
    fun getCurrentUserIdOrThrow(): Long =
        getCurrentUserId()
            ?: throw IllegalArgumentException("User ID not found in security context")

    /**
     * 현재 사용자가 특정 역할을 가지고 있는지 확인합니다.
     */
    fun hasRole(role: UserRole): Boolean {
        val context = SecurityContextHolder.getContext()
        val authorities = context?.authentication?.authorities ?: return false

        val roleString = "ROLE_${role.name}"
        return authorities.any { it.authority == roleString }
    }

    /**
     * 현재 사용자의 역할 목록을 조회합니다.
     */
    fun getCurrentRoles(): List<String> {
        val context = SecurityContextHolder.getContext()
        return context?.authentication?.authorities?.mapNotNull { it.authority } ?: emptyList()
    }

    /**
     * 현재 사용자 이름(이메일)을 조회합니다.
     */
    fun getCurrentUsername(): String {
        val context = SecurityContextHolder.getContext()
        val principal = context?.authentication?.principal

        return when (principal) {
            is OidcUser -> principal.email ?: principal.subject ?: "unknown"
            else -> "unknown"
        }
    }

    /**
     * Principal을 SessionResponse로 변환합니다.
     */
    fun of(principal: Principal?): SessionResponse =
        when (principal) {
            is OAuth2AuthenticationToken -> {
                val oidcUser = principal.principal as? OidcUser
                SessionResponse(
                    authenticated = true,
                    userId = oidcUser?.subject ?: "unknown",
                    email = oidcUser?.email ?: "",
                    roles = principal.authorities?.mapNotNull { it.authority } ?: emptyList(),
                )
            }
            else -> {
                SessionResponse(
                    authenticated = false,
                    userId = "",
                    email = "",
                    roles = emptyList(),
                )
            }
        }
}
