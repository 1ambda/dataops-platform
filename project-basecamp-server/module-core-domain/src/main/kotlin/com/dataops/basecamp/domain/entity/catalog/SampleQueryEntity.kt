package com.dataops.basecamp.domain.entity.catalog

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.time.LocalDateTime

/**
 * Sample Query Entity
 *
 * Represents a sample query associated with a catalog table.
 * Stored in our self-managed MySQL DB.
 */
@Entity
@Table(
    name = "catalog_sample_queries",
    indexes = [
        Index(name = "idx_sample_query_table_ref", columnList = "table_ref"),
        Index(name = "idx_sample_query_run_count", columnList = "run_count"),
        Index(name = "idx_sample_query_author", columnList = "author"),
    ],
)
class SampleQueryEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    /**
     * Fully qualified table reference (project.dataset.table)
     */
    @field:NotBlank(message = "Table reference is required")
    @Column(name = "table_ref", nullable = false, length = 500)
    val tableRef: String,
    /**
     * Query title/name
     */
    @field:NotBlank(message = "Title is required")
    @field:Size(max = 200, message = "Title must not exceed 200 characters")
    @Column(nullable = false, length = 200)
    val title: String,
    /**
     * SQL query text
     */
    @field:NotBlank(message = "SQL is required")
    @Column(name = "`sql`", nullable = false, length = 10000)
    val sql: String,
    /**
     * Query author email
     */
    @field:NotBlank(message = "Author is required")
    @field:Email(message = "Author must be a valid email")
    @Column(nullable = false, length = 200)
    val author: String,
    /**
     * Number of times this query has been run
     */
    @Column(name = "run_count", nullable = false)
    var runCount: Int = 0,
    /**
     * Last time the query was run
     */
    @Column(name = "last_run")
    var lastRun: Instant? = null,
    /**
     * Query description
     */
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(length = 1000)
    val description: String? = null,
    /**
     * Record creation timestamp
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    /**
     * Record update timestamp
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    /**
     * Increment the run count and update lastRun timestamp
     */
    fun incrementRunCount() {
        runCount++
        lastRun = Instant.now()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SampleQueryEntity) return false
        if (id != null && other.id != null) return id == other.id
        return tableRef == other.tableRef && title == other.title
    }

    override fun hashCode(): Int {
        var result = tableRef.hashCode()
        result = 31 * result + title.hashCode()
        return result
    }
}
