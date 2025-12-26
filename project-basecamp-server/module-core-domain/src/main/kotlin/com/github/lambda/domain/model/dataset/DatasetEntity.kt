package com.github.lambda.domain.model.dataset

import com.github.lambda.domain.model.BaseEntity
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 데이터셋 엔티티
 */
@Entity
@Table(name = "datasets")
data class Dataset(
    @field:NotBlank(message = "Dataset name is required")
    @field:Size(max = 100, message = "Dataset name must not exceed 100 characters")
    @Column(name = "name", nullable = false, length = 100, unique = true)
    val name: String,
    @field:Size(max = 500, message = "Description must not exceed 500 characters")
    @Column(name = "description", length = 500)
    val description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    val type: DatasetType,
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false)
    val format: DataFormat,
    @field:NotBlank(message = "Location is required")
    @Column(name = "location", nullable = false, length = 500)
    val location: String,
    @Column(name = "schema_definition", columnDefinition = "JSON")
    val schemaDefinition: String? = null,
    @Column(name = "connection_info", columnDefinition = "JSON")
    val connectionInfo: String? = null,
    @Column(name = "tags", columnDefinition = "JSON")
    val tags: String? = null,
    @field:NotBlank(message = "Owner is required")
    @Column(name = "owner", nullable = false, length = 50)
    val owner: String,
    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = true,
) : BaseEntity()

/**
 * 데이터셋 유형
 */
enum class DatasetType {
    SOURCE, // 소스 데이터
    TARGET, // 타겟 데이터
    INTERMEDIATE, // 중간 처리 데이터
    REFERENCE, // 참조 데이터
    ARCHIVE, // 아카이브 데이터
}

/**
 * 데이터 형식
 */
enum class DataFormat {
    // 구조화된 데이터
    CSV,
    JSON,
    XML,
    PARQUET,
    AVRO,
    ORC,

    // 데이터베이스
    MYSQL,
    POSTGRESQL,
    MONGODB,
    REDIS,

    // 파일 시스템
    TEXT,
    BINARY,

    // 스트리밍
    KAFKA,
    KINESIS,

    // API
    REST_API,
    GRAPHQL_API,
}
