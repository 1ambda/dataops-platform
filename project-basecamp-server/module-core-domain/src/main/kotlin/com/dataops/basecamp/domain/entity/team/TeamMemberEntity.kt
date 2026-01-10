package com.dataops.basecamp.domain.entity.team

import com.dataops.basecamp.common.enums.TeamRole
import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * Team Member Entity
 *
 * Represents a user's membership in a team with a specific role.
 * Uses FK ID reference instead of JPA relationship.
 */
@Entity
@Table(
    name = "team_member",
    indexes = [
        Index(name = "idx_team_member_team_id", columnList = "team_id"),
        Index(name = "idx_team_member_user_id", columnList = "user_id"),
        Index(name = "idx_team_member_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_team_member_team_user",
            columnNames = ["team_id", "user_id"],
        ),
    ],
)
class TeamMemberEntity(
    @Column(name = "team_id", nullable = false)
    val teamId: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    var role: TeamRole = TeamRole.VIEWER,
) : BaseEntity() {
    /**
     * Updates the member's role.
     */
    fun updateRole(newRole: TeamRole) {
        this.role = newRole
    }

    /**
     * Soft deletes the team member.
     */
    fun delete(deletedBy: Long) {
        this.deletedAt = LocalDateTime.now()
        this.deletedBy = deletedBy
    }
}
