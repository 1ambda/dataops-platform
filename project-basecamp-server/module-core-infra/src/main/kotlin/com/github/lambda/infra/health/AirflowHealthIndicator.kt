package com.github.lambda.infra.health

import com.github.lambda.domain.model.health.ComponentHealth
import com.github.lambda.domain.model.health.HealthStatus
import com.github.lambda.domain.port.HealthIndicator
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
