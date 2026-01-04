package com.github.lambda.domain.model.workflow

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Workflow Spec - YAML 파일로부터 파싱된 워크플로우 명세
 *
 * YAML 파일 예시:
 * ```yaml
 * name: catalog.schema.dataset_name
 * owner: user@example.com
 * team: data-platform
 * description: Daily user activity aggregation
 * schedule:
 *   cron: "0 0 * * *"
 *   timezone: Asia/Seoul
 * ```
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowSpec(
    /**
     * 데이터셋 이름 (catalog.schema.name 형식)
     */
    @JsonProperty("name")
    val name: String,
    /**
     * 소유자 이메일
     */
    @JsonProperty("owner")
    val owner: String,
    /**
     * 팀 이름 (optional)
     */
    @JsonProperty("team")
    val team: String? = null,
    /**
     * 설명 (optional)
     */
    @JsonProperty("description")
    val description: String? = null,
    /**
     * 스케줄 정보 (optional)
     */
    @JsonProperty("schedule")
    val schedule: WorkflowScheduleSpec? = null,
    /**
     * 추가 메타데이터 (optional)
     */
    @JsonProperty("metadata")
    val metadata: Map<String, Any>? = null,
) {
    /**
     * 필수 필드 검증
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (name.isBlank()) {
            errors.add("name is required")
        } else if (!name.matches(Regex("^[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+$"))) {
            errors.add("name must follow pattern: catalog.schema.name")
        }

        if (owner.isBlank()) {
            errors.add("owner is required")
        } else if (!owner.contains("@")) {
            errors.add("owner must be a valid email")
        }

        schedule?.validate()?.let { errors.addAll(it) }

        return errors
    }

    /**
     * WorkflowEntity로 변환 (s3Path와 airflowDagId는 별도로 설정해야 함)
     */
    fun toEntity(
        sourceType: WorkflowSourceType,
        s3Path: String,
        airflowDagId: String,
    ): WorkflowEntity =
        WorkflowEntity(
            datasetName = name,
            sourceType = sourceType,
            status = WorkflowStatus.ACTIVE,
            owner = owner,
            team = team,
            description = description,
            s3Path = s3Path,
            airflowDagId = airflowDagId,
            schedule =
                ScheduleInfo(
                    cron = schedule?.cron,
                    timezone = schedule?.timezone ?: "UTC",
                ),
        )
}

/**
 * 스케줄 명세
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WorkflowScheduleSpec(
    /**
     * Cron 표현식 (optional)
     */
    @JsonProperty("cron")
    val cron: String? = null,
    /**
     * 타임존 (기본값: UTC)
     */
    @JsonProperty("timezone")
    val timezone: String = "UTC",
) {
    /**
     * 스케줄 검증
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        if (!cron.isNullOrBlank() && !cron.matches(Regex("^[0-9\\s*\\-,/LW?#]+$"))) {
            errors.add("schedule.cron must be a valid cron expression")
        }

        if (timezone.isBlank()) {
            errors.add("schedule.timezone is required when schedule is provided")
        }

        return errors
    }
}

/**
 * YAML 파싱 결과
 */
sealed class WorkflowParseResult {
    /**
     * 파싱 성공
     */
    data class Success(
        val spec: WorkflowSpec,
    ) : WorkflowParseResult()

    /**
     * 파싱 실패
     */
    data class Failure(
        val errors: List<String>,
    ) : WorkflowParseResult()

    /**
     * 성공 여부 확인
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * 실패 여부 확인
     */
    fun isFailure(): Boolean = this is Failure

    /**
     * 성공 시 Spec 반환
     */
    fun getOrNull(): WorkflowSpec? = (this as? Success)?.spec

    /**
     * 실패 시 에러 반환
     */
    fun errorsOrEmpty(): List<String> = (this as? Failure)?.errors ?: emptyList()
}
