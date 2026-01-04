package com.github.lambda.domain.entity.lineage

import com.github.lambda.common.enums.LineageEdgeType
import com.github.lambda.domain.entity.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Lineage Edge Entity
 *
 * Represents a directed edge/relationship between two nodes in the lineage graph.
 * The relationship flows from source to target (source -> target).
 */
@Entity
@Table(
    name = "lineage_edges",
    indexes = [
        Index(name = "idx_lineage_edge_source", columnList = "source"),
        Index(name = "idx_lineage_edge_target", columnList = "target"),
        Index(name = "idx_lineage_edge_type", columnList = "edge_type"),
        Index(name = "idx_lineage_edge_source_target", columnList = "source, target", unique = true),
    ],
)
class LineageEdgeEntity(
    @NotBlank(message = "Source node name is required")
    @Size(max = 255, message = "Source node name must not exceed 255 characters")
    @Column(name = "source", nullable = false, length = 255)
    var source: String = "",
    @NotBlank(message = "Target node name is required")
    @Size(max = 255, message = "Target node name must not exceed 255 characters")
    @Column(name = "target", nullable = false, length = 255)
    var target: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "edge_type", nullable = false, length = 20)
    var edgeType: LineageEdgeType = LineageEdgeType.DIRECT,
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    var description: String? = null,
) : BaseEntity() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LineageEdgeEntity) return false
        return source == other.source && target == other.target
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + target.hashCode()
        return result
    }

    override fun toString(): String = "LineageEdgeEntity(source='$source', target='$target', edgeType=$edgeType)"
}
