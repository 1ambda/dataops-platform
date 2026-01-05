package com.dataops.basecamp.common.test

import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional

/**
 * 통합 테스트 기본 클래스
 *
 * 통합 테스트에서 공통으로 사용할 설정과 기능을 제공합니다.
 * 이 클래스를 상속하여 통합 테스트를 작성할 수 있습니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "logging.level.org.hibernate.SQL=WARN",
        "logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN",
        "spring.cache.type=simple",
        "spring.redis.host=localhost",
        "spring.redis.port=6379",
        "spring.session.store-type=none",
    ],
)
@Transactional
abstract class BaseIntegrationTest {
    @BeforeEach
    fun setUp() {
        // 각 테스트 전에 실행되는 공통 설정
        setupTestData()
    }

    /**
     * 테스트 데이터 설정
     *
     * 하위 클래스에서 오버라이드하여 테스트별 데이터를 설정할 수 있습니다.
     */
    protected open fun setupTestData() {
        // 기본 구현은 비어있음
    }

    /**
     * 테스트용 사용자 생성 헬퍼
     */
    protected fun createTestUser(
        username: String = "testuser",
        email: String = "test@example.com",
        enabled: Boolean = true,
    ) {
        // 구현은 하위 클래스에서 필요에 따라 오버라이드
    }

    /**
     * 데이터베이스 상태 클린업
     */
    protected fun cleanupDatabase() {
        // 테스트 후 데이터베이스 상태를 정리하는 로직
        // @Transactional에 의해 자동으로 롤백되지만,
        // 필요한 경우 명시적으로 클린업 가능
    }
}
