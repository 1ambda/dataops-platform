package com.github.lambda.domain.model.workflow

/**
 * Airflow cluster environment type
 *
 * 클러스터가 운영되는 환경을 나타냅니다.
 */
enum class AirflowEnvironment {
    /**
     * 개발 환경
     */
    DEVELOPMENT,

    /**
     * 운영 환경
     */
    PRODUCTION,
}
