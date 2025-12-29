package com.github.lambda

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Temporarily disabled: OAuth2 auto-configuration issue with Spring Boot 4.x - needs proper test configuration")
class DataOpsBasecampServerApplicationTests {
    @Test
    fun contextLoads() {
        // 스프링 컨텍스트가 정상적으로 로드되는지 테스트
    }
}
