package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.ExecutionStatus
import com.dataops.basecamp.common.enums.ExecutionType
import com.dataops.basecamp.common.exception.ExecutionException
import com.dataops.basecamp.common.exception.InvalidSqlException
import com.dataops.basecamp.common.exception.QueryEngineNotSupportedException
import com.dataops.basecamp.common.exception.QueryExecutionTimeoutException
import com.dataops.basecamp.common.exception.QueryTooLargeException
import com.dataops.basecamp.common.exception.RateLimitExceededException
import com.dataops.basecamp.common.exception.ResultSizeLimitExceededException
import com.dataops.basecamp.domain.command.execution.DatasetRunParams
import com.dataops.basecamp.domain.command.execution.QualityTestParams
import com.dataops.basecamp.domain.command.execution.SqlExecutionParams
import com.dataops.basecamp.domain.entity.adhoc.UserExecutionQuotaEntity
import com.dataops.basecamp.domain.entity.execution.ExecutionHistoryEntity
import com.dataops.basecamp.domain.entity.execution.ExecutionResultEntity
import com.dataops.basecamp.domain.external.adhoc.RunExecutionConfig
import com.dataops.basecamp.domain.external.parser.BasecampParserClient
import com.dataops.basecamp.domain.external.queryengine.QueryEngineClient
import com.dataops.basecamp.domain.projection.execution.ColumnInfo
import com.dataops.basecamp.domain.projection.execution.CurrentUsageProjection
import com.dataops.basecamp.domain.projection.execution.ExecutionPolicyProjection
import com.dataops.basecamp.domain.projection.execution.QualityExecutionResult
import com.dataops.basecamp.domain.projection.execution.QualityTestResultProjection
import com.dataops.basecamp.domain.projection.execution.QueryExecutionResult
import com.dataops.basecamp.domain.projection.execution.RateLimitsProjection
import com.dataops.basecamp.domain.projection.execution.RenderedExecutionResultProjection
import com.dataops.basecamp.domain.projection.execution.RenderedQualityExecutionResultProjection
import com.dataops.basecamp.domain.repository.adhoc.UserExecutionQuotaRepositoryJpa
import com.dataops.basecamp.domain.repository.dataset.DatasetRepositoryJpa
import com.dataops.basecamp.domain.repository.execution.ExecutionHistoryRepositoryJpa
import com.dataops.basecamp.domain.repository.execution.ExecutionResultRepositoryJpa
import com.dataops.basecamp.domain.repository.quality.QualitySpecRepositoryJpa
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.util.UUID

/**
 * Execution Service
 *
 * Dataset/Quality/Raw SQL 실행을 통합 관리하는 서비스
 * - 실행 이력 저장
 * - 실행 결과 저장
 * - 에러 추적
 */
