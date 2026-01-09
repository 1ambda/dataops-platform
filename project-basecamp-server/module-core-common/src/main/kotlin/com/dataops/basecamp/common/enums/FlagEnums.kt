package com.dataops.basecamp.common.enums

/**
 * Feature Flag 상태
 */
enum class FlagStatus {
    /** 활성화 (targeting_type에 따라 적용) */
    ENABLED,

    /** 전체 비활성화 */
    DISABLED,
}

/**
 * Feature Flag 타겟팅 타입
 */
enum class TargetingType {
    /** 전체 사용자 적용 */
    GLOBAL,

    /** Override가 있는 사용자만 */
    USER,
    // Phase 2
    // ROLE,      // UserRole 기반
    // PERCENTAGE // 비율 기반 롤아웃
}

/**
 * Feature Flag 대상 타입
 */
enum class SubjectType {
    /** 사용자 */
    USER,

    /** API 토큰 (Phase 2) */
    API_TOKEN,
}
