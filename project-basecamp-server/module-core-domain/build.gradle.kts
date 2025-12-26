plugins {
    kotlin("plugin.spring")
    kotlin("plugin.allopen")
    kotlin("plugin.jpa") // Entity를 위한 JPA 플러그인
    kotlin("kapt") // Q 클래스 생성을 위한 KAPT만 활성화
}

description = "Core Domain Module - Business domain models and logic (Pure Domain)"

dependencies {
    // 모듈 의존성
    implementation(project(":module-core-common"))

    // 테스트에서 common 모듈의 테스트 유틸리티 사용
    testImplementation(project(":module-core-common"))

    // Spring Boot - 최소한의 의존성만 유지
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework:spring-context")

    // JPA - Entity와 어노테이션을 위한 의존성 (하지만 Repository는 순수함)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // QueryDSL - Q 클래스 생성을 위한 최소 의존성
    compileOnly("com.querydsl:querydsl-core:5.1.0") // Q 클래스 컴파일을 위한 최소 의존성 (런타임 X)
    // implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta") // Infrastructure에서만 사용
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta") // Q 클래스 생성을 위한 annotation processor
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    // Spring Security (UserService에서 사용)
    implementation("org.springframework.boot:spring-boot-starter-security")

    // 검증
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // 테스트 의존성
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
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

    // Spring Test Context
    testImplementation("org.springframework:spring-test")

    // Jackson for JSON processing in tests
    testImplementation("com.fasterxml.jackson.core:jackson-databind")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

// QueryDSL Q 클래스 생성 설정 - 활성화
kotlin.sourceSets.main {
    kotlin.srcDirs("src/main/kotlin", "build/generated/source/kapt/main")
}

// Kapt 설정 - Q 클래스 생성만
kapt {
    keepJavacAnnotationProcessors = true
    includeCompileClasspath = false
    arguments {
        arg("querydsl.entityAccessors", "true")
        arg("querydsl.useFields", "false")
    }
}

// KTLint가 KAPT 후에 실행되도록 태스크 의존성 설정
tasks.named("runKtlintCheckOverMainSourceSet") {
    dependsOn("kaptKotlin")
}
