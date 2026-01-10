package com.dataops.basecamp.domain.entity.team

import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * Team Entity
 *
 * Represents a team that owns and manages data resources.
 * Teams organize all data resources including Metric, Dataset, Workflow, Quality, GitHub, Query History, and SQL Snippets.
 */
@Entity
@Table(
    name = "team",
    indexes = [
        Index(name = "idx_team_name", columnList = "name", unique = true),
        Index(name = "idx_team_deleted_at", columnList = "deleted_at"),
    ],
)
class TeamEntity(
    @field:NotBlank
    @field:Size(max = 50)
    @Column(name = "name", nullable = false, unique = true, length = 50)
    var name: String,
    @field:NotBlank
    @field:Size(max = 100)
    @Column(name = "display_name", nullable = false, length = 100)
    var displayName: String,
    @field:Size(max = 500)
    @Column(name = "description", length = 500)
    var description: String? = null,
) : BaseEntity() {
    /**
     * Updates team information.
     */
    fun update(
        displayName: String? = null,
        description: String? = null,
    ) {
        displayName?.let { this.displayName = it }
        description?.let { this.description = it }
    }

    /**
     * Soft deletes the team.
     */
    fun delete(deletedBy: Long) {
        this.deletedAt = LocalDateTime.now()
        this.deletedBy = deletedBy
    }
}
