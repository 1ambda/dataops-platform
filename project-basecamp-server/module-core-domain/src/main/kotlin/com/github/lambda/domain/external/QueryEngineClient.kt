package com.github.lambda.domain.external

import java.math.BigDecimal

/**
 * Query Engine Client 인터페이스 (Port)
 *
 * BigQuery, Trino 등 쿼리 엔진과의 통신을 추상화합니다.
 * Infrastructure layer에서 구현됩니다.
 */
interface QueryEngineClient {
    /**
     * SQL 쿼리 실행
     *
     * @param sql 실행할 SQL 쿼리
     * @param engine 쿼리 엔진 (bigquery, trino)
     * @param timeoutSeconds 실행 제한 시간
     * @param maxRows 최대 반환 행 수
     * @return 쿼리 실행 결과
     */
    fun execute(
        sql: String,
        engine: String,
        timeoutSeconds: Int,
        maxRows: Int,
    ): QueryExecutionResult

    /**
     * SQL 쿼리 검증 (dry-run)
     *
     * @param sql 검증할 SQL 쿼리
     * @param engine 쿼리 엔진 (bigquery, trino)
     * @return 검증 결과
     */
    fun validateSQL(
        sql: String,
        engine: String,
    ): QueryValidationResult

    /**
     * 지원하는 엔진 목록 반환
     */
    fun getSupportedEngines(): List<String>

    /**
     * 엔진이 지원되는지 확인
     */
    fun isEngineSupported(engine: String): Boolean = getSupportedEngines().contains(engine.lowercase())
}

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
