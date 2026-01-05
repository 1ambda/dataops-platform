package com.dataops.basecamp.security

/**
 * 보안 레벨 열거형
 *
 * API 응답 데이터의 노출 수준을 제어하는 데 사용됩니다.
 */
enum class SecurityLevel {
    PUBLIC, // 공개 API (최소한의 정보만 노출)
    INTERNAL, // 내부 API (일부 정보 마스킹)
    ADMIN, // 관리자 API (모든 정보 노출)
}
