package com.github.lambda.domain.service

import com.github.lambda.common.enums.ExecutionStatus
import com.github.lambda.common.exception.AdHocExecutionException
import com.github.lambda.common.exception.InvalidSqlException
import com.github.lambda.common.exception.QueryExecutionTimeoutException
import com.github.lambda.common.exception.ResultSizeLimitExceededException
import com.github.lambda.common.util.QueryUtility
import com.github.lambda.domain.entity.adhoc.AdHocExecutionEntity
import com.github.lambda.domain.external.QueryEngineClient
import com.github.lambda.domain.model.adhoc.RunExecutionConfig
import com.github.lambda.domain.projection.adhoc.AdHocExecutionResultProjection
import com.github.lambda.domain.repository.adhoc.AdHocExecutionRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime

/**
 * Ad-Hoc 실행 서비스
 *
 * Ad-Hoc SQL 쿼리 실행을 담당하는 핵심 도메인 서비스입니다.
 * Pure Hexagonal Architecture 패턴을 따르는 concrete class입니다.
 *
 * Testability: Uses injected Clock and QueryUtility for deterministic behavior.
 */
@Service
@Transactional(readOnly = true)
class AdHocExecutionService(
    private val config: RunExecutionConfig,
    private val executionPolicyService: ExecutionPolicyService,
    private val queryEngineClient: QueryEngineClient,
    private val executionRepositoryJpa: AdHocExecutionRepositoryJpa,
    private val clock: Clock,
    private val queryUtility: QueryUtility,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Ad-Hoc SQL 실행
     */
    @Transactional
    fun executeSQL(
        userId: String,
        sql: String,
        engine: String,
        parameters: Map<String, Any>,
        downloadFormat: String?,
        dryRun: Boolean,
    ): AdHocExecutionResultProjection {
        // 1. 실행 정책 검증
        executionPolicyService.validateExecution(userId, sql, engine)

        // 2. SQL에 파라미터 적용
        val renderedSql = renderSqlWithParameters(sql, parameters)

        // 3. Dry-run 처리
        if (dryRun) {
            return executeDryRun(userId, sql, renderedSql, engine)
        }

        // 4. 쿼리 ID 생성
        val queryId = queryUtility.generate()
        val startTime = clock.millis()

        // 5. 실행 기록 생성
        var execution =
            AdHocExecutionEntity.create(
                queryId = queryId,
                userId = userId,
                sqlQuery = sql,
                renderedSql = renderedSql,
                engine = engine,
            )
        execution.startExecution()
        execution = executionRepositoryJpa.save(execution)

        logger.info("Starting ad-hoc execution: queryId=$queryId, userId=$userId, engine=$engine")

        try {
            // 6. 쿼리 실행
            val result =
                queryEngineClient.execute(
                    sql = renderedSql,
                    engine = engine,
                    timeoutSeconds = config.maxQueryDurationSeconds,
                    maxRows = config.maxResultRows,
                )

            // 7. 결과 크기 검증
            val resultSizeMb = estimateResultSizeMb(result.rows)
            if (resultSizeMb > config.maxResultSizeMb) {
                execution.fail("Result size ${resultSizeMb}MB exceeds limit ${config.maxResultSizeMb}MB")
                executionRepositoryJpa.save(execution)
                throw ResultSizeLimitExceededException(
                    resultSizeMb = resultSizeMb,
                    limitMb = config.maxResultSizeMb,
                )
            }

            val executionTime = (clock.millis() - startTime) / 1000.0
            val expiresAt = LocalDateTime.now(clock).plusHours(config.resultExpirationHours.toLong())

            // 8. 실행 완료 처리
            execution.complete(
                rowsReturned = result.rows.size,
                bytesScanned = result.bytesScanned,
                costUsd = result.costUsd,
                executionTimeSeconds = executionTime,
                resultPath = if (downloadFormat != null) "adhoc-results/$queryId" else null,
                expiresAt = if (downloadFormat != null) expiresAt else null,
            )
            executionRepositoryJpa.save(execution)

            // 9. 사용량 증가
            executionPolicyService.incrementUsage(userId)

            logger.info(
                "Ad-hoc execution completed: queryId=$queryId, rows=${result.rows.size}, time=${executionTime}s",
            )

            return AdHocExecutionResultProjection(
                queryId = queryId,
                status = ExecutionStatus.COMPLETED,
                executionTimeSeconds = executionTime,
                rowsReturned = result.rows.size,
                bytesScanned = result.bytesScanned,
                costUsd = result.costUsd,
                rows = result.rows,
                expiresAt = expiresAt,
                renderedSql = renderedSql,
                downloadFormat = downloadFormat,
            )
        } catch (e: QueryExecutionTimeoutException) {
            execution.timeout(config.maxQueryDurationSeconds)
            executionRepositoryJpa.save(execution)
            logger.error("Ad-hoc execution timed out: queryId=$queryId", e)
            throw e
        } catch (e: ResultSizeLimitExceededException) {
            throw e
        } catch (e: Exception) {
            execution.fail(e.message ?: "Unknown error")
            executionRepositoryJpa.save(execution)
            logger.error("Ad-hoc execution failed: queryId=$queryId", e)
            throw AdHocExecutionException(
                queryId = queryId,
                reason = e.message ?: "Unknown error",
                cause = e,
            )
        }
    }

    /**
     * Dry-run 실행 (검증만)
     */
    private fun executeDryRun(
        userId: String,
        sql: String,
        renderedSql: String,
        engine: String,
    ): AdHocExecutionResultProjection {
        val startTime = clock.millis()

        val validationResult = queryEngineClient.validateSQL(renderedSql, engine)

        if (!validationResult.valid) {
            throw InvalidSqlException(
                sql = sql,
                sqlError = validationResult.errorMessage ?: "Unknown validation error",
            )
        }

        val executionTime = (clock.millis() - startTime) / 1000.0

        logger.debug("Dry-run validation completed: userId=$userId, engine=$engine, time=${executionTime}s")

        return AdHocExecutionResultProjection(
            queryId = null,
            status = ExecutionStatus.VALIDATED,
            executionTimeSeconds = executionTime,
            rowsReturned = 0,
            bytesScanned = null,
            costUsd = null,
            rows = emptyList(),
            expiresAt = null,
            renderedSql = renderedSql,
            downloadFormat = null,
        )
    }

    /**
     * 쿼리 ID로 실행 기록 조회
     */
    fun getExecution(queryId: String): AdHocExecutionEntity? = executionRepositoryJpa.findByQueryId(queryId)

    /**
     * 쿼리 ID로 실행 기록 조회 (존재 필수)
     */
    fun getExecutionOrThrow(queryId: String): AdHocExecutionEntity =
        getExecution(queryId)
            ?: throw com.github.lambda.common.exception
                .ResultNotFoundException(queryId)

    /**
     * SQL에 파라미터 적용
     */
    private fun renderSqlWithParameters(
        sql: String,
        parameters: Map<String, Any>,
    ): String {
        var rendered = sql
        parameters.forEach { (key, value) ->
            val placeholder = "{$key}"
            val replacement =
                when (value) {
                    is String -> "'${escapeString(value)}'"
                    is Number -> value.toString()
                    is Boolean -> value.toString()
                    is List<*> -> value.joinToString(", ") { formatValue(it) }
                    else -> "'${escapeString(value.toString())}'"
                }
            rendered = rendered.replace(placeholder, replacement)
        }
        return rendered
    }

    /**
     * 값을 SQL 형식으로 포맷
     */
    private fun formatValue(value: Any?): String =
        when (value) {
            null -> "NULL"
            is String -> "'${escapeString(value)}'"
            is Number -> value.toString()
            is Boolean -> value.toString()
            else -> "'${escapeString(value.toString())}'"
        }

    /**
     * 문자열 이스케이프
     */
    private fun escapeString(value: String): String = value.replace("'", "''")

    /**
     * 결과 크기 추정 (MB)
     */
    private fun estimateResultSizeMb(rows: List<Map<String, Any?>>): Int {
        val estimatedBytes =
            rows.sumOf { row ->
                row.values.sumOf { value ->
                    when (value) {
                        null -> 4
                        is String -> value.toByteArray(Charsets.UTF_8).size
                        is Number -> 8
                        is Boolean -> 1
                        else -> value.toString().toByteArray(Charsets.UTF_8).size
                    }
                }
            }
        return (estimatedBytes / (1024 * 1024)).toInt()
    }
}
