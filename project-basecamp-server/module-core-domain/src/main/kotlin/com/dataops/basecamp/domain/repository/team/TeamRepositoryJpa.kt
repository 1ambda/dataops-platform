package com.dataops.basecamp.domain.repository.team

import com.dataops.basecamp.domain.entity.team.TeamEntity

/**
 * Team Repository JPA Interface
 *
 * Defines basic CRUD operations for TeamEntity.
 */
interface TeamRepositoryJpa {
    fun save(team: TeamEntity): TeamEntity

    fun findByIdAndDeletedAtIsNull(id: Long): TeamEntity?

    fun findByNameAndDeletedAtIsNull(name: String): TeamEntity?

    fun findAllByDeletedAtIsNull(): List<TeamEntity>

    fun existsByNameAndDeletedAtIsNull(name: String): Boolean
}
