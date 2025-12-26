# Testing Guide for DataOps Platform - Basecamp Server

이 문서는 project-basecamp-server의 테스트 구조와 모범 사례를 설명합니다.

## 📁 테스트 구조

```
project-basecamp-server/
├── module-core-common/
│   └── src/test/kotlin/
│       └── com/github/lambda/common/
│           ├── test/BaseTestSupport.kt          # 기본 테스트 지원 클래스
│           └── util/TestUtils.kt               # 공통 테스트 유틸리티
├── module-core-domain/
│   └── src/test/kotlin/
│       └── com/github/lambda/domain/
│           ├── model/                          # 도메인 모델 단위 테스트
│           └── service/                        # 도메인 서비스 테스트
├── module-core-infra/
│   └── src/test/kotlin/
│       └── com/github/lambda/infra/
│           ├── config/TestContainersConfig.kt  # 테스트컨테이너 설정
│           └── repository/                     # 리포지토리 통합 테스트
└── module-server-api/
    └── src/test/kotlin/
        └── com/github/lambda/api/
            ├── config/TestSecurityConfig.kt    # 테스트용 보안 설정
            ├── controller/                     # 컨트롤러 단위 테스트
            └── integration/                    # API 통합 테스트
```

## 🎯 테스트 전략

### 1. 테스트 피라미드

```
                    E2E Tests (적음)
                  /               \
            Integration Tests (중간)
          /                           \
    Unit Tests (많음)                   \
  /                                      \
Component Tests                   Contract Tests
```

### 2. 테스트 분류

| 테스트 유형 | 설명 | 사용 도구 | 위치 |
|-----------|------|-----------|------|
| **단위 테스트** | 개별 클래스/메서드 테스트 | JUnit 5, MockK | 각 모듈 |
| **통합 테스트** | 여러 컴포넌트 연동 테스트 | Spring Test, Testcontainers | 각 모듈 |
| **슬라이스 테스트** | 특정 계층만 로드하는 테스트 | @WebMvcTest, @DataJpaTest | 해당 계층 |
| **E2E 테스트** | 전체 애플리케이션 테스트 | @SpringBootTest | module-server-api |

## 🛠️ 테스트 도구 및 프레임워크

### 핵심 의존성

```kotlin
// JUnit 5 - 테스트 프레임워크
testImplementation("org.junit.jupiter:junit-jupiter")

// MockK - Kotlin 친화적 모킹 프레임워크
testImplementation("io.mockk:mockk:1.13.8")
testImplementation("com.ninja-squad:springmockk:4.0.2")

// AssertJ - 유창한 검증 API
testImplementation("org.assertj:assertj-core")

// Spring Boot Test
testImplementation("org.springframework.boot:spring-boot-starter-test")

// Testcontainers - 컨테이너 기반 통합 테스트
testImplementation("org.testcontainers:junit-jupiter:1.19.3")
testImplementation("org.testcontainers:mysql:1.19.3")
```

## 📚 테스트 유틸리티 가이드

### 1. BaseTestSupport 클래스

모든 테스트의 기본 클래스로 공통 기능을 제공합니다.

```kotlin
class MyTest : BaseTestSupport() {
    
    @Test
    fun `should do something`() {
        // 랜덤 데이터 생성
        val email = randomEmail()
        val name = randomString(10, "user_")
        
        // 실행 시간 측정
        val result = measureExecutionTime("작업 수행") {
            someService.doSomething(email, name)
        }
        
        // Soft Assertions 사용
        softly.assertThat(result).isNotNull
        softly.assertThat(result.email).isEqualTo(email)
    }
}
```

### 2. TestUtils 유틸리티

다양한 테스트 데이터 생성과 헬퍼 메서드를 제공합니다.

```kotlin
// 랜덤 데이터 생성
val email = TestUtils.randomEmail()
val pastDate = TestUtils.randomPastDateTime(30)
val json = TestUtils.toJson(myObject)

// 테스트 데이터 빌더
val user = TestUtils.testData<UserEntity>()
    .with { username = "testuser" }
    .with { enabled = true }
    .build { UserEntity() }
```