@Service
@Transactional(readOnly = true)
class ExecutionService(
    private val basecampParserClient: BasecampParserClient,
    private val queryEngineClient: QueryEngineClient,
    private val executionHistoryRepositoryJpa: ExecutionHistoryRepositoryJpa,
    private val executionResultRepositoryJpa: ExecutionResultRepositoryJpa,
    private val datasetRepositoryJpa: DatasetRepositoryJpa,
    private val qualitySpecRepositoryJpa: QualitySpecRepositoryJpa,
    private val quotaRepositoryJpa: UserExecutionQuotaRepositoryJpa,
    private val runExecutionConfig: RunExecutionConfig,
    private val clock: Clock,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Dataset 실행
     */
    @Transactional
    fun executeDataset(
        name: String,
        params: DatasetRunParams,
    ): QueryExecutionResult {
        val executionId = generateExecutionId()
        val startedAt = LocalDateTime.now()

        try {
            // 1. Load dataset
            val dataset =
                datasetRepositoryJpa.findByName(name)
                    ?: throw ExecutionException("EXEC-006", "Dataset not found: $name")

            // 2. Transpile SQL
            val transpileResponse =
                basecampParserClient.transpileSQL(
                    sql = dataset.sql,
                    sourceDialect = "trino",
                    targetDialect = params.dialect.name.lowercase(),
                )

            if (!transpileResponse.success) {
                throw ExecutionException("EXEC-001", transpileResponse.errorMessage ?: "Transpile failed")
            }

            val transpiledSql = transpileResponse.transpiledSql

            // 3. Execute query
            val queryResponse =
                queryEngineClient.execute(
                    sql = transpiledSql,
                    engine = params.dialect.name.lowercase(),
                    timeoutSeconds = 300,
                    maxRows = params.limit ?: 10000,
                )

            val completedAt = LocalDateTime.now()
            val durationMs =
                java.time.Duration
                    .between(startedAt, completedAt)
                    .toMillis()

            // 4. Save history
            saveHistory(
                executionId = executionId,
                type = ExecutionType.DATASET,
                resourceName = name,
                status = ExecutionStatus.SUCCESS,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = params.userId,
                transpiledSql = transpiledSql,
                parameters = objectMapper.writeValueAsString(params.parameters),
                reason = params.reason,
                dialect = params.dialect,
            )

            // 5. Save result
            if (queryResponse.rows.isNotEmpty()) {
                saveResult(executionId, queryResponse.rows, extractSchema(queryResponse.rows))
            }

            return QueryExecutionResult(
                executionId = executionId,
                status = ExecutionStatus.SUCCESS,
                rows = queryResponse.rows,
                rowCount = queryResponse.rows.size,
                schema = extractSchema(queryResponse.rows),
                executionTimeMs = durationMs,
                transpiledSql = transpiledSql,
                error = null,
            )
        } catch (e: ExecutionException) {
            saveFailureHistory(executionId, ExecutionType.DATASET, name, startedAt, params, e)
            throw e
        } catch (e: Exception) {
            val execException = ExecutionException("EXEC-003", "Query execution failed: ${e.message}")
            saveFailureHistory(executionId, ExecutionType.DATASET, name, startedAt, params, execException)
            throw execException
        }
    }

    /**
     * Quality 실행 (Placeholder - Phase 3에서 실제 구현)
     */
    @Transactional
    fun executeQuality(
        resourceName: String,
        params: QualityTestParams,
    ): QualityExecutionResult {
        val executionId = generateExecutionId()

        // TODO: Phase 3에서 실제 Quality 실행 로직 구현
        return QualityExecutionResult(
            executionId = executionId,
            status = ExecutionStatus.SUCCESS,
            passed = true,
            totalTests = 0,
            passedTests = 0,
            failedTests = 0,
            failures = emptyList(),
            executionTimeMs = 0,
            error = null,
        )
    }

    // === Execution Policy Methods (ExecutionPolicyService integrated) ===

    /**
     * 사용자의 실행 정책 조회
     *
     * @param userId 사용자 ID
     * @return 실행 정책 및 현재 사용량
     */
    fun getExecutionPolicy(userId: String): ExecutionPolicyProjection {
        val quota = getOrCreateQuota(userId)
        quota.refreshIfNeeded(clock)

        return ExecutionPolicyProjection(
            maxQueryDurationSeconds = runExecutionConfig.maxQueryDurationSeconds,
            maxResultRows = runExecutionConfig.maxResultRows,
            maxResultSizeMb = runExecutionConfig.maxResultSizeMb,
            allowedEngines = runExecutionConfig.allowedEngines,
            allowedFileTypes = runExecutionConfig.allowedFileTypes,
            maxFileSizeMb = runExecutionConfig.maxFileSizeMb,
            rateLimits =
                RateLimitsProjection(
                    queriesPerHour = runExecutionConfig.queriesPerHour,
                    queriesPerDay = runExecutionConfig.queriesPerDay,
                ),
            currentUsage =
                CurrentUsageProjection(
                    queriesToday = quota.queriesToday,
                    queriesThisHour = quota.queriesThisHour,
                ),
        )
    }

    // === Execution Methods ===

    /**
     * Raw SQL 실행 (AdHocExecutionService 통합)
     */
    @Transactional
    fun executeRawSql(params: SqlExecutionParams): QueryExecutionResult {
        // 1. ExecutionPolicy 검증 (rate limiting, engine validation)
        validateExecution(
            params.userId.toString(),
            params.sql,
            params.dialect.name.lowercase(),
        )

        // 2. SQL에 파라미터 적용
        val renderedSql = renderSqlWithParameters(params.sql, params.parameters)

        // 3. Dry-run 처리
        if (params.dryRun) {
            return executeDryRun(params, renderedSql)
        }

        val executionId = generateExecutionId()
        val startedAt = LocalDateTime.now(clock)
        val startTime = clock.millis()

        try {
            // 4. Query 실행
            val queryResponse =
                queryEngineClient.execute(
                    sql = renderedSql,
                    engine = params.dialect.name.lowercase(),
                    timeoutSeconds = runExecutionConfig.maxQueryDurationSeconds,
                    maxRows = runExecutionConfig.maxResultRows,
                )

            // 5. 결과 크기 검증
            val resultSizeMb = estimateResultSizeMb(queryResponse.rows)
            if (resultSizeMb > runExecutionConfig.maxResultSizeMb) {
                throw ResultSizeLimitExceededException(
                    resultSizeMb = resultSizeMb,
                    limitMb = runExecutionConfig.maxResultSizeMb,
                )
            }

            val completedAt = LocalDateTime.now(clock)
            val durationMs = clock.millis() - startTime

            // 6. History 저장
            saveHistory(
                executionId = executionId,
                type = ExecutionType.RAW_SQL,
                resourceName = null,
                status = ExecutionStatus.SUCCESS,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = params.userId,
                transpiledSql = renderedSql,
                parameters = objectMapper.writeValueAsString(params.parameters),
                reason = params.reason,
                dialect = params.dialect,
            )

            // 7. Result 저장
            if (queryResponse.rows.isNotEmpty()) {
                saveResult(executionId, queryResponse.rows, extractSchema(queryResponse.rows))
            }

            // 8. 사용량 증가
            incrementUsage(params.userId.toString())

            logger.info(
                "Raw SQL execution completed: executionId=$executionId, rows=${queryResponse.rows.size}, time=${durationMs}ms",
            )

            return QueryExecutionResult(
                executionId = executionId,
                status = ExecutionStatus.SUCCESS,
                rows = queryResponse.rows,
                rowCount = queryResponse.rows.size,
                schema = extractSchema(queryResponse.rows),
                executionTimeMs = durationMs,
                transpiledSql = renderedSql,
                error = null,
            )
        } catch (e: QueryExecutionTimeoutException) {
            logger.error("Raw SQL execution timed out: executionId=$executionId", e)
            val completedAt = LocalDateTime.now(clock)
            val durationMs = clock.millis() - startTime
            saveHistory(
                executionId = executionId,
                type = ExecutionType.RAW_SQL,
                resourceName = null,
                status = ExecutionStatus.TIMEOUT,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = params.userId,
                transpiledSql = renderedSql,
                parameters = objectMapper.writeValueAsString(params.parameters),
                reason = params.reason,
                dialect = params.dialect,
                errorCode = "TIMEOUT",
                errorMessage = e.message,
            )
            throw e
        } catch (e: ResultSizeLimitExceededException) {
            logger.error("Raw SQL execution result size exceeded: executionId=$executionId", e)
            val completedAt = LocalDateTime.now(clock)
            val durationMs = clock.millis() - startTime
            saveHistory(
                executionId = executionId,
                type = ExecutionType.RAW_SQL,
                resourceName = null,
                status = ExecutionStatus.FAILED,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = params.userId,
                transpiledSql = renderedSql,
                parameters = objectMapper.writeValueAsString(params.parameters),
                reason = params.reason,
                dialect = params.dialect,
                errorCode = "RESULT_SIZE_EXCEEDED",
                errorMessage = e.message,
            )
            throw e
        } catch (e: Exception) {
            logger.error("Raw SQL execution failed: executionId=$executionId", e)
            val execException = ExecutionException("EXEC-003", "Raw SQL execution failed: ${e.message}")
            saveFailureHistory(executionId, ExecutionType.RAW_SQL, null, startedAt, params, execException)
            throw execException
        }
    }

    /**
     * Dry-run 실행 (검증만)
     */
    private fun executeDryRun(
        params: SqlExecutionParams,
        renderedSql: String,
    ): QueryExecutionResult {
        val startTime = clock.millis()

        val validationResult =
            queryEngineClient.validateSQL(
                renderedSql,
                params.dialect.name.lowercase(),
            )

        if (!validationResult.valid) {
            throw InvalidSqlException(
                sql = params.sql,
                sqlError = validationResult.errorMessage ?: "Unknown validation error",
            )
        }

        val durationMs = clock.millis() - startTime

        logger.debug(
            "Dry-run validation completed: userId=${params.userId}, dialect=${params.dialect}, time=${durationMs}ms",
        )

        return QueryExecutionResult(
            executionId = "", // Dry-run doesn't create execution history
            status = ExecutionStatus.SUCCESS,
            rows = emptyList(),
            rowCount = 0,
            schema = emptyList(),
            executionTimeMs = durationMs,
            transpiledSql = renderedSql,
            error = null,
        )
    }

    // === Private Helper Methods ===

    private fun generateExecutionId(): String =
        "exec_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"

    private fun saveHistory(
        executionId: String,
        type: ExecutionType,
        resourceName: String?,
        status: ExecutionStatus,
        startedAt: LocalDateTime,
        completedAt: LocalDateTime,
        durationMs: Long,
        userId: Long,
        transpiledSql: String,
        parameters: String?,
        reason: String?,
        dialect: com.dataops.basecamp.common.enums.SqlDialect,
        errorCode: String? = null,
        errorMessage: String? = null,
    ): ExecutionHistoryEntity {
        val history =
            ExecutionHistoryEntity(
                executionId = executionId,
                executionType = type,
                resourceName = resourceName,
                status = status,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = userId,
                transpiledSql = transpiledSql,
                parameters = parameters,
                reason = reason,
                dialect = dialect,
                errorCode = errorCode,
                errorMessage = errorMessage,
            )
        return executionHistoryRepositoryJpa.save(history)
    }

    private fun saveFailureHistory(
        executionId: String,
        type: ExecutionType,
        resourceName: String?,
        startedAt: LocalDateTime,
        params: Any,
        exception: ExecutionException,
    ) {
        val completedAt = LocalDateTime.now()
        val durationMs =
            java.time.Duration
                .between(startedAt, completedAt)
                .toMillis()

        val userId =
            when (params) {
                is DatasetRunParams -> params.userId
                is QualityTestParams -> params.userId
                is SqlExecutionParams -> params.userId
                else -> 0L
            }

        val dialect =
            when (params) {
                is DatasetRunParams -> params.dialect
                is SqlExecutionParams -> params.dialect
                else -> com.dataops.basecamp.common.enums.SqlDialect.BIGQUERY
            }

        executionHistoryRepositoryJpa.save(
            ExecutionHistoryEntity(
                executionId = executionId,
                executionType = type,
                resourceName = resourceName,
                status = ExecutionStatus.FAILED,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = userId,
                transpiledSql = "",
                parameters = null,
                reason = null,
                dialect = dialect,
                errorCode = exception.errorCode,
                errorMessage = exception.message,
            ),
        )
    }

    private fun saveResult(
        executionId: String,
        rows: List<Map<String, Any?>>,
        schema: List<ColumnInfo>,
    ): ExecutionResultEntity {
        val result =
            ExecutionResultEntity(
                executionId = executionId,
                resultData = objectMapper.writeValueAsString(rows),
                rowCount = rows.size,
                schema = objectMapper.writeValueAsString(schema),
            )
        return executionResultRepositoryJpa.save(result)
    }

    private fun extractSchema(rows: List<Map<String, Any?>>): List<ColumnInfo> {
        if (rows.isEmpty()) return emptyList()

        return rows.first().map { (name, value) ->
            ColumnInfo(
                name = name,
                type = value?.javaClass?.simpleName ?: "Unknown",
                nullable = value == null,
            )
        }
    }

    /**
     * SQL에 파라미터 적용 (AdHocExecutionService에서 이관)
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

    // === Execution Policy Methods (ExecutionPolicyService integrated) ===

    /**
     * 실행 요청 검증
     *
     * @throws RateLimitExceededException Rate limit 초과 시
     * @throws QueryEngineNotSupportedException 지원하지 않는 엔진 시
     * @throws QueryTooLargeException SQL 크기 초과 시
     */
    @Transactional
    private fun validateExecution(
        userId: String,
        sql: String,
        engine: String,
    ) {
        val quota = getOrCreateQuota(userId)
        quota.refreshIfNeeded(clock)

        // 시간당 제한 체크
        if (quota.isHourlyLimitExceeded(runExecutionConfig.queriesPerHour, clock)) {
            logger.warn(
                "User $userId exceeded hourly rate limit: ${quota.queriesThisHour}/${runExecutionConfig.queriesPerHour}",
            )
            throw RateLimitExceededException(
                limitType = "queries_per_hour",
                limit = runExecutionConfig.queriesPerHour,
                currentUsage = quota.queriesThisHour,
                resetAt = quota.getHourlyResetAt(clock),
            )
        }

        // 일일 제한 체크
        if (quota.isDailyLimitExceeded(runExecutionConfig.queriesPerDay, clock)) {
            logger.warn(
                "User $userId exceeded daily rate limit: ${quota.queriesToday}/${runExecutionConfig.queriesPerDay}",
            )
            throw RateLimitExceededException(
                limitType = "queries_per_day",
                limit = runExecutionConfig.queriesPerDay,
                currentUsage = quota.queriesToday,
                resetAt = quota.getDailyResetAt(clock),
            )
        }

        // 엔진 검증
        val normalizedEngine = engine.lowercase()
        if (normalizedEngine !in runExecutionConfig.allowedEngines.map { it.lowercase() }) {
            logger.warn("User $userId requested unsupported engine: $engine")
            throw QueryEngineNotSupportedException(
                engine = engine,
                allowedEngines = runExecutionConfig.allowedEngines,
            )
        }

        // SQL 크기 검증
        val sqlSizeBytes = sql.toByteArray(Charsets.UTF_8).size.toLong()
        val maxSizeBytes = runExecutionConfig.maxFileSizeMb * 1024L * 1024L
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
    private fun incrementUsage(userId: String) {
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

    // === CLI-Rendered Execution Methods ===

    /**
     * CLI에서 렌더링된 Dataset SQL 실행
     *
     * @param params 렌더링된 Dataset 실행 파라미터
     * @return 실행 결과
     */
    @Transactional
    fun executeRenderedDatasetSql(
        params: com.dataops.basecamp.domain.command.execution.RenderedDatasetExecutionParams,
    ): RenderedExecutionResultProjection {
        val executionId = generateExecutionId()
        val startedAt = LocalDateTime.now(clock)
        val startTime = clock.millis()

        try {
            // 1. Validate execution policy
            val targetDialect = params.transpileTargetDialect ?: "bigquery"
            validateExecution(
                params.userId.toString(),
                params.renderedSql,
                targetDialect,
            )

            // 2. Execute query
            val queryResponse =
                queryEngineClient.execute(
                    sql = params.renderedSql,
                    engine = targetDialect,
                    timeoutSeconds = params.executionTimeout,
                    maxRows = params.executionLimit ?: runExecutionConfig.maxResultRows,
                )

            val completedAt = LocalDateTime.now(clock)
            val durationMs = clock.millis() - startTime
            val durationSeconds = durationMs / 1000.0

            // 3. Save history
            saveHistory(
                executionId = executionId,
                type = ExecutionType.DATASET,
                resourceName = params.resourceName,
                status = ExecutionStatus.SUCCESS,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = params.userId,
                transpiledSql = params.renderedSql,
                parameters = objectMapper.writeValueAsString(params.parameters),
                reason = "CLI-rendered Dataset execution",
                dialect =
                    com.dataops.basecamp.common.enums.SqlDialect
                        .valueOf(targetDialect.uppercase()),
            )

            // 4. Save result
            if (queryResponse.rows.isNotEmpty()) {
                saveResult(executionId, queryResponse.rows, extractSchema(queryResponse.rows))
            }

            // 5. Increment usage
            incrementUsage(params.userId.toString())

            logger.info(
                "Rendered Dataset SQL execution completed: executionId=$executionId, rows=${queryResponse.rows.size}, time=${durationMs}ms",
            )

            return RenderedExecutionResultProjection(
                executionId = executionId,
                status = ExecutionStatus.SUCCESS,
                rows = queryResponse.rows,
                rowCount = queryResponse.rows.size,
                durationSeconds = durationSeconds,
                renderedSql = params.renderedSql,
                error = null,
            )
        } catch (e: Exception) {
            val completedAt = LocalDateTime.now(clock)
            val durationMs = clock.millis() - startTime

            saveHistory(
                executionId = executionId,
                type = ExecutionType.DATASET,
                resourceName = params.resourceName,
                status = ExecutionStatus.FAILED,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = params.userId,
                transpiledSql = params.renderedSql,
                parameters = objectMapper.writeValueAsString(params.parameters),
                reason = "CLI-rendered Dataset execution",
                dialect =
                    com.dataops.basecamp.common.enums.SqlDialect.valueOf(
                        (params.transpileTargetDialect ?: "bigquery").uppercase(),
                    ),
                errorCode = "EXEC-003",
                errorMessage = e.message,
            )

            logger.error("Rendered Dataset SQL execution failed: executionId=$executionId", e)

            return RenderedExecutionResultProjection(
                executionId = executionId,
                status = ExecutionStatus.FAILED,
                rows = null,
                rowCount = null,
                durationSeconds = durationMs / 1000.0,
                renderedSql = params.renderedSql,
                error = e.message,
            )
        }
    }

    /**
     * CLI에서 렌더링된 Metric SQL 실행
     *
     * @param params 렌더링된 Metric 실행 파라미터
     * @return 실행 결과
     */
    @Transactional
    fun executeRenderedMetricSql(
        params: com.dataops.basecamp.domain.command.execution.RenderedMetricExecutionParams,
    ): RenderedExecutionResultProjection {
        val executionId = generateExecutionId()
        val startedAt = LocalDateTime.now(clock)
        val startTime = clock.millis()

        try {
            // 1. Validate execution policy
            val targetDialect = params.transpileTargetDialect ?: "bigquery"
            validateExecution(
                params.userId.toString(),
                params.renderedSql,
                targetDialect,
            )

            // 2. Execute query
            val queryResponse =
                queryEngineClient.execute(
                    sql = params.renderedSql,
                    engine = targetDialect,
                    timeoutSeconds = params.executionTimeout,
                    maxRows = params.executionLimit ?: runExecutionConfig.maxResultRows,
                )

            val completedAt = LocalDateTime.now(clock)
            val durationMs = clock.millis() - startTime
            val durationSeconds = durationMs / 1000.0

            // 3. Save history
            saveHistory(
                executionId = executionId,
                type = ExecutionType.METRIC,
                resourceName = params.resourceName,
                status = ExecutionStatus.SUCCESS,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = params.userId,
                transpiledSql = params.renderedSql,
                parameters = objectMapper.writeValueAsString(params.parameters),
                reason = "CLI-rendered Metric execution",
                dialect =
                    com.dataops.basecamp.common.enums.SqlDialect
                        .valueOf(targetDialect.uppercase()),
            )

            // 4. Save result
            if (queryResponse.rows.isNotEmpty()) {
                saveResult(executionId, queryResponse.rows, extractSchema(queryResponse.rows))
            }

            // 5. Increment usage
            incrementUsage(params.userId.toString())

            logger.info(
                "Rendered Metric SQL execution completed: executionId=$executionId, rows=${queryResponse.rows.size}, time=${durationMs}ms",
            )

            return RenderedExecutionResultProjection(
                executionId = executionId,
                status = ExecutionStatus.SUCCESS,
                rows = queryResponse.rows,
                rowCount = queryResponse.rows.size,
                durationSeconds = durationSeconds,
                renderedSql = params.renderedSql,
                error = null,
            )
        } catch (e: Exception) {
            val completedAt = LocalDateTime.now(clock)
            val durationMs = clock.millis() - startTime

            saveHistory(
                executionId = executionId,
                type = ExecutionType.METRIC,
                resourceName = params.resourceName,
                status = ExecutionStatus.FAILED,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = params.userId,
                transpiledSql = params.renderedSql,
                parameters = objectMapper.writeValueAsString(params.parameters),
                reason = "CLI-rendered Metric execution",
                dialect =
                    com.dataops.basecamp.common.enums.SqlDialect.valueOf(
                        (params.transpileTargetDialect ?: "bigquery").uppercase(),
                    ),
                errorCode = "EXEC-003",
                errorMessage = e.message,
            )

            logger.error("Rendered Metric SQL execution failed: executionId=$executionId", e)

            return RenderedExecutionResultProjection(
                executionId = executionId,
                status = ExecutionStatus.FAILED,
                rows = null,
                rowCount = null,
                durationSeconds = durationMs / 1000.0,
                renderedSql = params.renderedSql,
                error = e.message,
            )
        }
    }

    /**
     * CLI에서 렌더링된 Quality SQL 실행
     *
     * @param params 렌더링된 Quality 실행 파라미터
     * @return 실행 결과
     */
    @Transactional
    fun executeRenderedQualitySql(
        params: com.dataops.basecamp.domain.command.execution.RenderedQualityExecutionParams,
    ): RenderedQualityExecutionResultProjection {
        val executionId = generateExecutionId()
        val startTime = clock.millis()
        val targetDialect = params.transpileTargetDialect ?: "bigquery"

        val testResults = mutableListOf<QualityTestResultProjection>()
        var totalPassed = 0
        var totalFailed = 0

        // Execute each test
        for (test in params.tests) {
            val testStartTime = clock.millis()
            try {
                val queryResponse =
                    queryEngineClient.execute(
                        sql = test.renderedSql,
                        engine = targetDialect,
                        timeoutSeconds = params.executionTimeout,
                        maxRows = 100, // Limit failed rows to 100
                    )

                val testDurationMs = clock.millis() - testStartTime
                val failedCount = queryResponse.rows.size

                // Quality test passes if no rows are returned (no violations)
                val passed = failedCount == 0
                if (passed) {
                    totalPassed++
                } else {
                    totalFailed++
                }

                @Suppress("UNCHECKED_CAST")
                testResults.add(
                    QualityTestResultProjection(
                        testName = test.name,
                        passed = passed,
                        failedCount = failedCount,
                        failedRows = if (passed) null else queryResponse.rows as List<Map<String, Any>>,
                        durationMs = testDurationMs,
                    ),
                )
            } catch (e: Exception) {
                val testDurationMs = clock.millis() - testStartTime
                totalFailed++

                testResults.add(
                    QualityTestResultProjection(
                        testName = test.name,
                        passed = false,
                        failedCount = -1, // Indicates error
                        failedRows = null,
                        durationMs = testDurationMs,
                    ),
                )

                logger.warn("Quality test '${test.name}' failed with error: ${e.message}")
            }
        }

        val totalDurationMs = clock.millis() - startTime
        val overallStatus = if (totalFailed == 0) ExecutionStatus.SUCCESS else ExecutionStatus.FAILED

        logger.info(
            "Rendered Quality execution completed: executionId=$executionId, " +
                "passed=$totalPassed, failed=$totalFailed, time=${totalDurationMs}ms",
        )

        return RenderedQualityExecutionResultProjection(
            executionId = executionId,
            status = overallStatus,
            results = testResults,
            totalTests = params.tests.size,
            passedTests = totalPassed,
            failedTests = totalFailed,
            totalDurationMs = totalDurationMs,
        )
    }

    /**
     * CLI에서 전달받은 Ad-hoc SQL 실행
     *
     * @param params 렌더링된 SQL 실행 파라미터
     * @return 실행 결과
     */
    @Transactional
    fun executeRenderedAdHocSql(
        params: com.dataops.basecamp.domain.command.execution.RenderedSqlExecutionParams,
    ): RenderedExecutionResultProjection {
        val executionId = generateExecutionId()
        val startedAt = LocalDateTime.now(clock)
        val startTime = clock.millis()

        try {
            // 1. Validate execution policy
            val targetDialect = params.targetDialect ?: "bigquery"
            validateExecution(
                params.userId.toString(),
                params.sql,
                targetDialect,
            )

            // 2. Apply parameters to SQL
            val renderedSql = renderSqlWithParameters(params.sql, params.parameters)

            // 3. Execute query
            val queryResponse =
                queryEngineClient.execute(
                    sql = renderedSql,
                    engine = targetDialect,
                    timeoutSeconds = params.executionTimeout,
                    maxRows = params.executionLimit ?: runExecutionConfig.maxResultRows,
                )

            val completedAt = LocalDateTime.now(clock)
            val durationMs = clock.millis() - startTime
            val durationSeconds = durationMs / 1000.0

            // 4. Validate result size
            val resultSizeMb = estimateResultSizeMb(queryResponse.rows)
            if (resultSizeMb > runExecutionConfig.maxResultSizeMb) {
                throw ResultSizeLimitExceededException(
                    resultSizeMb = resultSizeMb,
                    limitMb = runExecutionConfig.maxResultSizeMb,
                )
            }

            // 5. Save history
            saveHistory(
                executionId = executionId,
                type = ExecutionType.RAW_SQL,
                resourceName = null,
                status = ExecutionStatus.SUCCESS,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = params.userId,
                transpiledSql = renderedSql,
                parameters = objectMapper.writeValueAsString(params.parameters),
                reason = "CLI Ad-hoc SQL execution",
                dialect =
                    com.dataops.basecamp.common.enums.SqlDialect
                        .valueOf(targetDialect.uppercase()),
            )

            // 6. Save result
            if (queryResponse.rows.isNotEmpty()) {
                saveResult(executionId, queryResponse.rows, extractSchema(queryResponse.rows))
            }

            // 7. Increment usage
            incrementUsage(params.userId.toString())

            logger.info(
                "Rendered Ad-hoc SQL execution completed: executionId=$executionId, rows=${queryResponse.rows.size}, time=${durationMs}ms",
            )

            return RenderedExecutionResultProjection(
                executionId = executionId,
                status = ExecutionStatus.SUCCESS,
                rows = queryResponse.rows,
                rowCount = queryResponse.rows.size,
                durationSeconds = durationSeconds,
                renderedSql = renderedSql,
                error = null,
            )
        } catch (e: Exception) {
            val completedAt = LocalDateTime.now(clock)
            val durationMs = clock.millis() - startTime

            saveHistory(
                executionId = executionId,
                type = ExecutionType.RAW_SQL,
                resourceName = null,
                status = ExecutionStatus.FAILED,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                userId = params.userId,
                transpiledSql = params.sql,
                parameters = objectMapper.writeValueAsString(params.parameters),
                reason = "CLI Ad-hoc SQL execution",
                dialect =
                    com.dataops.basecamp.common.enums.SqlDialect.valueOf(
                        (params.targetDialect ?: "bigquery").uppercase(),
                    ),
                errorCode = "EXEC-003",
                errorMessage = e.message,
            )

            logger.error("Rendered Ad-hoc SQL execution failed: executionId=$executionId", e)

            return RenderedExecutionResultProjection(
                executionId = executionId,
                status = ExecutionStatus.FAILED,
                rows = null,
                rowCount = null,
                durationSeconds = durationMs / 1000.0,
                renderedSql = params.sql,
                error = e.message,
            )
        }
    }
}
