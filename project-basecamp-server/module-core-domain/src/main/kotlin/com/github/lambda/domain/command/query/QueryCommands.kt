package com.github.lambda.domain.command.query

/**
 * Command to cancel a query execution
 */
data class CancelQueryCommand(
    val queryId: String,
    val reason: String? = null,
)
