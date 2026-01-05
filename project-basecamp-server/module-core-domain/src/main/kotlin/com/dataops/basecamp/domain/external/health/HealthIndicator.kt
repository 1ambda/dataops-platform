package com.dataops.basecamp.domain.external.health

import com.dataops.basecamp.domain.projection.health.ComponentHealth

/**
 * 헬스 인디케이터 포트 인터페이스
 *
 * 개별 컴포넌트의 헬스체크를 수행하는 어댑터가 구현해야 합니다.
 */
interface HealthIndicator {
    /**
     * 컴포넌트 이름을 반환합니다.
     */
    fun name(): String

    /**
     * 컴포넌트의 헬스체크를 수행하고 결과를 반환합니다.
     */
    fun check(): ComponentHealth
}
