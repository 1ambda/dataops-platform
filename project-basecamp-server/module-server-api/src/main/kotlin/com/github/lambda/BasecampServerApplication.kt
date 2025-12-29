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
        "com.github.lambda.controller",
        "com.github.lambda.mapper",
        "com.github.lambda.dto",
        "com.github.lambda.security",
        "com.github.lambda.config",
        "com.github.lambda.common",
    ],
)
class BasecampServerApplication

fun main(args: Array<String>) {
    runApplication<BasecampServerApplication>(*args)
}
