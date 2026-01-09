package com.dataops.basecamp.dto.flag

import com.dataops.basecamp.common.enums.FlagStatus
import com.dataops.basecamp.common.enums.SubjectType
import com.dataops.basecamp.common.enums.TargetingType
import com.dataops.basecamp.domain.entity.flag.FlagEntity
import com.dataops.basecamp.domain.entity.flag.FlagTargetEntity
import com.dataops.basecamp.domain.service.FlagEvaluationResult
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.LocalDateTime

// ============ Response DTOs ============

/**
 * Flag 상세 응답 DTO
 */
data class FlagDto(
    val id: Long,
    val flagKey: String,
    val name: String,
    val description: String?,
    val status: FlagStatus,
    val targetingType: TargetingType,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: LocalDateTime?,
) {
    companion object {
        fun from(entity: FlagEntity) =
            FlagDto(
                id = entity.id!!,
                flagKey = entity.flagKey,
                name = entity.name,
                description = entity.description,
                status = entity.status,
                targetingType = entity.targetingType,
                createdAt = entity.createdAt,
                updatedAt = entity.updatedAt,
            )
    }
}

/**
 * Flag Target 응답 DTO (Override + Permission 통합)
 */
data class FlagTargetDto(
    val id: Long,
    val flagKey: String,
    val subjectType: SubjectType,
    val subjectId: Long,
    val enabled: Boolean,
    val permissions: Map<String, Boolean>?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime?,
) {
    companion object {
        private val objectMapper = ObjectMapper()
        private val mapTypeRef = object : TypeReference<Map<String, Boolean>>() {}

        fun from(
            entity: FlagTargetEntity,
            flagKey: String,
        ): FlagTargetDto {
            val permissions =
                entity.permissions?.let {
                    try {
                        objectMapper.readValue(it, mapTypeRef)
                    } catch (e: Exception) {
                        null
                    }
                }

            return FlagTargetDto(
                id = entity.id!!,
                flagKey = flagKey,
                subjectType = entity.subjectType,
                subjectId = entity.subjectId,
                enabled = entity.enabled,
                permissions = permissions,
                createdAt = entity.createdAt,
            )
        }
    }
}

/**
 * Flag 평가 결과 응답 DTO (전체 Flag 상태)
 */
data class FlagEvaluationDto(
    val flags: Map<String, Boolean>,
    val permissions: Map<String, Map<String, Boolean>>,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val evaluatedAt: LocalDateTime,
) {
    companion object {
        fun from(result: FlagEvaluationResult) =
            FlagEvaluationDto(
                flags = result.flags,
                permissions = result.permissions,
                evaluatedAt = result.evaluatedAt,
            )
    }
}

/**
 * 단일 Flag 평가 결과 응답 DTO
 */
data class FlagSingleEvaluationDto(
    val flagKey: String,
    val enabled: Boolean,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val evaluatedAt: LocalDateTime,
)

// ============ Request/Command DTOs ============

/**
 * Flag 생성 요청 DTO
 */
data class CreateFlagRequest(
    @field:NotBlank(message = "Flag key is required")
    @field:Pattern(
        regexp = "^[a-z0-9_.-]+$",
        message = "Flag key must contain only lowercase alphanumeric characters, hyphens, underscores, and dots",
    )
    val flagKey: String,
    @field:NotBlank(message = "Flag name is required")
    val name: String,
    val description: String? = null,
    val status: FlagStatus = FlagStatus.DISABLED,
    val targetingType: TargetingType = TargetingType.GLOBAL,
)

/**
 * Flag 수정 요청 DTO
 */
data class UpdateFlagRequest(
    val name: String? = null,
    val description: String? = null,
    val status: FlagStatus? = null,
    val targetingType: TargetingType? = null,
)

/**
 * Flag Target 설정 요청 DTO (Override + Permission 통합)
 */
data class SetTargetRequest(
    val subjectType: SubjectType = SubjectType.USER,
    val subjectId: Long,
    val enabled: Boolean,
    val permissions: Map<String, Boolean>? = null,
)

/**
 * Flag Target Permission 수정 요청 DTO
 */
data class UpdateTargetPermissionRequest(
    val subjectType: SubjectType = SubjectType.USER,
    val subjectId: Long,
    @field:NotBlank(message = "Permission key is required")
    val permissionKey: String,
    val granted: Boolean,
)
