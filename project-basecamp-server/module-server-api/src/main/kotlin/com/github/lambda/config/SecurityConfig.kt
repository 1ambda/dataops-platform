package com.github.lambda.config

import com.github.lambda.common.constant.CommonConstants
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

/**
 * 보안 설정
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain =
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
                    // 개발 환경에서는 모든 요청 허용 (추후 인증 구현)
                    .anyRequest()
                    .permitAll()
            }.build()
}
