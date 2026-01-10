package com.dataops.basecamp.common.enums

/**
 * Audit Action Types
 *
 * Defines all possible actions that can be audited in the system.
 */
enum class AuditAction(
    val value: String,
    val description: String,
) {
    // Session
    LOGIN("LOGIN", "사용자 로그인"),
    LOGOUT("LOGOUT", "사용자 로그아웃"),

    // CRUD Operations
    LIST("LIST", "목록 조회"),
    READ("READ", "단건 조회"),
    CREATE("CREATE", "생성"),
    UPDATE("UPDATE", "수정"),
    DELETE("DELETE", "삭제"),

    // Execution Operations
    EXECUTE("EXECUTE", "실행"),
    CANCEL("CANCEL", "실행 취소"),
    STOP("STOP", "실행 중지"),
    DOWNLOAD("DOWNLOAD", "결과 다운로드"),

    // Workflow Operations
    REGISTER("REGISTER", "워크플로우 등록"),
    UNREGISTER("UNREGISTER", "워크플로우 해제"),
    TRIGGER("TRIGGER", "워크플로우 트리거"),
    BACKFILL("BACKFILL", "백필 실행"),
    PAUSE("PAUSE", "일시 중지"),
    UNPAUSE("UNPAUSE", "일시 중지 해제"),

    // Resource Operations
    LOCK("LOCK", "리소스 잠금"),
    RELEASE("RELEASE", "리소스 해제"),

    // Catalog & Search
    SEARCH("SEARCH", "검색"),

    // Lineage
    LINEAGE("LINEAGE", "리니지 조회"),

    // Transpile
    TRANSPILE("TRANSPILE", "SQL 변환"),

    // Sync
    SYNC("SYNC", "동기화"),

    // Compare
    COMPARE("COMPARE", "비교"),

    // Flag Management
    OVERRIDE_SET("OVERRIDE_SET", "오버라이드 설정"),
    OVERRIDE_REMOVE("OVERRIDE_REMOVE", "오버라이드 제거"),
    PERMISSION_SET("PERMISSION_SET", "권한 설정"),
    PERMISSION_REMOVE("PERMISSION_REMOVE", "권한 제거"),
    EVALUATE("EVALUATE", "플래그 평가"),

    // Health Check (mainly @NoAudit target)
    HEALTH_CHECK("HEALTH_CHECK", "헬스 체크"),
}

/**
 * Audit Resource Types
 *
 * Defines all possible resource types that can be audited in the system.
 */
enum class AuditResource(
    val value: String,
    val description: String,
) {
    // Core Resources
    METRIC("METRIC", "메트릭"),
    DATASET("DATASET", "데이터셋"),
    WORKFLOW("WORKFLOW", "워크플로우"),
    QUALITY("QUALITY", "품질 스펙"),
    QUERY("QUERY", "쿼리"),

    // Catalog
    CATALOG("CATALOG", "카탈로그"),
    TABLE("TABLE", "테이블"),

    // Team & SQL Management
    TEAM("TEAM", "팀"),
    SQL_FOLDER("SQL_FOLDER", "SQL 폴더"),
    SQL_SNIPPET("SQL_SNIPPET", "SQL 스니펫"),

    // Feature Flag
    FLAG("FLAG", "피처 플래그"),
    FLAG_OVERRIDE("FLAG_OVERRIDE", "플래그 오버라이드"),
    FLAG_PERMISSION("FLAG_PERMISSION", "플래그 권한"),

    // GitHub Integration
    GITHUB_REPOSITORY("GITHUB_REPOSITORY", "GitHub 저장소"),
    GITHUB_BRANCH("GITHUB_BRANCH", "GitHub 브랜치"),
    GITHUB_PULL_REQUEST("GITHUB_PULL_REQUEST", "GitHub PR"),

    // Lineage & Transpile
    LINEAGE("LINEAGE", "리니지"),
    TRANSPILE_RULE("TRANSPILE_RULE", "변환 규칙"),

    // Execution
    RUN("RUN", "실행"),
    EXECUTION("EXECUTION", "CLI 위임 실행"),

    // Resource Lock
    RESOURCE_LOCK("RESOURCE_LOCK", "리소스 잠금"),

    // Airflow Sync
    AIRFLOW_SYNC("AIRFLOW_SYNC", "Airflow 동기화"),

    // Session & System
    SESSION("SESSION", "세션"),
    SYSTEM("SYSTEM", "시스템"),
}
