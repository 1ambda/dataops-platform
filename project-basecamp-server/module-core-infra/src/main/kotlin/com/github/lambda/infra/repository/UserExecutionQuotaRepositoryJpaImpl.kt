package com.github.lambda.infra.repository

import com.github.lambda.domain.model.adhoc.UserExecutionQuotaEntity
import com.github.lambda.domain.repository.UserExecutionQuotaRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDate

/**
 * User Execution Quota JPA Repository 구현
 *
 * Domain UserExecutionQuotaRepositoryJpa 인터페이스와 JpaRepository를 모두 확장하는 Simplified Pattern입니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("userExecutionQuotaRepositoryJpa")
interface UserExecutionQuotaRepositoryJpaImpl :
    UserExecutionQuotaRepositoryJpa,
    JpaRepository<UserExecutionQuotaEntity, String> {
    // 기본 조회 메서드들 (Spring Data JPA auto-implements)
    override fun findByUserId(userId: String): UserExecutionQuotaEntity?

    override fun existsByUserId(userId: String): Boolean

    @Modifying
    @Query("DELETE FROM UserExecutionQuotaEntity q WHERE q.userId = :userId")
    override fun deleteByUserId(
        @Param("userId") userId: String,
    ): Long

    // 날짜 기반 조회 (오래된 데이터 정리용)
    override fun findByLastQueryDateBefore(lastQueryDate: LocalDate): List<UserExecutionQuotaEntity>
}
