package com.dataops.basecamp.domain.repository.flag

import com.dataops.basecamp.domain.entity.flag.FlagEntity

/**
 * Flag Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * Flag에 대한 기본 CRUD 작업을 정의합니다.
 */
interface FlagRepositoryJpa {
    fun save(entity: FlagEntity): FlagEntity

    fun findById(id: Long): FlagEntity?

    fun findByFlagKey(flagKey: String): FlagEntity?

    fun existsByFlagKey(flagKey: String): Boolean

    fun findAll(): List<FlagEntity>

    fun delete(entity: FlagEntity)
}
