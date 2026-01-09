package com.dataops.basecamp.domain.repository.flag

import com.dataops.basecamp.domain.entity.flag.FlagTargetEntity

/**
 * FlagTarget Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * FlagTarget에 대한 기본 CRUD 작업을 정의합니다.
 */
interface FlagTargetRepositoryJpa {
    fun save(entity: FlagTargetEntity): FlagTargetEntity

    fun findById(id: Long): FlagTargetEntity?

    fun findByFlagId(flagId: Long): List<FlagTargetEntity>

    fun delete(entity: FlagTargetEntity)
}
