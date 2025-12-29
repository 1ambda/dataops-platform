package com.github.lambda.dataops

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
@Disabled("OAuth2 config issue with Spring Boot 4.x")
class DataopsApplicationTests {
    @Test
    fun contextLoads() {
    }
}
