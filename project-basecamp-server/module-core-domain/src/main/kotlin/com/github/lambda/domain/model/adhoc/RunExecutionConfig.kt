package com.github.lambda.domain.model.adhoc

/**
 * Run 실행 설정 인터페이스
 *
 * Infrastructure layer에서 @ConfigurationProperties로 구현됩니다.
 */
interface RunExecutionConfig {
    /** 최대 쿼리 실행 시간 (초) */
    val maxQueryDurationSeconds: Int

    /** 최대 반환 행 수 */
    val maxResultRows: Int

    /** 최대 결과 크기 (MB) */
    val maxResultSizeMb: Int

    /** 허용된 쿼리 엔진 목록 */
    val allowedEngines: List<String>

    /** 허용된 파일 타입 목록 */
    val allowedFileTypes: List<String>

    /** 최대 SQL 파일 크기 (MB) */
    val maxFileSizeMb: Int

    /** 시간당 최대 쿼리 수 */
    val queriesPerHour: Int

    /** 일일 최대 쿼리 수 */
    val queriesPerDay: Int

    /** 결과 만료 시간 (시간) */
    val resultExpirationHours: Int
}
