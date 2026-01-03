package com.github.lambda.infra.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Run API Configuration
 *
 * Ad-Hoc SQL 실행 API를 위한 설정을 관리합니다.
 * - RunExecutionProperties: 실행 정책 설정값
 * - @EnableScheduling: 결과 정리 스케줄러 활성화
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(RunExecutionProperties::class)
class RunConfiguration
