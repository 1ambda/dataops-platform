package com.github.lambda.domain.model.catalog

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.time.LocalDateTime

/**
 * Catalog Table Entity
 *
 * Represents a table in the data catalog stored in our self-managed MySQL DB.
 * Follows the same pattern as DatasetEntity for consistency.
 *
 * The 'name' field uses the format: project.dataset.table (e.g., my-project.analytics.users)
 */
@Entity
@Table(
    name = "catalog_tables",
    indexes = [
        Index(name = "idx_catalog_table_name", columnList = "name", unique = true),
        Index(name = "idx_catalog_table_engine", columnList = "engine"),
        Index(name = "idx_catalog_table_owner", columnList = "owner"),
        Index(name = "idx_catalog_table_team", columnList = "team"),
        Index(name = "idx_catalog_table_project", columnList = "project"),
        Index(name = "idx_catalog_table_dataset", columnList = "dataset_name"),
        Index(name = "idx_catalog_table_updated", columnList = "last_updated"),
    ],
)
class CatalogTableEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    /**
     * Fully qualified table name: project.dataset.table
     */
    @field:NotBlank(message = "Table name is required")
    @Column(nullable = false, unique = true, length = 500)
    val name: String,
    /**
     * Project/Catalog name (first part of fully qualified name)
     */
    @field:NotBlank(message = "Project is required")
    @Column(nullable = false, length = 200)
    val project: String,
    /**
     * Dataset/Schema name (second part of fully qualified name)
     */
    @field:NotBlank(message = "Dataset name is required")
    @Column(name = "dataset_name", nullable = false, length = 200)
    val datasetName: String,
    /**
     * Table name (third part of fully qualified name)
     */
    @field:NotBlank(message = "Table name is required")
    @Column(name = "table_name", nullable = false, length = 200)
    val tableName: String,
    /**
     * Query engine type: "bigquery" or "trino"
     */
    @field:NotBlank(message = "Engine is required")
    @Column(nullable = false, length = 50)
    val engine: String,
    /**
     * Owner email
     */
    @field:NotBlank(message = "Owner is required")
    @field:Email(message = "Owner must be a valid email")
    @Column(nullable = false, length = 200)
    val owner: String,
    /**
     * Team name (e.g., @data-eng)
     */
    @field:Size(max = 100, message = "Team name must not exceed 100 characters")
    @Column(length = 100)
    val team: String? = null,
    /**
     * Table description
     */
    @field:Size(max = 2000, message = "Description must not exceed 2000 characters")
    @Column(length = 2000)
    val description: String? = null,
    /**
     * Tags for categorization
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "catalog_table_tags", joinColumns = [JoinColumn(name = "catalog_table_id")])
    @Column(name = "tag", length = 100)
    val tags: Set<String> = emptySet(),
    /**
     * Number of rows in the table (cached metadata)
     */
    @Column(name = "row_count")
    val rowCount: Long? = null,
    /**
     * When the table data was last updated
     */
    @Column(name = "last_updated")
    val lastUpdated: Instant? = null,
    /**
     * Basecamp URL for the table
     */
    @Column(name = "basecamp_url", length = 500)
    val basecampUrl: String? = null,
    /**
     * Data stewards (comma-separated emails)
     */
    @Column(name = "stewards", length = 1000)
    val stewards: String? = null,
    /**
     * Data consumers (comma-separated emails)
     */
    @Column(name = "consumers", length = 1000)
    val consumers: String? = null,
    /**
     * Average update lag in hours (freshness metric)
     */
    @Column(name = "avg_update_lag_hours")
    val avgUpdateLagHours: Double? = null,
    /**
     * Update frequency description (e.g., "hourly", "daily")
     */
    @Column(name = "update_frequency", length = 50)
    val updateFrequency: String? = null,
    /**
     * Stale threshold in hours
     */
    @Column(name = "stale_threshold_hours")
    val staleThresholdHours: Int = 24,
    /**
     * Quality score (0-100)
     */
    @Column(name = "quality_score")
    val qualityScore: Int? = null,
    /**
     * Column metadata (one-to-many relationship)
     */
    @OneToMany(mappedBy = "catalogTable", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("ordinalPosition ASC")
    val columns: MutableList<CatalogColumnEntity> = mutableListOf(),
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
     * Check if the table is stale based on lastUpdated and staleThresholdHours
     */
    fun isStale(): Boolean {
        val lastUpdate = lastUpdated ?: return false
        val thresholdInstant = Instant.now().minusSeconds(staleThresholdHours.toLong() * 3600)
        return lastUpdate.isBefore(thresholdInstant)
    }

    /**
     * Get stewards as a list
     */
    fun getStewardsList(): List<String> =
        stewards
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    /**
     * Get consumers as a list
     */
    fun getConsumersList(): List<String> =
        consumers
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    /**
     * Add a column to this table
     */
    fun addColumn(column: CatalogColumnEntity) {
        columns.add(column)
        column.catalogTable = this
    }

    /**
     * Remove a column from this table
     */
    fun removeColumn(column: CatalogColumnEntity) {
        columns.remove(column)
        column.catalogTable = null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CatalogTableEntity) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()

    companion object {
        /**
         * Create a CatalogTableEntity from a fully qualified table name
         */
        fun fromFullyQualifiedName(
            fullyQualifiedName: String,
            engine: String,
            owner: String,
        ): CatalogTableEntity {
            val parts = fullyQualifiedName.split(".")
            require(parts.size == 3) { "Table name must be in format: project.dataset.table" }

            return CatalogTableEntity(
                name = fullyQualifiedName,
                project = parts[0],
                datasetName = parts[1],
                tableName = parts[2],
                engine = engine,
                owner = owner,
            )
        }
    }
}
