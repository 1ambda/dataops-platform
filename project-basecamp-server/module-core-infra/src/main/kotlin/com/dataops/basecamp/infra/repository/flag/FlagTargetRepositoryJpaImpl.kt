package com.dataops.basecamp.infra.repository.flag

import com.dataops.basecamp.domain.entity.flag.FlagTargetEntity
import com.dataops.basecamp.domain.repository.flag.FlagTargetRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * FlagTarget Spring Data JPA Repository
 */
interface FlagTargetRepositoryJpaSpringData : JpaRepository<FlagTargetEntity, Long> {
    fun findByFlagId(flagId: Long): List<FlagTargetEntity>
}

/**
 * FlagTarget JPA Repository 구현체
 *
 * Domain FlagTargetRepositoryJpa 인터페이스를 구현하며, Spring Data를 합성합니다.
 */
@Repository("flagTargetRepositoryJpa")
class FlagTargetRepositoryJpaImpl(
    private val springDataRepository: FlagTargetRepositoryJpaSpringData,
) : FlagTargetRepositoryJpa {
    override fun save(entity: FlagTargetEntity): FlagTargetEntity = springDataRepository.save(entity)

    override fun findById(id: Long): FlagTargetEntity? = springDataRepository.findById(id).orElse(null)

    override fun findByFlagId(flagId: Long): List<FlagTargetEntity> = springDataRepository.findByFlagId(flagId)

    override fun delete(entity: FlagTargetEntity) = springDataRepository.delete(entity)
}
