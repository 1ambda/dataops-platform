import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21" apply false
    id("org.springframework.boot") version "4.0.1" apply false
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.2.21" apply false
    kotlin("plugin.allopen") version "2.2.21" apply false
    kotlin("kapt") version "2.2.21" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

ext {
    set("kotestVersion", "5.9.1")
    set("mockkVersion", "1.13.12")
    set("springMockkVersion", "5.0.1")
    set("testcontainersVersion", "1.19.3")
    set("restAssuredVersion", "5.4.0")
    set("springdocVersion", "3.0.0")
    set("kotlinLoggingVersion", "3.0.5")
    set("commonsCollectionsVersion", "4.4")
    set("commonsLang3Version", "3.15.0")
    set("threeTenExtraVersion", "1.8.0")
    set("embeddedRedisVersion", "0.7.3")
    set("wiremockVersion", "2.35.1")
}

group = "com.github.lambda"
version = "0.0.1-SNAPSHOT"
description = "DataOps Platform - Basecamp Server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    // apply(plugin = "io.gitlab.arturbosch.detekt") // Temporarily disabled due to Kotlin 2.2.21 compatibility issue

    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:4.0.1")
        }
        dependencies {
            dependency("io.kotest:kotest-runner-junit5:${rootProject.extra["kotestVersion"] as String}")
            dependency("io.kotest:kotest-assertions-core:${rootProject.extra["kotestVersion"] as String}")
            dependency("io.kotest:kotest-property:${rootProject.extra["kotestVersion"] as String}")
            dependency("io.mockk:mockk:${rootProject.extra["mockkVersion"] as String}")
            dependency("com.ninja-squad:springmockk:${rootProject.extra["springMockkVersion"] as String}")
            dependency("org.testcontainers:mysql:${rootProject.extra["testcontainersVersion"] as String}")
            dependency("org.testcontainers:junit-jupiter:${rootProject.extra["testcontainersVersion"] as String}")
            dependency("org.testcontainers:testcontainers:${rootProject.extra["testcontainersVersion"] as String}")
            dependency("io.rest-assured:rest-assured:${rootProject.extra["restAssuredVersion"] as String}")
            dependency("io.rest-assured:kotlin-extensions:${rootProject.extra["restAssuredVersion"] as String}")
            dependency(
                "org.springdoc:springdoc-openapi-starter-webmvc-ui:${rootProject.extra["springdocVersion"] as String}",
            )
            dependency("io.github.microutils:kotlin-logging-jvm:${rootProject.extra["kotlinLoggingVersion"] as String}")
            dependency(
                "org.apache.commons:commons-collections4:${rootProject.extra["commonsCollectionsVersion"] as String}",
            )
            dependency("org.apache.commons:commons-lang3:${rootProject.extra["commonsLang3Version"] as String}")
            dependency("org.threeten:threeten-extra:${rootProject.extra["threeTenExtraVersion"] as String}")
            dependency("it.ozimov:embedded-redis:${rootProject.extra["embeddedRedisVersion"] as String}")
            dependency("com.github.tomakehurst:wiremock-jre8:${rootProject.extra["wiremockVersion"] as String}")
        }
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
        implementation("tools.jackson.module:jackson-module-kotlin")

        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

        // Kotest
        testImplementation("io.kotest:kotest-runner-junit5")

        // MockK
        testImplementation("io.mockk:mockk")

        testImplementation("org.junit.jupiter:junit-jupiter")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<KotlinCompile> {
        // replace deprecated kotlinOptions usage with compilerOptions DSL
        compilerOptions {
            freeCompilerArgs.addAll(listOf("-Xjsr305=strict", "-Xannotation-default-target=param-property"))
            // use JvmTarget.fromTarget(...) instead of a plain string
            jvmTarget.set(JvmTarget.fromTarget("24"))
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // Temporarily allow tests with no discovered tests
        systemProperty("junit.jupiter.execution.parallel.enabled", "true")
        systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    // Configure test task to not fail when no tests are discovered
    tasks.named<Test>("test") {
        reports {
            junitXml.required.set(true)
            html.required.set(true)
        }
        // finalizedBy("jacocoTestReport") // Temporarily disabled - jacoco not configured
    }

    // Add a no-op task to satisfy tools/IDEs that look for this legacy task name
    // (prevents: Task 'prepareKotlinBuildScriptModel' not found ...)
    tasks.register("prepareKotlinBuildScriptModel") {
        // no-op
    }
}

// ktlint configuration
ktlint {
    version.set("1.4.0")
    debug.set(false)
    verbose.set(true)
    android.set(false)
    outputToConsole.set(true)
    outputColorName.set("RED")
    ignoreFailures.set(false)
    enableExperimentalRules.set(false)
    additionalEditorconfig.set(
        mapOf(
            "indent_style" to "space",
            "indent_size" to "4",
            "max_line_length" to "120",
        ),
    )
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
        exclude("**/.gradle/**")
    }
}

// detekt configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt/detekt.yml")
    baseline = file("$projectDir/config/detekt/baseline.xml")

    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(true)
    }
}

dependencies {
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.8")
}

// Additional tasks
tasks.register("formatKotlin") {
    group = "formatting"
    description = "Fix Kotlin code style deviations"
    dependsOn("ktlintFormat")
}

tasks.register("checkCodeStyle") {
    group = "verification"
    description = "Check Kotlin code style and quality"
    dependsOn("ktlintCheck") // detekt temporarily disabled due to Kotlin 2.2.21 compatibility issue
}

// Make check task depend on our code style tasks
tasks.named("check") {
    dependsOn("checkCodeStyle")
}
