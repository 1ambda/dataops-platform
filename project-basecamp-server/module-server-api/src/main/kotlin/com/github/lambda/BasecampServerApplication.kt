package com.github.lambda

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * DataOps Basecamp Server 메인 애플리케이션 클래스
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.github.lambda.api",
        "com.github.lambda.infra",
        "com.github.lambda.domain",
    ],
)
class BasecampServerApplication

fun main(args: Array<String>) {
    runApplication<BasecampServerApplication>(*args)
}
