package com.github.lambda.domain.external

import java.math.BigDecimal

/**
 * 쿼리 실행 결과
 */
data class QueryExecutionResult(
    /** 결과 행 (컬럼명 -> 값 맵의 리스트) */
    val rows: List<Map<String, Any?>>,
    /** 스캔된 바이트 수 */
    val bytesScanned: Long,
    /** 예상 비용 (USD) */
    val costUsd: BigDecimal?,
    /** 실행 시간 (초) */
    val executionTimeSeconds: Double,
    /** 총 행 수 (제한 전) */
    val totalRows: Long? = null,
    /** 경고 메시지 */
    val warnings: List<String> = emptyList(),
)

/**
 * 쿼리 검증 결과
 */
data class QueryValidationResult(
    /** 검증 성공 여부 */
    val valid: Boolean,
    /** 검증 오류 메시지 */
    val errorMessage: String? = null,
    /** 상세 오류 정보 */
    val errorDetails: Map<String, Any>? = null,
    /** 검증 시간 (초) */
    val validationTimeSeconds: Double,
    /** 경고 메시지 */
    val warnings: List<String> = emptyList(),
)
