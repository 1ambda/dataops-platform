package com.dataops.basecamp.domain.entity.sql

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * SQL Worksheet Entity
 *
 * SQL Worksheet을 저장하는 엔티티입니다.
 * - Folder에 종속되며, folderId로 FK를 관리합니다.
 * - Soft delete를 지원합니다.
 */
@Entity
@Table(
    name = "sql_worksheet",
    indexes = [
        Index(name = "idx_sql_worksheet_folder_id", columnList = "folder_id"),
        Index(name = "idx_sql_worksheet_name", columnList = "name"),
        Index(name = "idx_sql_worksheet_starred", columnList = "is_starred"),
        Index(name = "idx_sql_worksheet_deleted_at", columnList = "deleted_at"),
    ],
)
class SqlWorksheetEntity(
    @Column(name = "folder_id", nullable = false)
    val folderId: Long,
    @field:NotBlank(message = "Worksheet name is required")
    @field:Size(max = 200, message = "Worksheet name must not exceed 200 characters")
    @Column(name = "name", nullable = false, length = 200)
    var name: String,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    var description: String? = null,
    @field:NotBlank(message = "SQL text is required")
    @Column(name = "sql_text", nullable = false, columnDefinition = "TEXT")
    var sqlText: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "dialect", nullable = false, length = 20)
    var dialect: SqlDialect = SqlDialect.BIGQUERY,
    @Column(name = "run_count", nullable = false)
    var runCount: Int = 0,
    @Column(name = "last_run_at")
    var lastRunAt: LocalDateTime? = null,
    @Column(name = "is_starred", nullable = false)
    var isStarred: Boolean = false,
) : BaseEntity() {
    /**
     * 워크시트 정보를 업데이트합니다.
     */
    fun update(
        name: String? = null,
        description: String? = null,
        sqlText: String? = null,
        dialect: SqlDialect? = null,
    ) {
        name?.let { this.name = it }
        description?.let { this.description = it }
        sqlText?.let { this.sqlText = it }
        dialect?.let { this.dialect = it }
    }

    /**
     * starred 상태를 토글합니다.
     */
    fun toggleStarred() {
        this.isStarred = !this.isStarred
    }

    /**
     * 실행 횟수를 증가시킵니다.
     */
    fun incrementRunCount() {
        this.runCount++
        this.lastRunAt = LocalDateTime.now()
    }
}
