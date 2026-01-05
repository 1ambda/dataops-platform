package com.dataops.basecamp.common.enums

/**
 * 감사 리소스 액션 열거형
 */
enum class AuditResourceAction(
    val value: String,
) {
    CREATE("CREATE"),
    READ("READ"),
    UPDATE("UPDATE"),
    DELETE("DELETE"),
}
