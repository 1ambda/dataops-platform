package com.dataops.basecamp.infra.repository.flag

import com.dataops.basecamp.domain.entity.flag.FlagEntity
import com.dataops.basecamp.domain.repository.flag.FlagRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Flag JPA Repository 구현 인터페이스
 *
 * Domain FlagRepositoryJpa 인터페이스와 JpaRepository를 모두 확장하는 Simplified Pattern입니다.
 */
@Repository("flagRepositoryJpa")
interface FlagRepositoryJpaImpl :
    FlagRepositoryJpa,
    JpaRepository<FlagEntity, Long> {
    override fun findByFlagKey(flagKey: String): FlagEntity?

    override fun existsByFlagKey(flagKey: String): Boolean
}