### 3. Testcontainers 설정

실제 데이터베이스를 사용하는 통합 테스트를 위한 설정입니다.

```kotlin
@RepositoryIntegrationTest
class MyRepositoryTest {
    // Testcontainers가 MySQL과 Redis를 자동으로 시작
}
```

## 📋 테스트 작성 가이드

### 1. 도메인 모델 테스트

도메인 로직과 비즈니스 규칙을 검증합니다.

```kotlin
@DisplayName("UserEntity 도메인 모델 테스트")
class UserEntityTest {

    @Test
    @DisplayName("이메일로부터 사용자명 동기화가 올바르게 작동해야 한다")
    fun `should sync username from email correctly`() {
        // Given
        val user = UserEntity()
        val email = "john.doe@example.com"

        // When
        user.sync(email)

        // Then
        assertThat(user.username).isEqualTo("john.doe")
    }
}
```

### 2. 서비스 단위 테스트

MockK를 사용하여 의존성을 격리하고 비즈니스 로직을 테스트합니다.

```kotlin
@DisplayName("UserService 비즈니스 로직 테스트")
class UserServiceTest {

    private val userRepository: UserRepository = mockk()
    private val userService = UserService(userRepository)

    @Test
    fun `should return user when finding by email succeeds`() {
        // Given
        val email = "test@example.com"
        val expectedUser = UserEntity(email = email)
        every { userRepository.findByEmail(email) } returns expectedUser

        // When
        val result = userService.findByEmailOrThrow(email)

        // Then
        assertThat(result).isEqualTo(expectedUser)
        verify(exactly = 1) { userRepository.findByEmail(email) }
    }
}
```

### 3. 리포지토리 통합 테스트

JPA 계층과 데이터베이스 연동을 테스트합니다.

```kotlin
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository 통합 테스트")
class UserRepositoryIntegrationTest {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Test
    fun `should find user by email correctly`() {
        // Given
        val user = UserEntity(email = "test@example.com")
        testEntityManager.persistAndFlush(user)

        // When
        val foundUser = userRepository.findByEmail("test@example.com")

        // Then
        assertThat(foundUser).isNotNull
        assertThat(foundUser!!.email).isEqualTo("test@example.com")
    }
}
```

### 4. 웹 계층 테스트

MockMvc를 사용하여 HTTP 요청/응답을 테스트합니다.

```kotlin
@WebMvcTest(HealthController::class)
@DisplayName("HealthController 웹 계층 테스트")
class HealthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should return correct health response`() {
        mockMvc.perform(
            get("/api/health")
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.success").value(true))
        .andExpected(jsonPath("$.data.status").value("UP"))
    }
}
```

### 5. 통합 테스트

전체 애플리케이션 컨텍스트를 로드하여 E2E 테스트를 수행합니다.

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `should perform health check successfully`() {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk)
            .andExpected(jsonPath("$.success").value(true))
    }
}
```

## 🎨 테스트 명명 규칙

### 1. 테스트 클래스 명명

- `{ClassName}Test` - 단위 테스트
- `{ClassName}IntegrationTest` - 통합 테스트
- `{Feature}Test` - 기능 테스트

### 2. 테스트 메서드 명명

```kotlin
// Given-When-Then 패턴으로 명명
fun `should return user when email exists`()
fun `should throw exception when user not found`()
fun `should save user with generated id`()

// 한국어도 지원 (팀 컨벤션에 따라)
fun `이메일이 존재할 때 사용자를 반환해야 한다`()
```

### 3. DisplayName 활용

```kotlin
@DisplayName("사용자 서비스 비즈니스 로직 테스트")
class UserServiceTest {
    
    @Test
    @DisplayName("이메일로 사용자 조회 시 올바른 사용자를 반환해야 한다")
    fun `should return correct user when finding by email`() {
        // 테스트 코드
    }
}
```

