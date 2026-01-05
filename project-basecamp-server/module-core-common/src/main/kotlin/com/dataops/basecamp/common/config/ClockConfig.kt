package com.dataops.basecamp.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * Clock Configuration
 *
 * Provides a `java.time.Clock` bean for time-dependent operations.
 * Using Clock injection improves testability by allowing time to be
 * controlled in tests via a fixed Clock.
 *
 * @see java.time.Clock.fixed for creating test clocks
 */
@Configuration
open class ClockConfig {
    /**
     * Production clock using system default time zone.
     *
     * In tests, this bean can be overridden with:
     * ```kotlin
     * Clock.fixed(Instant.parse("2024-01-15T10:30:00Z"), ZoneId.systemDefault())
     * ```
     */
    @Bean
    open fun clock(): Clock = Clock.systemDefaultZone()
}
