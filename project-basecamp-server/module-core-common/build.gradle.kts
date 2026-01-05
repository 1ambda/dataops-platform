plugins {
    kotlin("plugin.allopen")
}

description = "Core Common Module - Shared utilities and constants"

dependencies {
    // 공통 유틸리티
    implementation("org.apache.commons:commons-lang3")
    implementation("org.apache.commons:commons-collections4")

    // 로깅
    implementation("io.github.microutils:kotlin-logging-jvm")

    // 시간 처리
    implementation("org.threeten:threeten-extra")

    // JSON 처리 (테스트에서 사용)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Spring 트랜잭션 (테스트에서 사용)
    implementation("org.springframework:spring-tx")

    // Spring Context (ClockConfig 등에서 사용)
    implementation("org.springframework:spring-context")

    // 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
}

allOpen {
    annotation("com.dataops.basecamp.common.annotation.OpenClass")
}

tasks.test {
    failOnNoDiscoveredTests = false
}
