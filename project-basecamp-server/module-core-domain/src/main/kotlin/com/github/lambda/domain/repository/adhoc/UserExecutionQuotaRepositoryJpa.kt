package com.github.lambda.domain.repository.adhoc

import com.github.lambda.domain.entity.adhoc.UserExecutionQuotaEntity
import java.time.LocalDate

/**
 * User Execution Quota Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * 사용자별 Ad-Hoc 쿼리 실행 할당량에 대한 CRUD 작업을 정의합니다.
 */
interface UserExecutionQuotaRepositoryJpa {
    // 기본 CRUD 작업 (JpaRepository에서 상속)
    fun save(quota: UserExecutionQuotaEntity): UserExecutionQuotaEntity

    // 커스텀 조회
    fun findByUserId(userId: String): UserExecutionQuotaEntity?

    fun existsByUserId(userId: String): Boolean

    fun deleteByUserId(userId: String): Long

    // 전체 조회
    fun findAll(): List<UserExecutionQuotaEntity>

    // 날짜 기반 조회 (오래된 데이터 정리용)
    fun findByLastQueryDateBefore(lastQueryDate: LocalDate): List<UserExecutionQuotaEntity>

    // 통계
    fun count(): Long
}
