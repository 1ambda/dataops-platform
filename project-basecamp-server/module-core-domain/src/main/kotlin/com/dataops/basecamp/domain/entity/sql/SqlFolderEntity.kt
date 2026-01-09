package com.dataops.basecamp.domain.entity.sql

import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * SQL Folder Entity
 *
 * SQL Snippet을 그룹화하는 폴더입니다.
 * - Project에 종속되며, projectId로 FK를 관리합니다.
 * - name은 Project 내에서 unique합니다.
 * - Soft delete를 지원합니다.
 */
@Entity
@Table(
    name = "sql_folder",
    indexes = [
        Index(name = "idx_sql_folder_project_id", columnList = "project_id"),
        Index(name = "idx_sql_folder_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_sql_folder_name_project",
            columnNames = ["name", "project_id"],
        ),
    ],
)
class SqlFolderEntity(
    @Column(name = "project_id", nullable = false)
    val projectId: Long,
    @field:NotBlank(message = "Folder name is required")
    @field:Size(max = 100, message = "Folder name must not exceed 100 characters")
    @Column(name = "name", nullable = false, length = 100)
    var name: String,
    @field:Size(max = 500, message = "Description must not exceed 500 characters")
    @Column(name = "description", length = 500)
    var description: String? = null,
    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
) : BaseEntity() {
    /**
     * 폴더 정보를 업데이트합니다.
     * projectId와 name은 변경할 수 없습니다.
     */
    fun update(
        description: String? = null,
        displayOrder: Int? = null,
    ) {
        description?.let { this.description = it }
        displayOrder?.let { this.displayOrder = it }
    }
}
