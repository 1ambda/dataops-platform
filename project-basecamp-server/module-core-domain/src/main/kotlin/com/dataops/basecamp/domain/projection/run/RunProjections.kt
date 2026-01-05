package com.dataops.basecamp.domain.projection.run

import java.time.Instant

/**
 * Stored result projection for ad-hoc SQL execution results
 * Used for service-to-controller responses and internal result storage
 */
data class StoredResultProjection(
    val queryId: String,
    val csvContent: ByteArray,
    val rowCount: Int,
    val expiresAt: Instant,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as StoredResultProjection
        return queryId == other.queryId
    }

    override fun hashCode(): Int = queryId.hashCode()
}
