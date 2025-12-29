package com.github.lambda

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@Disabled("OAuth2 config issue with Spring Boot 4.x")
class DataOpsBasecampServerApplicationTests {
    @Test
    fun contextLoads() {
        // 스프링 컨텍스트가 정상적으로 로드되는지 테스트
    }
}
