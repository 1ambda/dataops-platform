package com.dataops.basecamp.config

import com.dataops.basecamp.common.constant.CommonConstants
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * 테스트용 보안 설정
 *
 * Authorization 테스트를 위해 실제와 유사한 보안 규칙 적용
 */
@TestConfiguration
@EnableWebSecurity
class TestSecurityConfig {
    @Bean
    @Primary
    fun testSecurityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    // 공개 엔드포인트
                    .requestMatchers(
                        "${CommonConstants.Api.BASE_PATH}${CommonConstants.Api.HEALTH_PATH}",
                        "${CommonConstants.Api.BASE_PATH}/info",
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                    ).permitAll()
                    // OPTIONS 요청 허용 (CORS)
                    .requestMatchers(HttpMethod.OPTIONS)
                    .permitAll()
                    // 관리자 권한 필요 (Airflow sync endpoints)
                    .requestMatchers(
                        "${CommonConstants.Api.BASE_PATH}/airflow/sync/manual/**",
                    ).hasRole("ADMIN")
                    // Query access control
                    .requestMatchers(
                        "${CommonConstants.Api.BASE_PATH}/queries/*/cancel",
                    ).authenticated()
                    // 나머지는 인증만 필요
                    .anyRequest()
                    .permitAll()
            }.build()
}
