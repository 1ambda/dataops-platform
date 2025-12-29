package com.github.lambda.dataops

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@Disabled("Temporarily disabled: OAuth2 auto-configuration issue with Spring Boot 4.x - needs proper test configuration")
class DataopsApplicationTests {
    @Test
    fun contextLoads() {
    }
}
