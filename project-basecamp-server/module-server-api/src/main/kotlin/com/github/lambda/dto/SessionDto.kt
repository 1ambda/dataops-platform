package com.github.lambda.dto

/**
 * 세션 응답 DTO
 */
data class SessionResponse(
    val authenticated: Boolean,
    val userId: String,
    val email: String,
    val roles: List<String>,
)
