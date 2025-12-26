package com.github.lambda.common.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.test.context.TestPropertySource
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.random.Random

/**
 * 공통 테스트 유틸리티 클래스
 *
 * 모든 테스트 모듈에서 공통으로 사용할 수 있는
 * 유틸리티 메서드와 상수들을 제공합니다.
 */
object TestUtils {
    /**
     * 테스트용 ObjectMapper
     * Jackson 설정이 포함된 ObjectMapper 인스턴스
     */
    val objectMapper: ObjectMapper =
        jacksonObjectMapper().apply {
            registerModule(JavaTimeModule())
        }

    /**
     * 테스트용 랜덤 데이터 생성기
     */
    val random = Random(System.currentTimeMillis())

    /**
     * 랜덤 문자열 생성
     */
    fun randomString(
        length: Int = 10,
        prefix: String = "",
    ): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return prefix +
            (1..length)
                .map { chars[random.nextInt(chars.length)] }
                .joinToString("")
    }

    /**
     * 랜덤 이메일 생성
     */
    fun randomEmail(domain: String = "test.com"): String = "${randomString(8)}@$domain"

    /**
     * 랜덤 숫자 생성
     */
    fun randomInt(
        min: Int = 1,
        max: Int = 1000,
    ): Int = random.nextInt(min, max + 1)

    fun randomLong(
        min: Long = 1L,
        max: Long = 1000L,
    ): Long = random.nextLong(min, max + 1)

    /**
     * 랜덤 Boolean 생성
     */
    fun randomBoolean(): Boolean = random.nextBoolean()

    /**
     * 랜덤 LocalDateTime 생성 (과거)
     */
    fun randomPastDateTime(daysAgo: Long = 30): LocalDateTime {
        val now = LocalDateTime.now()
        val randomDays = random.nextLong(1, daysAgo + 1)
        val randomHours = random.nextLong(0, 24)
        val randomMinutes = random.nextLong(0, 60)

        return now
            .minusDays(randomDays)
            .minusHours(randomHours)
            .minusMinutes(randomMinutes)
    }

    /**
     * 랜덤 LocalDateTime 생성 (미래)
     */
    fun randomFutureDateTime(daysLater: Long = 30): LocalDateTime {
        val now = LocalDateTime.now()
        val randomDays = random.nextLong(1, daysLater + 1)
        val randomHours = random.nextLong(0, 24)
        val randomMinutes = random.nextLong(0, 60)

        return now
            .plusDays(randomDays)
            .plusHours(randomHours)
            .plusMinutes(randomMinutes)
    }

    /**
     * JSON 문자열을 객체로 변환
     */
    inline fun <reified T> fromJson(json: String): T = objectMapper.readValue(json, T::class.java)

    /**
     * 객체를 JSON 문자열로 변환
     */
    fun toJson(obj: Any): String = objectMapper.writeValueAsString(obj)

    /**
     * 객체를 보기 좋은 JSON 문자열로 변환
     */
    fun toPrettyJson(obj: Any): String = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)

    /**
     * 두 LocalDateTime이 거의 같은지 확인 (초 단위 정밀도)
     */
    fun isAlmostEqual(
        time1: LocalDateTime,
        time2: LocalDateTime,
        toleranceSeconds: Long = 5,
    ): Boolean {
        val diff =
            kotlin.math.abs(
                time1.toEpochSecond(java.time.ZoneOffset.UTC) -
                    time2.toEpochSecond(java.time.ZoneOffset.UTC),
            )
        return diff <= toleranceSeconds
    }

    /**
     * 시간 문자열 포맷터
     */
    fun formatDateTime(dateTime: LocalDateTime): String = dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    /**
     * UUID 생성
     */
    fun randomUUID(): String = UUID.randomUUID().toString()

    /**
     * 랜덤 리스트 요소 선택
     */
    fun <T> randomChoice(list: List<T>): T = list[random.nextInt(list.size)]

    /**
     * 랜덤 enum 값 선택
     */
    inline fun <reified T : Enum<T>> randomEnum(): T {
        val values = enumValues<T>()
        return values[random.nextInt(values.size)]
    }

    /**
     * 테스트용 상수
     */
    object Constants {
        const val TEST_EMAIL_DOMAIN = "test.example.com"
        const val TEST_USERNAME_PREFIX = "testuser"
        const val TEST_PASSWORD = "testpassword123"

        // 테스트용 시간 상수 (밀리초)
        const val FAST_TEST_TIMEOUT = 1000L
        const val NORMAL_TEST_TIMEOUT = 5000L
        const val SLOW_TEST_TIMEOUT = 10000L

        // 테스트용 데이터 크기
        const val SMALL_DATASET_SIZE = 10
        const val MEDIUM_DATASET_SIZE = 100
        const val LARGE_DATASET_SIZE = 1000
    }

    /**
     * 테스트 데이터 생성을 위한 빌더 패턴
     */
    class TestDataBuilder<T> {
        private val modifications = mutableListOf<T.() -> Unit>()

        fun with(modification: T.() -> Unit): TestDataBuilder<T> {
            modifications.add(modification)
            return this
        }

        fun build(factory: () -> T): T {
            val instance = factory()
            modifications.forEach { it.invoke(instance) }
            return instance
        }
    }

    /**
     * 테스트 데이터 빌더 팩토리
     */
    fun <T> testData(): TestDataBuilder<T> = TestDataBuilder()

    /**
     * 테스트 실행 시간 측정
     */
    inline fun <T> measureTime(block: () -> T): Pair<T, Long> {
        val start = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - start
        return result to duration
    }

    /**
     * 재시도 로직
     */
    fun <T> retry(
        maxAttempts: Int = 3,
        delayMs: Long = 100,
        block: () -> T,
    ): T {
        repeat(maxAttempts - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                if (attempt < maxAttempts - 1) {
                    Thread.sleep(delayMs)
                } else {
                    throw e
                }
            }
        }
        return block() // 마지막 시도
    }
}

/**
 * 테스트 프로파일 구성을 위한 어노테이션
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@TestPropertySource(
    properties = [
        "spring.test.context.cache.maxSize=1",
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=true",
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.org.hibernate.SQL=DEBUG",
    ],
)
annotation class TestProfile

/**
 * 빠른 테스트를 위한 어노테이션
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@TestPropertySource(
    properties = [
        "spring.test.context.cache.maxSize=0",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.org.springframework=WARN",
        "logging.level.org.hibernate=WARN",
    ],
)
annotation class FastTest

/**
 * 느린 통합 테스트를 위한 어노테이션
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@TestPropertySource(
    properties = [
        "spring.test.context.cache.maxSize=10",
        "spring.jpa.show-sql=false",
        "logging.level.org.springframework=INFO",
    ],
)
annotation class SlowTest
