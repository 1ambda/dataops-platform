package com.dataops.basecamp.common.enums

/**
 * 사용자 역할 열거형
 */
enum class UserRole(
    val scopes: List<String>,
) {
    ADMIN(listOf("ROLE.admin")),
    CONSUMER(listOf("ROLE.consumer")),
    PUBLIC(listOf("ROLE.public")),
}
