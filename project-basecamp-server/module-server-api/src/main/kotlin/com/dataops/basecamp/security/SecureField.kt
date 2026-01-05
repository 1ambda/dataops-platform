package com.dataops.basecamp.security

/**
 * 보안 필드 어노테이션
 *
 * 민감한 데이터 필드에 적용하여 자동으로 보안 정책을 적용합니다.
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SecureField(
    /**
     * 필드 접근에 필요한 최소 보안 레벨
     */
    val minSecurityLevel: SecurityLevel = SecurityLevel.INTERNAL,
    /**
     * 마스킹 적용 여부
     */
    val masked: Boolean = false,
    /**
     * 소유자만 접근 가능 여부
     */
    val ownerOnly: Boolean = false,
    /**
     * 감사 로그 기록 여부
     */
    val auditLog: Boolean = false,
    /**
     * 필드 설명 (감사 목적)
     */
    val description: String = "",
)

/**
 * 민감한 데이터 어노테이션
 * 특별히 보호가 필요한 데이터에 적용
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class SensitiveData(
    val category: SensitiveDataCategory,
    val reason: String = "",
)

/**
 * 민감한 데이터 카테고리
 */
enum class SensitiveDataCategory {
    PERSONAL_INFO, // 개인정보
    BUSINESS_SECRET, // 영업비밀
    SYSTEM_CONFIG, // 시스템 설정
    FINANCIAL_DATA, // 금융 데이터
    SECURITY_TOKEN, // 보안 토큰
}
