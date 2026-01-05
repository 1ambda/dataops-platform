package com.dataops.basecamp.common.enums

/**
 * Spec 동기화 에러 유형
 */
enum class SpecSyncErrorType {
    /** YAML 파싱 실패 */
    PARSE_ERROR,

    /** 유효성 검사 실패 */
    VALIDATION_ERROR,

    /** 저장소 접근 오류 */
    STORAGE_ERROR,

    /** Airflow 연동 오류 */
    AIRFLOW_ERROR,

    /** 알 수 없는 오류 */
    UNKNOWN,
}
