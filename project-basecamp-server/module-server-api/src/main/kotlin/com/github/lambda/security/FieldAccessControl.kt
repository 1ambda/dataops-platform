package com.github.lambda.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * 필드 접근 제어 컴포넌트
 *
 * 사용자 역할과 보안 정책에 따라 응답 데이터의 필드 노출을 제어합니다.
 * 금융 프로젝트와 같이 보안 심의가 엄격한 환경에서 사용됩니다.
 */
@Component
class FieldAccessControl {
    /**
     * 현재 사용자의 권한 수준을 확인하여 SecurityLevel 반환
     */
    fun getCurrentUserSecurityLevel(): SecurityLevel {
        val authentication =
            SecurityContextHolder.getContext().authentication
                ?: return SecurityLevel.PUBLIC

        return when {
            hasRole(authentication, "ROLE_ADMIN") -> SecurityLevel.ADMIN
            hasRole(authentication, "ROLE_MANAGER") -> SecurityLevel.INTERNAL
            hasRole(authentication, "ROLE_USER") -> SecurityLevel.INTERNAL
            else -> SecurityLevel.PUBLIC
        }
    }

    /**
     * 특정 필드에 대한 접근 권한 확인
     */
    fun canAccessField(
        fieldName: String,
        targetUserId: String? = null,
    ): Boolean {
        val securityLevel = getCurrentUserSecurityLevel()
        val currentUserId = getCurrentUserId()

        return when (fieldName) {
            // 소유자 정보는 ADMIN 또는 본인만 접근 가능
            "owner" ->
                securityLevel == SecurityLevel.ADMIN ||
                    (targetUserId != null && currentUserId == targetUserId)

            // 스케줄 정보는 INTERNAL 이상에서만 접근 가능
            "scheduleExpression" -> securityLevel != SecurityLevel.PUBLIC

            // 설정 정보는 ADMIN만 접근 가능
            "config" -> securityLevel == SecurityLevel.ADMIN

            // 통계 상세 정보는 ADMIN만 접근 가능
            "ownerBreakdown" -> securityLevel == SecurityLevel.ADMIN

            // 실행 파라미터는 INTERNAL 이상에서만 접근 가능
            "parameters" -> securityLevel != SecurityLevel.PUBLIC

            // 기본적으로는 접근 허용
            else -> true
        }
    }

    /**
     * 데이터 마스킹 수준 결정
     */
    fun getMaskingLevel(fieldName: String): MaskingLevel {
        val securityLevel = getCurrentUserSecurityLevel()

        return when (fieldName) {
            "owner" ->
                when (securityLevel) {
                    SecurityLevel.PUBLIC -> MaskingLevel.FULL
                    SecurityLevel.INTERNAL -> MaskingLevel.PARTIAL
                    SecurityLevel.ADMIN -> MaskingLevel.NONE
                }
            "description" ->
                when (securityLevel) {
                    SecurityLevel.PUBLIC -> MaskingLevel.PARTIAL
                    else -> MaskingLevel.NONE
                }
            else -> MaskingLevel.NONE
        }
    }

    /**
     * 실제 데이터 마스킹 수행
     */
    fun maskData(
        data: String?,
        maskingLevel: MaskingLevel,
    ): String? {
        if (data == null) return null

        return when (maskingLevel) {
            MaskingLevel.NONE -> data
            MaskingLevel.PARTIAL -> {
                if (data.length <= 3) {
                    "***"
                } else {
                    data.take(3) + "*".repeat(data.length - 3)
                }
            }
            MaskingLevel.FULL -> "*".repeat(data.length.coerceAtLeast(3))
        }
    }

    private fun hasRole(
        authentication: org.springframework.security.core.Authentication,
        role: String,
    ): Boolean =
        authentication.authorities?.any {
            it.authority == role
        } ?: false

    private fun getCurrentUserId(): String? {
        val authentication = SecurityContextHolder.getContext().authentication
        return authentication?.name
    }
}

/**
 * 마스킹 수준 열거형
 */
enum class MaskingLevel {
    NONE, // 마스킹 없음
    PARTIAL, // 부분 마스킹
    FULL, // 완전 마스킹
}
