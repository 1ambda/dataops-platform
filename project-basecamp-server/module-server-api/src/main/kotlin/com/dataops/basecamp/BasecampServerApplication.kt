package com.dataops.basecamp

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * DataOps Basecamp Server 메인 애플리케이션 클래스
 */
@SpringBootApplication(
    scanBasePackages = [
        "com.dataops.basecamp.api",
        "com.dataops.basecamp.infra",
        "com.dataops.basecamp.domain",
        "com.dataops.basecamp.controller",
        "com.dataops.basecamp.mapper",
        "com.dataops.basecamp.dto",
        "com.dataops.basecamp.security",
        "com.dataops.basecamp.config",
        "com.dataops.basecamp.common",
        "com.dataops.basecamp.exception",
    ],
)
class BasecampServerApplication

fun main(args: Array<String>) {
    runApplication<BasecampServerApplication>(*args)
}
