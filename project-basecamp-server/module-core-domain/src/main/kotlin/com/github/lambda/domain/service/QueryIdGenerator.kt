package com.github.lambda.domain.service

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Query ID Generator Interface
 *
 * Generates unique query IDs for ad-hoc executions.
 * Extracted as interface for testability.
 */
interface QueryIdGenerator {
    /**
     * Generate a unique query ID
     *
     * @return Query ID in format: adhoc_{yyyyMMdd_HHmmss}_{uuid8chars}
     */
    fun generate(): String
}

/**
 * Default implementation using Clock and UUID
 *
 * Testability: Uses injected Clock for deterministic timestamp generation.
 * UUID suffix can be replaced with a seeded generator for tests.
 */
@Component
class DefaultQueryIdGenerator(
    private val clock: Clock,
) : QueryIdGenerator {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    override fun generate(): String {
        val timestamp = LocalDateTime.now(clock).format(formatter)
        val suffix = UUID.randomUUID().toString().take(8)
        return "adhoc_${timestamp}_$suffix"
    }
}

/**
 * Deterministic Query ID Generator for testing
 *
 * Generates predictable IDs based on a counter.
 */
class DeterministicQueryIdGenerator(
    private val clock: Clock,
    private val prefix: String = "adhoc",
) : QueryIdGenerator {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private var counter = 0

    override fun generate(): String {
        val timestamp = LocalDateTime.now(clock).format(formatter)
        val suffix = String.format("%08d", ++counter)
        return "${prefix}_${timestamp}_$suffix"
    }

    /**
     * Reset counter for test isolation
     */
    fun reset() {
        counter = 0
    }
}
