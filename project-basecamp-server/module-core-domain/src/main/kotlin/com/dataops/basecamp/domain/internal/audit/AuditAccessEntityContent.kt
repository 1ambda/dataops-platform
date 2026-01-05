package com.dataops.basecamp.domain.internal.audit

/**
 * 접근 감사 엔티티 컨텐츠
 *
 * 접근 감사 정보의 JSON 컨텐츠를 구조화한 데이터 클래스입니다.
 */
data class AuditAccessEntityContent(
    val controllerName: String,
    val controllerFunction: String,
    val requestUrl: String,
    val requestQuery: String?,
    val requestVerb: String,
)
