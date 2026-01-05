package com.dataops.basecamp.common.util

import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Query Utility Interface
 *
 * Generates unique query IDs for ad-hoc executions.
 * Extracted as interface for testability.
 */
interface QueryUtility {
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
class DefaultQueryUtility(
    private val clock: Clock,
) : QueryUtility {
    private val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    override fun generate(): String {
        val timestamp = LocalDateTime.now(clock).format(formatter)
        val suffix = UUID.randomUUID().toString().take(8)
        return "adhoc_${timestamp}_$suffix"
    }
}

/**
 * Deterministic Query Utility for testing
 *
 * Generates predictable IDs based on a counter.
 */
class DeterministicQueryUtility(
    private val clock: Clock,
    private val prefix: String = "adhoc",
) : QueryUtility {
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
