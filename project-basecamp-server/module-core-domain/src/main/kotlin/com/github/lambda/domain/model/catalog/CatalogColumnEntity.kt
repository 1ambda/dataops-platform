package com.github.lambda.domain.model.catalog

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

/**
 * Catalog Column Entity
 *
 * Represents a column in a catalog table.
 * Stored in our self-managed MySQL DB.
 */
@Entity
@Table(
    name = "catalog_columns",
    indexes = [
        Index(name = "idx_catalog_column_table_id", columnList = "catalog_table_id"),
        Index(name = "idx_catalog_column_name", columnList = "name"),
        Index(name = "idx_catalog_column_is_pii", columnList = "is_pii"),
    ],
)
class CatalogColumnEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    /**
     * Parent table reference
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "catalog_table_id", nullable = false)
    var catalogTable: CatalogTableEntity? = null,
    /**
     * Column name
     */
    @field:NotBlank(message = "Column name is required")
    @Column(nullable = false, length = 200)
    val name: String,
    /**
     * Column data type (e.g., STRING, INT64, TIMESTAMP)
     */
    @field:NotBlank(message = "Data type is required")
    @Column(name = "data_type", nullable = false, length = 100)
    val dataType: String,
    /**
     * Column description
     */
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(length = 1000)
    val description: String? = null,
    /**
     * Whether this column contains PII data
     */
    @Column(name = "is_pii", nullable = false)
    val isPii: Boolean = false,
    /**
     * Fill rate (percentage of non-null values, 0.0 to 1.0)
     */
    @Column(name = "fill_rate")
    val fillRate: Double? = null,
    /**
     * Distinct count (number of unique values)
     */
    @Column(name = "distinct_count")
    val distinctCount: Long? = null,
    /**
     * Ordinal position of the column in the table
     */
    @Column(name = "ordinal_position", nullable = false)
    val ordinalPosition: Int = 0,
    /**
     * Whether the column is nullable
     */
    @Column(name = "is_nullable", nullable = false)
    val isNullable: Boolean = true,
    /**
     * Whether the column is part of the primary key
     */
    @Column(name = "is_primary_key", nullable = false)
    val isPrimaryKey: Boolean = false,
    /**
     * Whether the column is partitioning column
     */
    @Column(name = "is_partition_key", nullable = false)
    val isPartitionKey: Boolean = false,
    /**
     * Whether the column is clustering column
     */
    @Column(name = "is_clustering_key", nullable = false)
    val isClusteringKey: Boolean = false,
    /**
     * Default value for the column
     */
    @Column(name = "default_value", length = 500)
    val defaultValue: String? = null,
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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CatalogColumnEntity) return false
        if (id != null && other.id != null) return id == other.id
        return catalogTable?.name == other.catalogTable?.name && name == other.name
    }

    override fun hashCode(): Int {
        var result = catalogTable?.name?.hashCode() ?: 0
        result = 31 * result + name.hashCode()
        return result
    }
}
