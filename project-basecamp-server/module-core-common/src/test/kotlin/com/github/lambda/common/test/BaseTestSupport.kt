package com.github.lambda.common.test

import com.github.lambda.common.util.TestUtils
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * 모든 테스트의 기본 클래스
 *
 * 공통 테스트 설정과 유틸리티를 제공합니다.
 * 다른 테스트 클래스들이 이 클래스를 상속하여 사용할 수 있습니다.
 */
abstract class BaseTestSupport {
    protected val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * 테스트 시작 시간
     */
    private var testStartTime: LocalDateTime = LocalDateTime.now()

    /**
     * Soft Assertions - 여러 검증을 한번에 수행
     */
    protected lateinit var softly: SoftAssertions

    @BeforeEach
    fun baseSetUp(testInfo: TestInfo) {
        testStartTime = LocalDateTime.now()
        softly = SoftAssertions()

        logger.info("=== 테스트 시작: ${testInfo.displayName} ===")
        logger.debug("테스트 클래스: ${testInfo.testClass.orElse(null)?.simpleName}")
        logger.debug("테스트 메서드: ${testInfo.testMethod.orElse(null)?.name}")

        // 하위 클래스에서 오버라이드할 수 있는 설정 메서드
        customSetUp(testInfo)
    }

    @AfterEach
    fun baseTearDown(testInfo: TestInfo) {
        val testEndTime = LocalDateTime.now()
        val duration = java.time.Duration.between(testStartTime, testEndTime)

        logger.info("=== 테스트 완료: ${testInfo.displayName} (실행시간: ${duration.toMillis()}ms) ===")

        // SoftAssertions 검증
        softly.assertAll()

        // 하위 클래스에서 오버라이드할 수 있는 정리 메서드
        customTearDown(testInfo)
    }

    /**
     * 하위 클래스에서 오버라이드할 수 있는 설정 메서드
     */
    open fun customSetUp(testInfo: TestInfo) {
        // 기본 구현은 빈 메서드
    }

    /**
     * 하위 클래스에서 오버라이드할 수 있는 정리 메서드
     */
    open fun customTearDown(testInfo: TestInfo) {
        // 기본 구현은 빈 메서드
    }

    /**
     * 테스트 데이터 생성 헬퍼
     */
    protected fun <T> createTestData(factory: () -> T): TestUtils.TestDataBuilder<T> = TestUtils.testData()

    /**
     * 랜덤 테스트 데이터 생성을 위한 헬퍼 메서드들
     */
    protected fun randomString(
        length: Int = 10,
        prefix: String = "",
    ) = TestUtils.randomString(length, prefix)

    protected fun randomEmail(domain: String = TestUtils.Constants.TEST_EMAIL_DOMAIN) = TestUtils.randomEmail(domain)

    protected fun randomInt(
        min: Int = 1,
        max: Int = 1000,
    ) = TestUtils.randomInt(min, max)

    protected fun randomLong(
        min: Long = 1L,
        max: Long = 1000L,
    ) = TestUtils.randomLong(min, max)

    protected fun randomBoolean() = TestUtils.randomBoolean()

    protected fun randomPastDateTime(daysAgo: Long = 30) = TestUtils.randomPastDateTime(daysAgo)

    protected fun randomFutureDateTime(daysLater: Long = 30) = TestUtils.randomFutureDateTime(daysLater)

    /**
     * JSON 변환 헬퍼
     */
    protected inline fun <reified T> fromJson(json: String): T = TestUtils.fromJson(json)

    protected fun toJson(obj: Any): String = TestUtils.toJson(obj)

    protected fun toPrettyJson(obj: Any): String = TestUtils.toPrettyJson(obj)

    /**
     * 시간 비교 헬퍼
     */
    protected fun isAlmostEqual(
        time1: LocalDateTime,
        time2: LocalDateTime,
        toleranceSeconds: Long = 5,
    ): Boolean = TestUtils.isAlmostEqual(time1, time2, toleranceSeconds)

    /**
     * 성능 측정 헬퍼
     */
    protected inline fun <T> measureExecutionTime(
        description: String = "",
        block: () -> T,
    ): T {
        val (result, duration) = TestUtils.measureTime(block)
        logger.debug("실행시간 측정${if (description.isNotEmpty()) " - $description" else ""}: ${duration}ms")
        return result
    }

    /**
     * 테스트 조건 검증 헬퍼
     */
    protected fun assumeCondition(
        condition: Boolean,
        message: String = "테스트 조건이 만족되지 않음",
    ) {
        org.junit.jupiter.api.Assumptions
            .assumeTrue(condition, message)
    }

