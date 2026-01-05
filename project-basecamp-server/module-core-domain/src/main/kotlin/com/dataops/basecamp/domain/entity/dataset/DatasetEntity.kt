package com.dataops.basecamp.domain.entity.dataset

import jakarta.persistence.*
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.*

/**
 * Dataset Entity for SQL-based dataset definitions
 *
 * Follows the specification in DATASET_FEATURE.md for API-driven dataset management.
 * This entity represents SQL-based dataset definitions with dependencies and scheduling.
 */
@Entity
@Table(
    name = "datasets",
    indexes = [
        Index(name = "idx_dataset_name", columnList = "name", unique = true),
        Index(name = "idx_dataset_owner", columnList = "owner"),
        Index(name = "idx_dataset_created_at", columnList = "created_at"),
    ],
)
class DatasetEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),
    @field:NotBlank(message = "Dataset name is required")
    @field:Pattern(
        regexp = "^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*$",
        message = "Dataset name must follow pattern: catalog.schema.name",
    )
    @Column(nullable = false, unique = true, length = 255)
    val name: String,
    @field:NotBlank(message = "Owner email is required")
    @field:Email(message = "Owner must be a valid email")
    @Column(nullable = false, length = 100)
    val owner: String,
    @field:Size(max = 100, message = "Team name must not exceed 100 characters")
    @Column(length = 100)
    val team: String? = null,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(length = 1000)
    val description: String? = null,
    @field:NotBlank(message = "SQL expression is required")
    @Column(name = "sql_expression", nullable = false, length = 10000)
    val sql: String,
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "dataset_tags", joinColumns = [JoinColumn(name = "dataset_id")])
    @Column(name = "tag", length = 50)
    val tags: Set<String> = emptySet(),
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "dataset_dependencies", joinColumns = [JoinColumn(name = "dataset_id")])
    @Column(name = "dependency", length = 255)
    val dependencies: Set<String> = emptySet(),
    @Column(name = "schedule_cron", length = 100)
    val scheduleCron: String? = null,
    @Column(name = "schedule_timezone", length = 50)
    val scheduleTimezone: String? = "UTC",
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    // Override equals and hashCode using name instead of id
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DatasetEntity) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()

    /**
     * Check if dataset has scheduling configuration
     */
    fun hasSchedule(): Boolean = !scheduleCron.isNullOrBlank()

    /**
     * Check if dataset has dependencies
     */
    fun hasDependencies(): Boolean = dependencies.isNotEmpty()

    /**
     * Check if dataset has tags
     */
    fun hasTags(): Boolean = tags.isNotEmpty()
}
