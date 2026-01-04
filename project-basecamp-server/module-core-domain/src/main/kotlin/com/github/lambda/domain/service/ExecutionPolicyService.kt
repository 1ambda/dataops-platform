package com.github.lambda.domain.service

import com.github.lambda.common.exception.QueryEngineNotSupportedException
import com.github.lambda.common.exception.QueryTooLargeException
import com.github.lambda.common.exception.RateLimitExceededException
import com.github.lambda.domain.entity.adhoc.UserExecutionQuotaEntity
import com.github.lambda.domain.model.adhoc.RunExecutionConfig
import com.github.lambda.domain.projection.execution.*
import com.github.lambda.domain.repository.adhoc.UserExecutionQuotaRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 실행 정책 서비스
 *
 * Ad-Hoc 쿼리 실행에 대한 Rate Limiting과 정책 검증을 담당합니다.
 * Pure Hexagonal Architecture 패턴을 따르는 concrete class입니다.
 *
 * Testability: Uses injected Clock for all time-dependent operations.
 */
@Service
@Transactional(readOnly = true)
class ExecutionPolicyService(
    private val config: RunExecutionConfig,
    private val quotaRepositoryJpa: UserExecutionQuotaRepositoryJpa,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 사용자의 실행 정책 조회
     */
    fun getPolicy(userId: String): ExecutionPolicyProjection {
        val quota = getOrCreateQuota(userId)
        quota.refreshIfNeeded(clock)

        return ExecutionPolicyProjection(
            maxQueryDurationSeconds = config.maxQueryDurationSeconds,
            maxResultRows = config.maxResultRows,
            maxResultSizeMb = config.maxResultSizeMb,
            allowedEngines = config.allowedEngines,
            allowedFileTypes = config.allowedFileTypes,
            maxFileSizeMb = config.maxFileSizeMb,
            rateLimits =
                RateLimitsProjection(
                    queriesPerHour = config.queriesPerHour,
                    queriesPerDay = config.queriesPerDay,
                ),
            currentUsage =
                CurrentUsageProjection(
                    queriesToday = quota.queriesToday,
                    queriesThisHour = quota.queriesThisHour,
                ),
        )
    }

    /**
     * 실행 요청 검증
     *
     * @throws RateLimitExceededException Rate limit 초과 시
     * @throws QueryEngineNotSupportedException 지원하지 않는 엔진 시
     * @throws QueryTooLargeException SQL 크기 초과 시
     */
    @Transactional
    fun validateExecution(
        userId: String,
        sql: String,
        engine: String,
    ) {
        val quota = getOrCreateQuota(userId)
        quota.refreshIfNeeded(clock)

        // 시간당 제한 체크
        if (quota.isHourlyLimitExceeded(config.queriesPerHour, clock)) {
            logger.warn("User $userId exceeded hourly rate limit: ${quota.queriesThisHour}/${config.queriesPerHour}")
            throw RateLimitExceededException(
                limitType = "queries_per_hour",
                limit = config.queriesPerHour,
                currentUsage = quota.queriesThisHour,
                resetAt = quota.getHourlyResetAt(clock),
            )
        }

        // 일일 제한 체크
        if (quota.isDailyLimitExceeded(config.queriesPerDay, clock)) {
            logger.warn("User $userId exceeded daily rate limit: ${quota.queriesToday}/${config.queriesPerDay}")
            throw RateLimitExceededException(
                limitType = "queries_per_day",
                limit = config.queriesPerDay,
                currentUsage = quota.queriesToday,
                resetAt = quota.getDailyResetAt(clock),
            )
        }

        // 엔진 검증
        val normalizedEngine = engine.lowercase()
        if (normalizedEngine !in config.allowedEngines.map { it.lowercase() }) {
            logger.warn("User $userId requested unsupported engine: $engine")
            throw QueryEngineNotSupportedException(
                engine = engine,
                allowedEngines = config.allowedEngines,
            )
        }

        // SQL 크기 검증
        val sqlSizeBytes = sql.toByteArray(Charsets.UTF_8).size.toLong()
        val maxSizeBytes = config.maxFileSizeMb * 1024L * 1024L
        if (sqlSizeBytes > maxSizeBytes) {
            logger.warn("User $userId submitted query too large: $sqlSizeBytes bytes > $maxSizeBytes bytes")
            throw QueryTooLargeException(
                actualSizeBytes = sqlSizeBytes,
                maxSizeBytes = maxSizeBytes,
            )
        }

        // 할당량 저장 (refreshIfNeeded가 상태를 변경했을 수 있음)
        quotaRepositoryJpa.save(quota)
    }

    /**
     * 사용량 증가
     */
    @Transactional
    fun incrementUsage(userId: String) {
        val quota = getOrCreateQuota(userId)
        quota.incrementUsage(clock)
        quotaRepositoryJpa.save(quota)
        logger.debug("User $userId usage incremented: hour=${quota.queriesThisHour}, day=${quota.queriesToday}")
    }

    /**
     * 사용자 할당량 조회 또는 생성
     */
    @Transactional
    private fun getOrCreateQuota(userId: String): UserExecutionQuotaEntity =
        quotaRepositoryJpa.findByUserId(userId)
            ?: quotaRepositoryJpa.save(UserExecutionQuotaEntity.create(userId, clock))

    /**
     * 설정값 조회 (테스트용)
     */
    fun getConfig(): RunExecutionConfig = config
}