    /**
     * 예외 테스트 헬퍼
     */
    protected inline fun <reified T : Throwable> expectException(
        message: String = "",
        block: () -> Unit,
    ): T =
        org.junit.jupiter.api
            .assertThrows<T>(message, block)

    /**
     * 타임아웃 테스트 헬퍼
     */
    protected fun <T> assertTimeout(
        timeout: java.time.Duration,
        block: () -> T,
    ): T =
        org.junit.jupiter.api
            .assertTimeout(timeout, block)

    /**
     * 컬렉션 검증 헬퍼
     */
    protected fun <T> assertCollectionSize(
        collection: Collection<T>,
        expectedSize: Int,
        description: String = "컬렉션 크기",
    ) {
        softly
            .assertThat(collection)
            .`as`(description)
            .hasSize(expectedSize)
    }

    protected fun <T> assertCollectionContains(
        collection: Collection<T>,
        element: T,
        description: String = "컬렉션 포함",
    ) {
        softly
            .assertThat(collection)
            .`as`(description)
            .contains(element)
    }

    /**
     * Null 검증 헬퍼
     */
    protected fun <T> assertNotNull(
        value: T?,
        description: String = "값이 null이 아님",
    ): T {
        softly
            .assertThat(value)
            .`as`(description)
            .isNotNull()
        return value!!
    }

    /**
     * 문자열 검증 헬퍼
     */
    protected fun assertStringNotEmpty(
        value: String?,
        description: String = "문자열이 비어있지 않음",
    ) {
        softly
            .assertThat(value)
            .`as`(description)
            .isNotEmpty()
    }

    protected fun assertStringContains(
        value: String?,
        substring: String,
        description: String = "문자열 포함",
    ) {
        softly
            .assertThat(value)
            .`as`(description)
            .contains(substring)
    }

    /**
     * 숫자 검증 헬퍼
     */
    protected fun assertPositive(
        value: Number?,
        description: String = "양수",
    ) {
        softly
            .assertThat(value?.toDouble())
            .`as`(description)
            .isPositive()
    }

    /**
     * 범위 검증 헬퍼
     */
    protected fun <T : Comparable<T>> assertInRange(
        value: T,
        min: T,
        max: T,
        description: String = "범위 내 값",
    ) {
        softly
            .assertThat(value)
            .`as`(description)
            .isBetween(min, max)
    }

    /**
     * 테스트 데이터 정리 헬퍼
     */
    protected fun cleanupTestData(
        description: String = "",
        cleanup: () -> Unit,
    ) {
        try {
            cleanup()
            logger.debug("테스트 데이터 정리 완료${if (description.isNotEmpty()) " - $description" else ""}")
        } catch (e: Exception) {
            logger.warn("테스트 데이터 정리 실패${if (description.isNotEmpty()) " - $description" else ""}: ${e.message}")
        }
    }
}

/**
 * 도메인 모델 테스트를 위한 기본 클래스
 */
abstract class BaseDomainTestSupport : BaseTestSupport() {
    override fun customSetUp(testInfo: TestInfo) {
        logger.debug("도메인 모델 테스트 설정 완료")
    }

    /**
     * 도메인 모델 검증 헬퍼
     */
    protected fun <T> assertDomainInvariant(
        model: T,
        description: String = "도메인 불변조건",
        invariant: (T) -> Boolean,
    ) {
        softly
            .assertThat(invariant(model))
            .`as`(description)
            .isTrue()
    }
}

/**
 * 리포지토리 테스트를 위한 기본 클래스
 */
abstract class BaseRepositoryTestSupport : BaseTestSupport() {
    override fun customSetUp(testInfo: TestInfo) {
        logger.debug("리포지토리 테스트 설정 완료")
    }

    /**
     * 엔티티 영속성 검증 헬퍼
     */
    protected fun <T> assertEntityPersisted(
        entity: T,
        idExtractor: (T) -> Any?,
        description: String = "엔티티 영속성",
    ) {
        val id = idExtractor(entity)
        softly
            .assertThat(id)
            .`as`(description)
            .isNotNull()
    }
}

/**
 * 웹 계층 테스트를 위한 기본 클래스
 */
abstract class BaseWebTestSupport : BaseTestSupport() {
    override fun customSetUp(testInfo: TestInfo) {
        logger.debug("웹 계층 테스트 설정 완료")
    }

    /**
     * HTTP 상태 코드 검증 헬퍼
     */
    protected fun assertHttpStatus(
        expectedStatus: Int,
        actualStatus: Int,
        description: String = "HTTP 상태 코드",
    ) {
        softly
            .assertThat(actualStatus)
            .`as`(description)
            .isEqualTo(expectedStatus)
    }
}
