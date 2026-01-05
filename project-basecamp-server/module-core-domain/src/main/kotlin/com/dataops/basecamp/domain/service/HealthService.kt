package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.HealthStatus
import com.dataops.basecamp.domain.external.health.HealthIndicator
import com.dataops.basecamp.domain.projection.health.ComponentHealth
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 헬스체크 도메인 서비스
 *
 * 등록된 모든 HealthIndicator를 수집하여 시스템 전체 헬스 상태를 제공합니다.
 */
@Service
@Transactional(readOnly = true)
class HealthService(
    private val healthIndicators: List<HealthIndicator>,
) {
    /**
     * 모든 컴포넌트의 헬스체크를 수행합니다.
     *
     * @return 컴포넌트 이름과 헬스 정보의 맵
     */
    fun checkHealth(): Map<String, ComponentHealth> =
        healthIndicators.associate { indicator ->
            indicator.name() to
                indicator.check()
        }

    /**
     * 컴포넌트들의 상태로부터 전체 상태를 결정합니다.
     *
     * @param components 컴포넌트별 헬스 정보
     * @return 전체 시스템 상태
     */
    fun getOverallStatus(components: Map<String, ComponentHealth>): HealthStatus =
        HealthStatus.fromComponents(components.values.map { it.status })

    /**
     * 특정 컴포넌트의 헬스체크를 수행합니다.
     *
     * @param componentName 컴포넌트 이름
     * @return 컴포넌트 헬스 정보 (없으면 null)
     */
    fun checkComponent(componentName: String): ComponentHealth? =
        healthIndicators.find { it.name() == componentName }?.check()
}
