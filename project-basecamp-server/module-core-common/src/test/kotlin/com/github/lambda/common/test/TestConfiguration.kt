package com.github.lambda.common.test

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * 테스트용 공통 설정
 *
 * 모든 테스트에서 사용할 수 있는 공통 설정과 빈을 제공합니다.
 */
@TestConfiguration
@Profile("test")
class TestConfiguration {
    /**
     * 테스트용 고정 시계
     *
     * 시간에 의존적인 테스트의 일관성을 보장하기 위해 고정된 시점의 Clock을 제공합니다.
     */
    @Bean
    @Primary
    fun testClock(): Clock =
        Clock.fixed(
            Instant.parse("2024-01-15T10:30:00Z"),
            ZoneId.systemDefault(),
        )
}
