package com.dataops.basecamp.infra.health

import com.dataops.basecamp.common.enums.HealthStatus
import com.dataops.basecamp.domain.external.health.HealthIndicator
import com.dataops.basecamp.domain.projection.health.ComponentHealth
import org.springframework.stereotype.Component

/**
 * Airflow 헬스 인디케이터 (MVP Mock 구현)
 *
 * 현재는 Mock 구현으로, 실제 Airflow 연동 시 업데이트 필요합니다.
 */
@Component
class AirflowHealthIndicator : HealthIndicator {
    override fun name(): String = "airflow"

    override fun check(): ComponentHealth =
        ComponentHealth(
            status = HealthStatus.UNKNOWN,
            details =
                mapOf(
                    "note" to "Airflow integration pending",
                    "version" to null,
                    "dagCount" to null,
                ),
        )
}
