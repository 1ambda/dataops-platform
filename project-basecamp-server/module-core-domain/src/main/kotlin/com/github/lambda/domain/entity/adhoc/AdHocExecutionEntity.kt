package com.github.lambda.domain.entity.adhoc

import com.github.lambda.domain.model.adhoc.ExecutionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Lob
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.GenericGenerator
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDateTime

/**
 * Ad-Hoc 쿼리 실행 기록 엔티티
 *
 * 사용자가 실행한 ad-hoc SQL 쿼리의 실행 이력을 저장합니다.
 */
@Entity
@Table(
    name = "adhoc_executions",
    indexes = [
        Index(name = "idx_adhoc_exec_query_id", columnList = "query_id", unique = true),
        Index(name = "idx_adhoc_exec_user_id", columnList = "user_id"),
        Index(name = "idx_adhoc_exec_status", columnList = "status"),
        Index(name = "idx_adhoc_exec_engine", columnList = "engine"),
        Index(name = "idx_adhoc_exec_created_at", columnList = "created_at"),
        Index(name = "idx_adhoc_exec_expires_at", columnList = "expires_at"),
    ],
)
class AdHocExecutionEntity(
    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Column(name = "id", nullable = false, length = 36)
    var id: String? = null,
    @Column(name = "query_id", nullable = false, unique = true, length = 64)
    var queryId: String = "",
    @Column(name = "user_id", nullable = false, length = 255)
    var userId: String = "",
    @Lob
    @Column(name = "sql_query", nullable = false, columnDefinition = "TEXT")
    var sqlQuery: String = "",
    @Lob
    @Column(name = "rendered_sql", nullable = false, columnDefinition = "TEXT")
    var renderedSql: String = "",
    @Column(name = "engine", nullable = false, length = 50)
    var engine: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: ExecutionStatus = ExecutionStatus.PENDING,
    @Column(name = "rows_returned")
    var rowsReturned: Int? = null,
    @Column(name = "bytes_scanned")
    var bytesScanned: Long? = null,
    @Column(name = "cost_usd", precision = 10, scale = 6)
    var costUsd: BigDecimal? = null,
    @Column(name = "execution_time_seconds")
    var executionTimeSeconds: Double? = null,
    @Column(name = "result_path", length = 500)
    var resultPath: String? = null,
    @Column(name = "error_message", length = 5000)
    var errorMessage: String? = null,
    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null,
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime? = null,
) {
    // === State Transition Methods ===

    /**
     * 실행 시작
     */
    fun startExecution() {
        this.status = ExecutionStatus.RUNNING
    }

    /**
     * 실행 완료
     */
    fun complete(
        rowsReturned: Int,
        bytesScanned: Long,
        costUsd: BigDecimal?,
        executionTimeSeconds: Double,
        resultPath: String?,
        expiresAt: LocalDateTime?,
    ) {
        this.status = ExecutionStatus.COMPLETED
        this.rowsReturned = rowsReturned
        this.bytesScanned = bytesScanned
        this.costUsd = costUsd
        this.executionTimeSeconds = executionTimeSeconds
        this.resultPath = resultPath
        this.expiresAt = expiresAt
    }

    /**
     * 실행 실패
     */
    fun fail(errorMessage: String) {
        this.status = ExecutionStatus.FAILED
        this.errorMessage = errorMessage
    }

    /**
     * 실행 시간 초과
     */
    fun timeout(timeoutSeconds: Int) {
        this.status = ExecutionStatus.TIMEOUT
        this.errorMessage = "Query execution timed out after $timeoutSeconds seconds"
    }

    /**
     * 실행 취소
     */
    fun cancel() {
        this.status = ExecutionStatus.CANCELLED
    }

    /**
     * 결과가 만료되었는지 확인
     *
     * @param clock Clock for time operations (default: system clock)
     */
    fun isExpired(clock: Clock = Clock.systemDefaultZone()): Boolean =
        expiresAt?.isBefore(LocalDateTime.now(clock)) == true

    /**
     * 다운로드 가능한지 확인
     *
     * @param clock Clock for time operations (default: system clock)
     */
    fun canDownload(clock: Clock = Clock.systemDefaultZone()): Boolean =
        status == ExecutionStatus.COMPLETED && !isExpired(clock) && resultPath != null

    companion object {
        /**
         * 새로운 Ad-Hoc 실행 기록 생성
         */
        fun create(
            queryId: String,
            userId: String,
            sqlQuery: String,
            renderedSql: String,
            engine: String,
        ): AdHocExecutionEntity =
            AdHocExecutionEntity(
                queryId = queryId,
                userId = userId,
                sqlQuery = sqlQuery,
                renderedSql = renderedSql,
                engine = engine,
                status = ExecutionStatus.PENDING,
            )

        /**
         * Dry-run 검증 전용 결과 생성
         */
        fun createValidated(
            queryId: String,
            userId: String,
            sqlQuery: String,
            renderedSql: String,
            engine: String,
            executionTimeSeconds: Double,
        ): AdHocExecutionEntity =
            AdHocExecutionEntity(
                queryId = queryId,
                userId = userId,
                sqlQuery = sqlQuery,
                renderedSql = renderedSql,
                engine = engine,
                status = ExecutionStatus.VALIDATED,
                executionTimeSeconds = executionTimeSeconds,
            )
    }
}
