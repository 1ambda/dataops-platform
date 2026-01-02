package com.github.lambda.domain.model.workflow

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Workflow 스케줄 정보
 */
@Embeddable
class ScheduleInfo(
    @Pattern(
        regexp = "^[0-9\\s\\*\\-\\,\\/LW?#]+$",
        message = "Schedule cron must be a valid cron expression",
    )
    @Size(max = 100, message = "Schedule cron must not exceed 100 characters")
    @Column(name = "schedule_cron", length = 100)
    var cron: String? = null,
    @NotBlank(message = "Timezone is required")
    @Size(max = 50, message = "Timezone must not exceed 50 characters")
    @Column(name = "schedule_timezone", nullable = false, length = 50)
    var timezone: String = "UTC",
) {
    /**
     * 스케줄이 활성화되어 있는지 확인
     */
    fun hasSchedule(): Boolean = !cron.isNullOrBlank()

    /**
     * 스케줄 정보 업데이트
     */
    fun updateSchedule(
        cron: String?,
        timezone: String,
    ) {
        this.cron = cron
        this.timezone = timezone
    }
}
