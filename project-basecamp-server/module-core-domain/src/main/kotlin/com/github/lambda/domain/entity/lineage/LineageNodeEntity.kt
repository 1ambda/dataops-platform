package com.github.lambda.domain.entity.lineage

import com.github.lambda.common.enums.LineageNodeType
import jakarta.persistence.*
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

/**
 * Lineage Node Entity
 *
 * Represents a node in the lineage graph. Each node represents a data asset
 * like a dataset, metric, table, or view.
 */
@Entity
@Table(
    name = "lineage_nodes",
    indexes = [
        Index(name = "idx_lineage_node_name", columnList = "name", unique = true),
        Index(name = "idx_lineage_node_type", columnList = "type"),
        Index(name = "idx_lineage_node_owner", columnList = "owner"),
        Index(name = "idx_lineage_node_team", columnList = "team"),
    ],
)
class LineageNodeEntity(
    @Id
    @NotBlank(message = "Node name is required")
    @Size(max = 255, message = "Node name must not exceed 255 characters")
    @Column(name = "name", nullable = false, unique = true, length = 255)
    var name: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    var type: LineageNodeType = LineageNodeType.TABLE,
    @Email(message = "Owner must be a valid email")
    @Size(max = 100, message = "Owner must not exceed 100 characters")
    @Column(name = "owner", length = 100)
    var owner: String? = null,
    @Size(max = 100, message = "Team must not exceed 100 characters")
    @Column(name = "team", length = 100)
    var team: String? = null,
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    var description: String? = null,
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "lineage_node_tags",
        joinColumns = [JoinColumn(name = "lineage_node_name", referencedColumnName = "name")],
    )
    @Column(name = "tag", length = 50)
    var tags: MutableSet<String> = mutableSetOf(),
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LineageNodeEntity) return false
        return name == other.name
    }

    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "LineageNodeEntity(name='$name', type=$type, owner=$owner, team=$team)"
}
