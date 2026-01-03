plugins {
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
    kotlin("kapt") // QueryDSL 활성화
}

description = "Core Infrastructure Module - Database, external APIs, and infrastructure concerns"

dependencies {
    // 모듈 의존성
    implementation(project(":module-core-common"))
    implementation(project(":module-core-domain"))

    // 테스트에서 common/domain 모듈의 테스트 유틸리티 사용
    testImplementation(project(":module-core-common"))
    testImplementation(project(":module-core-domain"))

    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-tx")

    // 데이터베이스
    runtimeOnly("com.mysql:mysql-connector-j")
    implementation("com.zaxxer:HikariCP")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")

    // QueryDSL - 활성화
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    implementation("com.querydsl:querydsl-core:5.1.0")
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    // Redis (캐시, 세션)
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("io.lettuce:lettuce-core")

    // HTTP 클라이언트 (외부 API 통신)
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("io.netty:netty-resolver-dns-native-macos:4.1.115.Final:osx-aarch_64")

    // 구성 관리
    implementation("org.springframework.boot:spring-boot-configuration-processor")

    // Jackson for JSON serialization (needed by CacheConfiguration)
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // 테스트 의존성
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("com.h2database:h2")

    // Testcontainers for integration tests
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:testcontainers")

    // MockK - Kotlin-friendly mocking framework
    testImplementation("io.mockk:mockk")
    testImplementation("com.ninja-squad:springmockk")

    // AssertJ for fluent assertions
    testImplementation("org.assertj:assertj-core")

    // JUnit 5 Extensions
    testImplementation("org.junit.jupiter:junit-jupiter-params")

    // Spring Boot Test Slices - includes @DataJpaTest, @AutoConfigureTestDatabase, TestEntityManager
    // NOTE: Spring Boot 4 moved @DataJpaTest to spring-boot-data-jpa-test module
    testImplementation("org.springframework.boot:spring-boot-test-autoconfigure")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")

    // Spring Test Context
    testImplementation("org.springframework:spring-test")

    // Jackson for JSON processing in tests
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Redis Testing
    testImplementation("it.ozimov:embedded-redis") {
        exclude(group = "org.slf4j", module = "slf4j-simple")
    }

    // WireMock for HTTP service mocking
    testImplementation("com.github.tomakehurst:wiremock-jre8")
}

// QueryDSL 설정 - 활성화
kotlin.sourceSets.main {
    kotlin.srcDirs("src/main/kotlin", "build/generated/source/kapt/main")
}

// Kapt 설정
kapt {
    keepJavacAnnotationProcessors = true
    includeCompileClasspath = false
    correctErrorTypes = true // Allow KAPT to proceed with error types
    arguments {
        arg("querydsl.entityAccessors", "true")
        arg("querydsl.useFields", "false")
    }
}

// Disable KAPT for test sources - only needed for QueryDSL Q-classes in main sources
tasks.matching { it.name == "kaptTestKotlin" }.configureEach {
    enabled = false
}
tasks.matching { it.name == "kaptGenerateStubsTestKotlin" }.configureEach {
    enabled = false
}

// KTLint가 KAPT 후에 실행되도록 태스크 의존성 설정
tasks.named("runKtlintCheckOverMainSourceSet") {
    dependsOn("kaptKotlin")
}