## 🔧 테스트 설정

### 1. application-test.yml

테스트 환경 전용 설정 파일입니다.

```yaml
spring:
  profiles:
    active: test
  
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true
```

### 2. 테스트 프로파일 어노테이션

```kotlin
@TestProfile  // 공통 테스트 설정
@FastTest     // 빠른 실행을 위한 설정
@SlowTest     // 느린 통합 테스트 설정
```

## 📊 테스트 실행

### 1. Gradle 명령어

```bash
# 전체 테스트 실행
./gradlew test

# 특정 모듈 테스트
./gradlew :module-core-domain:test

# 테스트 병렬 실행
./gradlew test --parallel

# 테스트 보고서 생성
./gradlew test jacocoTestReport
```

### 2. IDE에서 실행

- IntelliJ IDEA에서 클래스/메서드 우클릭 → Run Test
- 전체 프로젝트 테스트: Run All Tests

## 🎯 테스트 모범 사례

### 1. AAA 패턴

```kotlin
@Test
fun `should calculate total price correctly`() {
    // Arrange (Given)
    val items = listOf(Item("A", 100), Item("B", 200))
    
    // Act (When)
    val total = calculator.calculateTotal(items)
    
    // Assert (Then)
    assertThat(total).isEqualTo(300)
}
```

### 2. 테스트 독립성

- 각 테스트는 독립적으로 실행 가능해야 함
- 테스트 간 데이터 공유 금지
- @Transactional 또는 명시적 cleanup 사용

### 3. 테스트 데이터 관리

```kotlin
@BeforeEach
fun setUp() {
    // 테스트 데이터 준비
}

@AfterEach  
fun tearDown() {
    // 테스트 데이터 정리
}
```

### 4. 의미있는 검증

```kotlin
// 좋지 않은 예
assertThat(result).isNotNull()

// 좋은 예
assertThat(result.email).isEqualTo("test@example.com")
assertThat(result.enabled).isTrue()
assertThat(result.lastActiveAt).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS))
```

## 🚀 성능 테스트

### 1. 응답 시간 검증

```kotlin
@Test
fun `should respond within acceptable time`() {
    assertTimeout(Duration.ofMillis(100)) {
        service.findUser("test@example.com")
    }
}
```

### 2. 동시성 테스트

```kotlin
@Test
fun `should handle concurrent requests`() {
    val futures = (1..10).map { 
        CompletableFuture.supplyAsync {
            service.createUser(randomEmail())
        }
    }
    
    val results = CompletableFuture.allOf(*futures.toTypedArray()).get()
    // 검증 코드
}
```

## 🔍 테스트 커버리지

### 1. Jacoco 설정

```kotlin
// build.gradle.kts
jacoco {
    toolVersion = "0.8.8"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
```

### 2. 커버리지 목표

- **라인 커버리지**: 80% 이상
- **브랜치 커버리지**: 70% 이상  
- **메서드 커버리지**: 90% 이상

## 🐛 테스트 디버깅

### 1. 로그 활용

```kotlin
@Test
fun `debug test with logging`() {
    logger.debug("테스트 데이터: {}", testData)
    
    val result = service.process(testData)
    
    logger.debug("처리 결과: {}", result)
    assertThat(result).isNotNull()
}
```

### 2. 테스트 실패 분석

- MockMvc 결과 출력: `.andDo(MockMvcResultHandlers.print())`
- 데이터베이스 상태 확인: H2 콘솔 사용
- 디버거 활용: 브레이크포인트 설정

## 📈 지속적 개선

1. **정기적인 테스트 리뷰**: 테스트 코드 품질 점검
2. **느린 테스트 최적화**: 실행 시간 모니터링
3. **테스트 커버리지 추적**: 코드 변경 시 커버리지 유지
4. **플레이키 테스트 제거**: 간헐적으로 실패하는 테스트 수정

---

이 가이드를 따라 일관성 있고 신뢰할 수 있는 테스트를 작성하여 코드 품질을 보장하세요.
