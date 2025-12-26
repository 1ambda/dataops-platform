plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
}

description = "Server API Module - REST API endpoints and web layer"

dependencies {
    // 모듈 의존성
    implementation(project(":module-core-common"))
    implementation(project(":module-core-domain"))
    implementation(project(":module-core-infra"))

    // 테스트에서 다른 모듈의 테스트 유틸리티 사용
    testImplementation(project(":module-core-common"))
    testImplementation(project(":module-core-domain"))
    testImplementation(project(":module-core-infra"))

    // Spring Boot Web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")

    // Spring Data (for pagination)
    implementation("org.springframework.data:spring-data-commons")

    // Spring Boot Security
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.springframework.security:spring-security-oauth2-resource-server")
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // Session Management
    implementation("org.springframework.session:spring-session-data-redis")

    // Spring Boot Actuator (헬스체크, 메트릭)
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // API 문서화
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui")

    // 검증
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // 로깅
    implementation("io.github.microutils:kotlin-logging-jvm")

    // 개발 도구
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // 테스트
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("com.h2database:h2")

    // MockK - Kotlin-friendly mocking framework
    testImplementation("io.mockk:mockk")
    testImplementation("com.ninja-squad:springmockk")

    // AssertJ for fluent assertions
    testImplementation("org.assertj:assertj-core")

    // JUnit 5 Extensions
    testImplementation("org.junit.jupiter:junit-jupiter-params")

    // Spring Boot Test Slices
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // JSON Path testing
    testImplementation("com.jayway.jsonpath:json-path")
    testImplementation("com.jayway.jsonpath:json-path-assert")

    // Testcontainers for integration tests
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")

    // WebTestClient for reactive testing (if needed)
    testImplementation("org.springframework:spring-webflux")

    // RestAssured for API testing
    testImplementation("io.rest-assured:rest-assured")
    testImplementation("io.rest-assured:kotlin-extensions")
}

tasks.bootJar {
    enabled = true
    archiveClassifier = ""
}

tasks.jar {
    enabled = false
}
