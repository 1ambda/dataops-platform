package com.github.lambda.domain.model.adhoc

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.GenericGenerator
import org.hibernate.annotations.UpdateTimestamp
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 사용자별 Ad-Hoc 쿼리 실행 할당량 엔티티
 *
 * Rate limiting을 위한 사용자별 쿼리 실행 횟수를 추적합니다.
 * @Version을 통한 낙관적 잠금으로 동시성 이슈를 방지합니다.
 *
 * Testability: All time-dependent methods accept an optional Clock parameter
 * to allow controlled time in tests.
 */
@Entity
@Table(
    name = "user_execution_quotas",
    indexes = [
        Index(name = "idx_user_exec_quota_user_id", columnList = "user_id", unique = true),
        Index(name = "idx_user_exec_quota_last_query_date", columnList = "last_query_date"),
    ],
)
class UserExecutionQuotaEntity(
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", nullable = false, length = 36)
    var id: String? = null,
    @Column(name = "user_id", nullable = false, unique = true, length = 255)
    var userId: String = "",
    @Column(name = "queries_today", nullable = false)
    var queriesToday: Int = 0,
    @Column(name = "queries_this_hour", nullable = false)
    var queriesThisHour: Int = 0,
    @Column(name = "last_query_date", nullable = false)
    var lastQueryDate: LocalDate = LocalDate.now(),
    @Column(name = "last_query_hour", nullable = false)
    var lastQueryHour: Int = LocalDateTime.now().hour,
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime? = null,
    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,
) {
    // === Business Methods ===

    /**
     * 필요한 경우 할당량을 리셋합니다.
     * 날짜가 바뀌었으면 일간 카운터 리셋, 시간이 바뀌었으면 시간당 카운터 리셋
     *
     * @param clock Clock for time operations (default: system clock)
     */
    fun refreshIfNeeded(clock: Clock = Clock.systemDefaultZone()) {
        val now = LocalDateTime.now(clock)

        // 날짜가 바뀌었으면 일간 카운터 리셋
        if (lastQueryDate != now.toLocalDate()) {
            queriesToday = 0
            lastQueryDate = now.toLocalDate()
        }

        // 시간이 바뀌었으면 시간당 카운터 리셋
        if (lastQueryHour != now.hour) {
            queriesThisHour = 0
            lastQueryHour = now.hour
        }
    }

    /**
     * 사용량 증가
     *
     * @param clock Clock for time operations (default: system clock)
     */
    fun incrementUsage(clock: Clock = Clock.systemDefaultZone()) {
        refreshIfNeeded(clock)
        queriesToday++
        queriesThisHour++
    }

    /**
     * 시간당 제한 체크
     *
     * @param limit Maximum queries allowed per hour
     * @param clock Clock for time operations (default: system clock)
     */
    fun isHourlyLimitExceeded(
        limit: Int,
        clock: Clock = Clock.systemDefaultZone(),
    ): Boolean {
        refreshIfNeeded(clock)
        return queriesThisHour >= limit
    }

    /**
     * 일일 제한 체크
     *
     * @param limit Maximum queries allowed per day
     * @param clock Clock for time operations (default: system clock)
     */
    fun isDailyLimitExceeded(
        limit: Int,
        clock: Clock = Clock.systemDefaultZone(),
    ): Boolean {
        refreshIfNeeded(clock)
        return queriesToday >= limit
    }

    /**
     * 시간당 제한 리셋 시간 계산
     *
     * @param clock Clock for time operations (default: system clock)
     */
    fun getHourlyResetAt(clock: Clock = Clock.systemDefaultZone()): LocalDateTime {
        val now = LocalDateTime.now(clock)
        return now
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .plusHours(1)
    }

    /**
     * 일일 제한 리셋 시간 계산
     *
     * @param clock Clock for time operations (default: system clock)
     */
    fun getDailyResetAt(clock: Clock = Clock.systemDefaultZone()): LocalDateTime {
        val now = LocalDateTime.now(clock)
        return now.toLocalDate().plusDays(1).atStartOfDay()
    }

    companion object {
        /**
         * 새로운 사용자 할당량 생성
         *
         * @param userId User ID
         * @param clock Clock for time operations (default: system clock)
         */
        fun create(
            userId: String,
            clock: Clock = Clock.systemDefaultZone(),
        ): UserExecutionQuotaEntity {
            val now = LocalDateTime.now(clock)
            return UserExecutionQuotaEntity(
                userId = userId,
                queriesToday = 0,
                queriesThisHour = 0,
                lastQueryDate = now.toLocalDate(),
                lastQueryHour = now.hour,
            )
        }
    }
}
