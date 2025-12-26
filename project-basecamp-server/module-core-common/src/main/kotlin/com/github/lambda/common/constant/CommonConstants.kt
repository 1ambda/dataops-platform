package com.github.lambda.common.constant

/**
 * 공통 상수 정의
 */
object CommonConstants {
    // API 관련
    object Api {
        const val BASE_PATH = "/api"
        const val V1_PATH = "$BASE_PATH/v1"
        const val HEALTH_PATH = "/health"
    }

    // 페이징 관련
    object Pagination {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20
        const val MAX_SIZE = 100
    }

    // 헤더 관련
    object Headers {
        const val X_REQUEST_ID = "X-Request-ID"
        const val X_USER_ID = "X-User-ID"
        const val X_CORRELATION_ID = "X-Correlation-ID"
    }

    // 캐시 관련
    object Cache {
        // 분 단위 TTL
        const val DEFAULT_TTL_MINUTES = 10L // 기본 10분
        const val SHORT_TTL_MINUTES = 5L // 5분 (Job 상태 등 빈번한 변경)
        const val MEDIUM_TTL_MINUTES = 30L // 30분 (Dataset 메타데이터)
        const val LONG_TTL_MINUTES = 60L // 1시간 (Pipeline 설정)
        const val VERY_LONG_TTL_MINUTES = 120L // 2시간 (읽기 전용 데이터)

        // 초 단위 TTL (하위 호환성)
        const val DEFAULT_TTL_SECONDS = 600L // 기본 10분 (초 단위)
        const val SHORT_TTL_SECONDS = 300L // 5분 (Job 상태 등 빈번한 변경)
        const val MEDIUM_TTL_SECONDS = 1800L // 30분 (Dataset 메타데이터)
        const val LONG_TTL_SECONDS = 3600L // 1시간 (Pipeline 설정)
        const val VERY_LONG_TTL_SECONDS = 7200L // 2시간 (읽기 전용 데이터)
        const val DAILY_TTL_SECONDS = 86400L // 1일 (통계 데이터)

        // 캐시 이름 상수
        const val PIPELINE_CACHE = "pipeline"
        const val JOB_CACHE = "job"
        const val DATASET_CACHE = "dataset"
        const val PIPELINE_STATS_CACHE = "pipeline-statistics"
        const val JOB_STATS_CACHE = "job-statistics"
        const val READONLY_CACHE = "pipeline-readonly"
    }
}
