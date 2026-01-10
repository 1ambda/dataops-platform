package com.dataops.basecamp.infra.repository.team

import com.dataops.basecamp.domain.entity.team.TeamEntity
import com.dataops.basecamp.domain.repository.team.TeamRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Team Repository JPA Implementation
 *
 * Implements TeamRepositoryJpa interface using Spring Data JPA.
 */
@Repository("teamRepositoryJpa")
interface TeamRepositoryJpaImpl :
    TeamRepositoryJpa,
    JpaRepository<TeamEntity, Long> {
    // Spring Data JPA auto-implements methods with naming convention

    override fun findByIdAndDeletedAtIsNull(id: Long): TeamEntity?

    override fun findByNameAndDeletedAtIsNull(name: String): TeamEntity?

    override fun findAllByDeletedAtIsNull(): List<TeamEntity>

    override fun existsByNameAndDeletedAtIsNull(name: String): Boolean
}
