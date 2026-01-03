package com.github.lambda.infra.config

import com.github.lambda.domain.model.adhoc.RunExecutionConfig
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Run Execution 설정 Properties
 *
 * application.yaml의 basecamp.run 설정을 바인딩합니다.
 * Domain layer의 RunExecutionConfig 인터페이스를 구현합니다.
 */
@ConfigurationProperties(prefix = "basecamp.run")
class RunExecutionProperties : RunExecutionConfig {
    /** 최대 쿼리 실행 시간 (초) - 기본값: 1800초 (30분) */
    override var maxQueryDurationSeconds: Int = 1800

    /** 최대 반환 행 수 - 기본값: 10000 */
    override var maxResultRows: Int = 10000

    /** 최대 결과 크기 (MB) - 기본값: 100MB */
    override var maxResultSizeMb: Int = 100

    /** 허용된 쿼리 엔진 목록 */
    override var allowedEngines: List<String> = listOf("bigquery", "trino")

    /** 허용된 파일 타입 목록 */
    override var allowedFileTypes: List<String> = listOf(".sql", ".yaml")

    /** 최대 SQL 파일 크기 (MB) - 기본값: 10MB */
    override var maxFileSizeMb: Int = 10

    /** 시간당 최대 쿼리 수 - 기본값: 50 */
    override var queriesPerHour: Int = 50

    /** 일일 최대 쿼리 수 - 기본값: 200 */
    override var queriesPerDay: Int = 200

    /** 결과 만료 시간 (시간) - 기본값: 8시간 */
    override var resultExpirationHours: Int = 8
}
