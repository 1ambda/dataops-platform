# Testing Guide for Spring Boot 4.x + Kotlin

> **Purpose:** Comprehensive testing guide for project-basecamp-server with Spring Boot 4.x-specific patterns and lessons learned.

**Last Updated:** 2025-12-30

**See Also:** [PATTERNS.md](./PATTERNS.md) - Quick reference patterns & templates

---

## Table of Contents

1. [Quick Reference](#quick-reference)
2. [Spring Boot 4.x Migration Changes](#spring-boot-4x-migration-changes)
3. [Dependency Versions](#dependency-versions)
4. [Test Patterns by Layer](#test-patterns-by-layer) **(NEW)**
5. [Test Patterns](#test-patterns)
6. [Multi-Module Project Testing](#multi-module-project-testing)
7. [Security Testing](#security-testing)
8. [Troubleshooting](#troubleshooting)
9. [Test Structure](#test-structure)

---

## Quick Reference

### Required Annotations for Controller Tests

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@WithMockUser(username = "testuser", roles = ["USER"])
class MyControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper  // Jackson 3 - NOT ObjectMapper

    @MockkBean(relaxed = true)
    private lateinit var myService: MyService
}
```

### Critical Imports for Spring Boot 4.x

```kotlin
// Jackson 3 (NOT Jackson 2)
import tools.jackson.databind.json.JsonMapper

// Web MVC Test (NEW package)
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest

// MockK with Spring
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import io.mockk.slot

// Parallel execution control
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
```

---

## Spring Boot 4.x Migration Changes

### 1. Jackson 3 Migration

Spring Boot 4 uses **Jackson 3** instead of Jackson 2. This is a breaking change.

| Jackson 2 (Old) | Jackson 3 (New) |
|-----------------|-----------------|
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.json.JsonMapper` |
| `ObjectMapper` class | `JsonMapper` class |
| `@JsonProperty` from `com.fasterxml.jackson` | `@JsonProperty` from `tools.jackson` |

**Example:**

```kotlin
// OLD (Spring Boot 3.x)
import com.fasterxml.jackson.databind.ObjectMapper

@Autowired
private lateinit var objectMapper: ObjectMapper

val json = objectMapper.writeValueAsString(request)

// NEW (Spring Boot 4.x)
import tools.jackson.databind.json.JsonMapper

@Autowired
private lateinit var jsonMapper: JsonMapper

val json = jsonMapper.writeValueAsString(request)
```

### 2. Package Changes

Several Spring Boot test annotations have moved to new packages:

| Old Package | New Package |
|-------------|-------------|
| `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| `org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc` | `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` |
| `org.springframework.boot.autoconfigure.security.oauth2.*` | `org.springframework.boot.security.oauth2.client.autoconfigure.*` |

### 3. springmockk Version Compatibility

**CRITICAL:** Use springmockk **5.0.1+** for Spring Boot 4 compatibility.

```kotlin
// build.gradle.kts
set("springMockkVersion", "5.0.1")

// Usage
testImplementation("com.ninja-squad:springmockk")
```

springmockk 4.x is NOT compatible with Spring Boot 4.

---

## Dependency Versions

Current tested versions in `build.gradle.kts`:

```kotlin
ext {
    set("kotestVersion", "5.9.1")
    set("mockkVersion", "1.13.12")
    set("springMockkVersion", "5.0.1")      // REQUIRED for Spring Boot 4
    set("testcontainersVersion", "1.19.3")
    set("restAssuredVersion", "5.4.0")
    set("springdocVersion", "3.0.0")        // REQUIRED for Spring Boot 4
}
```

### Compatibility Matrix

| Library | Minimum Version | Notes |
|---------|-----------------|-------|
| springmockk | 5.0.1 | Spring Boot 4 support |
| springdoc-openapi | 3.0.0 | Spring Boot 4 + jakarta.* namespace |
| MockK | 1.13.12 | Kotlin 2.x support |
| Kotest | 5.9.1 | Kotlin 2.x support |
| Testcontainers | 1.19.3 | Stable with JDK 24 |

---

## Test Patterns by Layer

> **Quick Reference:** Use this section to determine the correct test type and annotations for each architectural layer.

### Summary Table

| Layer | Test Type | Annotation | Focus Areas |
|-------|-----------|------------|-------------|
| Entity | Unit | None | Domain logic, validation, calculations |
| Service | Unit + Mock | `@Mock` (MockK) | Business logic, orchestration |
| External Client | Unit + Mock | `@Mock` (MockK) | Interface behavior, error handling |
| Controller | Slice | `@WebMvcTest` | HTTP status, validation, security, JSON |
| Controller Integration | Integration | `@SpringBootTest` | E2E scenarios, DB side-effects |
| Repository JPA | Slice | `@DataJpaTest` | CRUD, mappings, auditing |
| Repository DSL | Slice | `@DataJpaTest` + `@Import` | Dynamic queries, projections, joins |

---

### Entity Test (module-core-domain)

**Test Type:** Unit Test only, no Spring context

**What to Test:**
- **Domain Logic:** State change methods (e.g., `activate()`, `cancel()`, `approve()`)
- **Validation Logic:** Constructor/factory method validations
- **Calculation Logic:** Calculation methods (e.g., `calculateTotal()`, `isExpired()`)

**What NOT to Test:**
- Getter/Setter (auto-generated, no logic)
- JPA mappings (verified in Repository tests)

```kotlin
package com.github.lambda.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PipelineEntity Domain Logic Test")
class PipelineEntityTest {

    @Nested
    @DisplayName("activate()")
    inner class Activate {

        @Test
        fun `should change status to ACTIVE when INACTIVE`() {
            // Given
            val pipeline = PipelineEntity(
                name = "test-pipeline",
                status = PipelineStatus.INACTIVE
            )

            // When
            pipeline.activate()

            // Then
            assertThat(pipeline.status).isEqualTo(PipelineStatus.ACTIVE)
        }

        @Test
        fun `should throw exception when already ACTIVE`() {
            // Given
            val pipeline = PipelineEntity(
                name = "test-pipeline",
                status = PipelineStatus.ACTIVE
            )

            // When & Then
            assertThatThrownBy { pipeline.activate() }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("already active")
        }
    }

    @Nested
    @DisplayName("Validation")
    inner class Validation {

        @Test
        fun `should throw exception when name is blank`() {
            assertThatThrownBy {
                PipelineEntity(name = "", status = PipelineStatus.INACTIVE)
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }
}
```

---

### Service Test (module-core-domain)

**Test Type:** Unit Test with Mocks, no Spring context

**Key Patterns:**
- Inject dependencies via `@Mock` (MockK) - **NO Spring context**
- Constructor injection pattern
- Test business logic only

**CRITICAL:** Do NOT use `@MockkBean` for Service tests - it requires Spring context

```kotlin
package com.github.lambda.domain.service

import com.github.lambda.domain.model.PipelineEntity
import com.github.lambda.domain.repository.PipelineRepositoryJpa
import com.github.lambda.domain.repository.PipelineRepositoryDsl
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PipelineService Business Logic Test")
class PipelineServiceTest {

    // Pure mocks - NO Spring context
    private val pipelineRepositoryJpa: PipelineRepositoryJpa = mockk()
    private val pipelineRepositoryDsl: PipelineRepositoryDsl = mockk()

    private lateinit var pipelineService: PipelineService

    @BeforeEach
    fun setUp() {
        // Constructor injection
        pipelineService = PipelineService(
            pipelineRepositoryJpa = pipelineRepositoryJpa,
            pipelineRepositoryDsl = pipelineRepositoryDsl
        )
    }

    @Nested
    @DisplayName("createPipeline")
    inner class CreatePipeline {

        @Test
        fun `should save and return pipeline`() {
            // Given
            val command = CreatePipelineCommand(name = "test-pipeline")
            val savedEntity = PipelineEntity(id = 1L, name = "test-pipeline")
            val saveSlot = slot<PipelineEntity>()

            every { pipelineRepositoryJpa.save(capture(saveSlot)) } returns savedEntity

            // When
            val result = pipelineService.createPipeline(command)

            // Then
            assertThat(result.id).isEqualTo(1L)
            assertThat(result.name).isEqualTo("test-pipeline")
            verify(exactly = 1) { pipelineRepositoryJpa.save(any()) }

            // Verify captured entity
            val capturedEntity = saveSlot.captured
            assertThat(capturedEntity.name).isEqualTo("test-pipeline")
        }
    }
}
```

---

### External Client Test (module-core-infra)

**Test Type:** Unit Test with Mocks, no Spring context

**Pattern:** External dependencies like `AirflowClient` exist as interfaces in `external/`

**CRITICAL:**
- **NO `@MockBean`** - use `@Mock` + constructor injection for pure unit tests
- Real external integration tested separately with WireMock

```kotlin
package com.github.lambda.infra.external

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("AirflowClientImpl Unit Test")
class AirflowClientImplTest {

    // Mock the HTTP client or RestTemplate - NOT the AirflowClient interface
    private val restTemplate: RestTemplate = mockk()

    private lateinit var airflowClient: AirflowClientImpl

    @BeforeEach
    fun setUp() {
        airflowClient = AirflowClientImpl(
            restTemplate = restTemplate,
            baseUrl = "http://localhost:8080"
        )
    }

    @Nested
    @DisplayName("triggerDag")
    inner class TriggerDag {

        @Test
        fun `should return run id when successful`() {
            // Given
            every { restTemplate.postForEntity(any<String>(), any(), any<Class<*>>()) } returns
                ResponseEntity.ok(AirflowDagRunResponse(dagRunId = "run-123"))

            // When
            val result = airflowClient.triggerDag("my-dag", mapOf("date" to "2025-01-01"))

            // Then
            assertThat(result.dagRunId).isEqualTo("run-123")
        }

        @Test
        fun `should throw AirflowException when API fails`() {
            // Given
            every { restTemplate.postForEntity(any<String>(), any(), any<Class<*>>()) } throws
                HttpClientErrorException(HttpStatus.NOT_FOUND)

            // When & Then
            assertThatThrownBy {
                airflowClient.triggerDag("unknown-dag", emptyMap())
            }.isInstanceOf(AirflowException::class.java)
        }
    }
}
```

---

### Controller Test - Slice (module-server-api)

**Test Type:** Slice Test with `@WebMvcTest`

**Class Name Pattern:** `*ControllerTest`

**Focus Areas:**
- **HTTP Status Codes:** success (200), failure (400, 401, 403, 404, 500)
- **Input Validation:** `@Valid` handling, Bad Request responses
- **Security/Auth:** 403 for unauthorized access
- **JSON Serialization:** Response DTO field names match API spec

```kotlin
package com.github.lambda.api.controller

import tools.jackson.databind.json.JsonMapper
import com.github.lambda.domain.service.PipelineService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Controller Slice Test - focuses on HTTP layer only
 *
 * Pattern:
 * - @WebMvcTest: Loads only web layer (controller, filters, converters)
 * - @MockkBean: Mock all service dependencies
 * - Focus: HTTP status, validation, JSON serialization
 */
@WebMvcTest(PipelineController::class)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@WithMockUser(username = "testuser", roles = ["USER"])
class PipelineControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @MockkBean(relaxed = true)
    private lateinit var pipelineService: PipelineService

    @Nested
    @DisplayName("GET /api/v1/pipelines/{id}")
    inner class GetPipeline {

        @Test
        fun `should return 200 with pipeline data`() {
            // Given
            every { pipelineService.getPipeline(any()) } returns testPipelineDto

            // When & Then
            mockMvc.perform(get("/api/v1/pipelines/1"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test-pipeline"))
        }

        @Test
        fun `should return 404 when pipeline not found`() {
            // Given
            every { pipelineService.getPipeline(any()) } returns null

            // When & Then
            mockMvc.perform(get("/api/v1/pipelines/999"))
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/pipelines - Validation")
    inner class CreatePipelineValidation {

        @Test
        fun `should return 400 when name is blank`() {
            // Given - invalid request
            val request = CreatePipelineRequest(name = "")

            // When & Then
            mockMvc.perform(
                post("/api/v1/pipelines")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors").isArray)
        }

        @Test
        @WithMockUser(roles = ["GUEST"])  // Insufficient role
        fun `should return 403 when user lacks permission`() {
            mockMvc.perform(
                post("/api/v1/pipelines")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(validRequest))
            )
                .andExpect(status().isForbidden)
        }
    }
}
```

---

### Controller Integration Test (module-server-api)

**Test Type:** Full Integration Test

**Class Name Pattern:** `*ControllerIntegrationTest`

**Annotations:** `@SpringBootTest`, `@AutoConfigureMockMvc`, TestContainers

**Focus Areas:**
- **Happy Path:** Full user scenarios with real DB
- **Database Side-Effects:** Verify DB state after controller calls

**WARNING:** Expensive to run - minimize count, prefer Unit Tests!

```kotlin
package com.github.lambda.api.controller

import com.github.lambda.config.TestContainersConfig
import tools.jackson.databind.json.JsonMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.transaction.annotation.Transactional

/**
 * Controller Integration Test - Full stack with real database
 *
 * Configuration Pattern:
 * - @SpringBootTest: Full application context
 * - @AutoConfigureMockMvc: HTTP testing without actual server
 * - @Transactional: Test isolation (auto-rollback)
 * - @Import(TestContainersConfig::class): Real database via TestContainers
 *
 * WARNING: Expensive! Use sparingly - prefer slice tests for most cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional  // Test isolation - rolls back after each test
@Execution(ExecutionMode.SAME_THREAD)
@Import(TestContainersConfig::class)
@WithMockUser(username = "testuser", roles = ["USER"])
class PipelineControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @Autowired
    private lateinit var pipelineRepositoryJpaSpringData: PipelineRepositoryJpaSpringData

    @Test
    @DisplayName("POST /api/v1/pipelines - should create and persist pipeline")
    fun `should create pipeline and verify database state`() {
        // Given
        val request = CreatePipelineRequest(name = "integration-test-pipeline")

        // When
        val result = mockMvc.perform(
            post("/api/v1/pipelines")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNumber)
            .andReturn()

        // Then - Verify database side-effect
        val responseId = /* extract id from response */
        val savedEntity = pipelineRepositoryJpaSpringData.findById(responseId)
        assertThat(savedEntity).isPresent
        assertThat(savedEntity.get().name).isEqualTo("integration-test-pipeline")
    }

    @Test
    @DisplayName("Full scenario: Create -> Update -> Get")
    fun `should handle full CRUD lifecycle`() {
        // 1. Create
        // 2. Update
        // 3. Get and verify final state
    }
}
```

---

### Repository Test - JPA (module-core-infra)

**Test Type:** Slice Test with `@DataJpaTest`

**Class Name Pattern:** `*RepositoryJpaImplTest`

**Focus Areas:**
- Mapping correctness
- Basic CRUD operations
- Entity lifecycle
- Auditing (`@CreatedDate`, `@LastModifiedDate`)
- Method naming conventions

```kotlin
package com.github.lambda.infra.repository

import com.github.lambda.domain.model.PipelineEntity
import com.github.lambda.domain.model.PipelineStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles

/**
 * Repository JPA Test - Verifies CRUD operations and JPA mappings
 *
 * Pattern:
 * - @DataJpaTest: Auto-rollback, embedded DB
 * - TestEntityManager: Direct entity manipulation for test setup
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("PipelineRepositoryJpaImpl Test")
class PipelineRepositoryJpaImplTest {

    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    @Autowired
    private lateinit var pipelineRepository: PipelineRepositoryJpaSpringData

    @Nested
    @DisplayName("save()")
    inner class Save {

        @Test
        fun `should persist entity with generated id`() {
            // Given
            val pipeline = PipelineEntity(name = "test-pipeline")

            // When
            val saved = pipelineRepository.save(pipeline)
            testEntityManager.flush()

            // Then
            assertThat(saved.id).isNotNull()
            assertThat(saved.name).isEqualTo("test-pipeline")
        }

        @Test
        fun `should set createdAt on persist`() {
            // Given
            val pipeline = PipelineEntity(name = "audit-test")

            // When
            val saved = pipelineRepository.save(pipeline)
            testEntityManager.flush()

            // Then - Verify auditing
            assertThat(saved.createdAt).isNotNull()
        }
    }

    @Nested
    @DisplayName("findByName()")
    inner class FindByName {

        @Test
        fun `should return entity when name exists`() {
            // Given
            val pipeline = PipelineEntity(name = "unique-pipeline")
            testEntityManager.persistAndFlush(pipeline)

            // When
            val found = pipelineRepository.findByName("unique-pipeline")

            // Then
            assertThat(found).isNotNull
            assertThat(found!!.name).isEqualTo("unique-pipeline")
        }

        @Test
        fun `should return null when name not found`() {
            // When
            val found = pipelineRepository.findByName("nonexistent")

            // Then
            assertThat(found).isNull()
        }
    }
}
```

---

### Repository Test - QueryDSL (module-core-infra)

**Test Type:** Slice Test with `@DataJpaTest` + `@Import`

**Class Name Pattern:** `*RepositoryDslImplTest`

**Focus Areas:**
- Dynamic conditions (BooleanExpression)
- DTO projections
- Joins and aggregations
- Complex operations

```kotlin
package com.github.lambda.infra.repository

import com.github.lambda.config.QueryDslConfig
import com.github.lambda.domain.model.PipelineEntity
import com.github.lambda.domain.model.PipelineStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * Repository QueryDSL Test - Verifies complex queries
 *
 * Pattern:
 * - @DataJpaTest: Lightweight slice test
 * - @Import(QueryDslConfig::class): Required for JPAQueryFactory
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(QueryDslConfig::class)  // REQUIRED: Provides JPAQueryFactory
@DisplayName("PipelineRepositoryDslImpl Test")
class PipelineRepositoryDslImplTest {

    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    @Autowired
    private lateinit var pipelineRepositoryDsl: PipelineRepositoryDslImpl

    @BeforeEach
    fun setUp() {
        // Setup test data
        testEntityManager.persistAndFlush(
            PipelineEntity(name = "pipeline-1", status = PipelineStatus.ACTIVE)
        )
        testEntityManager.persistAndFlush(
            PipelineEntity(name = "pipeline-2", status = PipelineStatus.INACTIVE)
        )
        testEntityManager.persistAndFlush(
            PipelineEntity(name = "other-3", status = PipelineStatus.ACTIVE)
        )
    }

    @Nested
    @DisplayName("searchByConditions()")
    inner class SearchByConditions {

        @Test
        fun `should filter by status`() {
            // When
            val result = pipelineRepositoryDsl.searchByConditions(
                status = PipelineStatus.ACTIVE,
                namePattern = null
            )

            // Then
            assertThat(result).hasSize(2)
            assertThat(result).allMatch { it.status == PipelineStatus.ACTIVE }
        }

        @Test
        fun `should filter by name pattern`() {
            // When
            val result = pipelineRepositoryDsl.searchByConditions(
                status = null,
                namePattern = "pipeline%"
            )

            // Then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder(
                "pipeline-1", "pipeline-2"
            )
        }

        @Test
        fun `should combine multiple conditions`() {
            // When
            val result = pipelineRepositoryDsl.searchByConditions(
                status = PipelineStatus.ACTIVE,
                namePattern = "pipeline%"
            )

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("pipeline-1")
        }
    }

    @Nested
    @DisplayName("findWithRelatedData() - Aggregation Pattern")
    inner class FindWithRelatedData {

        @Test
        fun `should fetch pipeline with tasks in single query`() {
            // When
            val result = pipelineRepositoryDsl.findPipelineWithTasks(1L)

            // Then
            assertThat(result).isNotNull
            assertThat(result!!.tasks).isNotEmpty
            // Verify no N+1 - single query executed
        }
    }
}
```

---

### Test Pattern Decision Tree

```
What are you testing?
│
├─> Domain Entity behavior
│   └─> Entity Test (Unit, no Spring)
│
├─> Service business logic
│   └─> Service Test (Unit + MockK)
│
├─> External API client
│   └─> External Client Test (Unit + MockK)
│       └─> Integration: WireMock (separate test)
│
├─> Controller HTTP behavior
│   ├─> Input validation, status codes, JSON
│   │   └─> Controller Test (@WebMvcTest)
│   └─> Full scenario with DB
│       └─> Controller Integration Test (@SpringBootTest)
│
└─> Repository data access
    ├─> Simple CRUD, mappings
    │   └─> JPA Repository Test (@DataJpaTest)
    └─> Complex queries, projections
        └─> DSL Repository Test (@DataJpaTest + @Import)
```

---

## Test Patterns

### Controller Integration Test (Recommended)

For multi-module projects, use `@SpringBootTest` + `@AutoConfigureMockMvc` instead of `@WebMvcTest`:

```kotlin
package com.github.lambda.controller

import tools.jackson.databind.json.JsonMapper
import com.github.lambda.domain.service.MyService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Controller REST API Test
 *
 * Spring Boot 4.x Pattern:
 * - @SpringBootTest + @AutoConfigureMockMvc: Integration test (multi-module compatible)
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@WithMockUser(username = "testuser", roles = ["USER"])
class MyControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper

    @MockkBean(relaxed = true)
    private lateinit var myService: MyService

    @Nested
    @DisplayName("GET /api/v1/items")
    inner class GetItems {

        @Test
        @DisplayName("should return items list")
        fun `should return items list`() {
            // Given
            every { myService.getItems() } returns listOf(testItem)

            // When & Then
            mockMvc.perform(get("/api/v1/items"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())

            verify { myService.getItems() }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/items")
    inner class CreateItem {

        @Test
        @DisplayName("should create item with valid data")
        fun `should create item with valid data`() {
            // Given
            val request = CreateItemRequest(name = "test")
            every { myService.createItem(any()) } returns testItem

            // When & Then
            mockMvc.perform(
                post("/api/v1/items")
                    .with(csrf())  // Required for POST/PUT/DELETE
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))

            verify { myService.createItem(any()) }
        }
    }
}
```

### Service Unit Test

```kotlin
package com.github.lambda.domain.service

import com.github.lambda.domain.repository.ItemRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ItemService Business Logic Test")
class ItemServiceTest {

    private val itemRepositoryJpa: ItemRepositoryJpa = mockk()
    private lateinit var itemService: ItemService

    @BeforeEach
    fun setUp() {
        itemService = ItemService(itemRepositoryJpa)
    }

    @Nested
    @DisplayName("createItem")
    inner class CreateItem {

        @Test
        @DisplayName("should save and return item")
        fun `should save and return item`() {
            // Given
            val command = CreateItemCommand(name = "test")
            val savedEntity = ItemEntity(id = 1L, name = "test")
            val saveSlot = slot<ItemEntity>()

            every { itemRepositoryJpa.save(capture(saveSlot)) } returns savedEntity

            // When
            val result = itemService.createItem(command)

            // Then
            assertThat(result.id).isEqualTo(1L)
            assertThat(result.name).isEqualTo("test")
            verify(exactly = 1) { itemRepositoryJpa.save(any()) }

            val capturedEntity = saveSlot.captured
            assertThat(capturedEntity.name).isEqualTo("test")
        }

        @Test
        @DisplayName("should throw exception when name is empty")
        fun `should throw exception when name is empty`() {
            // Given
            val command = CreateItemCommand(name = "")

            // When & Then
            assertThatThrownBy { itemService.createItem(command) }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessageContaining("name")
        }
    }
}
```

### Repository Integration Test

```kotlin
package com.github.lambda.infra.repository

import com.github.lambda.domain.model.ItemEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ItemRepository Integration Test")
class ItemRepositoryIntegrationTest {

    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    @Autowired
    private lateinit var itemRepository: ItemRepositoryJpaSpringData

    @Test
    @DisplayName("should find item by name")
    fun `should find item by name`() {
        // Given
        val item = ItemEntity(name = "test-item")
        testEntityManager.persistAndFlush(item)

        // When
        val found = itemRepository.findByName("test-item")

        // Then
        assertThat(found).isNotNull
        assertThat(found!!.name).isEqualTo("test-item")
    }
}
```

---

## Multi-Module Project Testing

### Why @WebMvcTest May Not Work

In multi-module Gradle projects, `@WebMvcTest` may fail to discover controllers because:

1. Controllers are in a different module than the test
2. Spring's component scanning doesn't automatically cross module boundaries
3. The `@SpringBootApplication` class may not be in the test's classpath

### Solution: Use @SpringBootTest + @AutoConfigureMockMvc

```kotlin
// Instead of this (may fail in multi-module):
@WebMvcTest(MyController::class)
class MyControllerTest { ... }

// Use this (works in multi-module):
@SpringBootTest
@AutoConfigureMockMvc
class MyControllerTest { ... }
```

### Ensure Proper Package Scanning

In your `@SpringBootApplication` class:

```kotlin
@SpringBootApplication(
    scanBasePackages = [
        "com.github.lambda.api",
        "com.github.lambda.domain",
        "com.github.lambda.infra",
        "com.github.lambda.common"
    ]
)
class BasecampServerApplication
```

---

## Security Testing

### Using @WithMockUser

For authenticated endpoints, add `@WithMockUser` at class or method level:

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "testuser", roles = ["USER"])  // Class-level
class MyControllerTest {

    @Test
    @WithMockUser(roles = ["ADMIN"])  // Method-level override
    fun `admin should access admin endpoint`() {
        mockMvc.perform(get("/api/admin/settings"))
            .andExpect(status().isOk)
    }
}
```

### CSRF Token for Mutating Requests

POST, PUT, DELETE require CSRF token:

```kotlin
mockMvc.perform(
    post("/api/v1/items")
        .with(csrf())  // Add this!
        .contentType(MediaType.APPLICATION_JSON)
        .content(jsonMapper.writeValueAsString(request))
)
```

### Test Security Configuration

Create a test-specific security config if needed:

```kotlin
package com.github.lambda.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

@TestConfiguration
@EnableWebSecurity
class TestSecurityConfig {

    @Bean
    @Primary
    fun testFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .authorizeHttpRequests { auth ->
                auth.anyRequest().permitAll()
            }
            .build()
}
```

**Note:** Only import `TestSecurityConfig` if the main `SecurityConfig` blocks test access. If your main config already permits all in dev/test profiles, you may not need this.

---

## Troubleshooting

### Error: "Browser not found" or ObjectMapper issues

**Symptom:**
```
No qualifying bean of type 'com.fasterxml.jackson.databind.ObjectMapper'
```

**Solution:**
Use Jackson 3's `JsonMapper` instead:
```kotlin
// Wrong
@Autowired
private lateinit var objectMapper: ObjectMapper

// Correct
@Autowired
private lateinit var jsonMapper: JsonMapper
```

---

### Error: springmockk not compatible

**Symptom:**
```
NoSuchMethodError or ClassNotFoundException related to MockkBean
```

**Solution:**
Upgrade to springmockk 5.0.1+:
```kotlin
// build.gradle.kts
set("springMockkVersion", "5.0.1")
```

---

### Error: Controller not found in @WebMvcTest

**Symptom:**
```
Error creating bean with name 'myController': Unsatisfied dependency
```
or
```
No qualifying bean of type 'com.github.lambda.api.controller.MyController'
```

**Solution:**
Use `@SpringBootTest` + `@AutoConfigureMockMvc` instead:
```kotlin
// Instead of
@WebMvcTest(MyController::class)

// Use
@SpringBootTest
@AutoConfigureMockMvc
```

---

### Error: Parallel test failures

**Symptom:**
Tests pass individually but fail when run together with random errors.

**Solution:**
Add `@Execution(ExecutionMode.SAME_THREAD)` to prevent parallel execution issues with MockK:
```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
class MyControllerTest { ... }
```

---

### Error: 403 Forbidden on POST/PUT/DELETE

**Symptom:**
```
Status expected:<201> but was:<403>
```

**Solution:**
Add CSRF token and authentication:
```kotlin
mockMvc.perform(
    post("/api/v1/items")
        .with(csrf())  // Add CSRF token
        .with(user("testuser").roles("USER"))  // Or use @WithMockUser
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
)
```

---

### Error: Import cannot be resolved for @AutoConfigureMockMvc

**Symptom:**
```
Cannot resolve symbol 'AutoConfigureMockMvc'
```

**Solution:**
Use the new Spring Boot 4.x package:
```kotlin
// Old (Spring Boot 3.x)
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc

// New (Spring Boot 4.x)
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
```

---

### Error: MockkBean not relaxed enough

**Symptom:**
```
io.mockk.MockKException: no answer found for...
```

**Solution:**
Use `relaxed = true` for lenient mocking:
```kotlin
@MockkBean(relaxed = true)
private lateinit var myService: MyService
```

Or provide explicit stubs for all called methods:
```kotlin
every { myService.anyMethod(any()) } returns expectedValue
```

---

## Test Structure

### Directory Structure

```
project-basecamp-server/
├── module-core-common/
│   └── src/test/kotlin/
│       └── com/github/lambda/common/
│           └── util/
├── module-core-domain/
│   └── src/test/kotlin/
│       └── com/github/lambda/domain/
│           ├── model/                    # Entity unit tests
│           └── service/                  # Service unit tests
├── module-core-infra/
│   └── src/test/kotlin/
│       └── com/github/lambda/infra/
│           └── repository/               # Repository integration tests
└── module-server-api/
    └── src/test/kotlin/
        └── com/github/lambda/
            ├── config/
            │   └── TestSecurityConfig.kt  # Test security configuration
            ├── controller/                # Controller tests
            └── mapper/                    # Mapper unit tests
```

### Test Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Unit Test | `{ClassName}Test` | `UserServiceTest` |
| Integration Test | `{ClassName}IntegrationTest` | `UserRepositoryIntegrationTest` |
| E2E Test | `{Feature}E2ETest` | `AuthenticationE2ETest` |

### Test Method Naming

Use backtick notation for readable test names:

```kotlin
@Test
fun `should return user when email exists`() { ... }

@Test
fun `should throw exception when user not found`() { ... }

@Test
@DisplayName("should save user with generated id")
fun saveUserWithGeneratedId() { ... }
```

---

## Test Execution

### Gradle Commands

```bash
# Run all tests
./gradlew test

# Run tests for specific module
./gradlew :module-server-api:test

# Run tests with parallel execution
./gradlew test --parallel

# Run single test class
./gradlew test --tests "com.github.lambda.controller.PipelineControllerTest"

# Run tests matching pattern
./gradlew test --tests "*Controller*"

# Generate test report
./gradlew test jacocoTestReport
```

### Test Reports

After running tests, reports are available at:
- HTML: `build/reports/tests/test/index.html`
- JaCoCo: `build/reports/jacoco/test/html/index.html`

---

## CLI-Server Contract Testing

### Overview

The Basecamp Server API must maintain compatibility with the CLI client (`dli`). Contract testing ensures that server responses match CLI expectations.

### API Contract Source of Truth

| Component | Source | Purpose |
|-----------|--------|---------|
| **API Contracts** | `project-interface-cli/src/dli/core/client.py` | Mock implementations define expected request/response shapes |
| **Response Models** | `BasecampClient.ServerResponse` dataclass | Standard response wrapper |
| **Enums & Constants** | `WorkflowSource`, `RunStatus` enums | Allowed values and state definitions |

### Contract Testing Pattern

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MetricApiContractTest {

    @Autowired
    private lateinit var testRestTemplate: TestRestTemplate

    @Test
    fun `POST metrics should match CLI BasecampClient contract`() {
        // Given: Request matching CLI client.py CreateMetricRequest
        val request = mapOf(
            "name" to "test.example.metric",
            "owner" to "test@example.com",
            "sql" to "SELECT 1",
            "tags" to listOf("test")
        )

        // When: Call API endpoint
        val response = testRestTemplate.postForEntity(
            "/api/v1/metrics",
            request,
            Map::class.java
        )

        // Then: Response matches CLI expectations
        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body).isNotNull

        // Verify response shape matches BasecampClient.ServerResponse
        val body = response.body as Map<*, *>
        assertThat(body).containsKeys("message", "name")
        assertThat(body["name"]).isEqualTo("test.example.metric")
    }

    @Test
    fun `GET metrics name should return CLI-compatible response`() {
        // Setup: Create test metric
        val metricName = "test.metric"
        // ... create metric ...

        // When: Get metric
        val response = testRestTemplate.getForEntity(
            "/api/v1/metrics/$metricName",
            Map::class.java
        )

        // Then: Response matches CLI MetricDto expectations
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        val body = response.body as Map<*, *>

        // Verify required fields from CLI client.py
        assertThat(body).containsKeys("name", "type", "owner", "sql", "tags")
        assertThat(body["type"]).isEqualTo("Metric")
        assertThat(body["tags"]).isInstanceOf(List::class.java)
    }

    @Test
    fun `Error responses should match CLI error format`() {
        // When: Request non-existent metric
        val response = testRestTemplate.getForEntity(
            "/api/v1/metrics/nonexistent",
            Map::class.java
        )

        // Then: Error format matches CLI expectations
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        val body = response.body as Map<*, *>

        // Verify error structure from ERROR_CODES.md
        assertThat(body).containsKey("error")
        val error = body["error"] as Map<*, *>
        assertThat(error).containsKeys("code", "message")
        assertThat(error["code"]).isEqualTo("METRIC_NOT_FOUND")
    }
}
```

### Contract Validation Checklist

When implementing or modifying API endpoints:

- [ ] Response shape matches `BasecampClient` mock implementation
- [ ] Error responses follow standard error format (see [ERROR_HANDLING.md](./ERROR_HANDLING.md))
- [ ] Enum values match CLI client definitions (`WorkflowSource`, `RunStatus`, etc.)
- [ ] Required fields are present in all responses
- [ ] HTTP status codes match CLI expectations
- [ ] Field names use snake_case (CLI) or camelCase (API) consistently

### Cross-Reference

- **Error Codes**: [docs/ERROR_HANDLING.md](../../../docs/ERROR_HANDLING.md#cli-error-code-mapping)
- **CLI Client Mock**: `project-interface-cli/src/dli/core/client.py`
- **API Specifications**: [features/METRIC_FEATURE.md](../features/METRIC_FEATURE.md)

---

## Checklist for New Tests

When writing a new test file:

- [ ] Use correct Spring Boot 4.x imports (`tools.jackson.*`, `org.springframework.boot.webmvc.*`)
- [ ] Use `@SpringBootTest` + `@AutoConfigureMockMvc` for controller tests
- [ ] Add `@Execution(ExecutionMode.SAME_THREAD)` to prevent parallel issues
- [ ] Use `@WithMockUser` for authenticated endpoints
- [ ] Add `.with(csrf())` for POST/PUT/DELETE requests
- [ ] Use `JsonMapper` instead of `ObjectMapper`
- [ ] Use `@MockkBean(relaxed = true)` for lenient mocking
- [ ] Use `@ActiveProfiles("test")` for test-specific configuration

---

## Related Documentation

- [README.md](../README.md) - Project overview and quick start
- [CLAUDE.md](../../CLAUDE.md) - AI assistant instructions
- [Spring Boot 4 Migration Guide](https://docs.spring.io/spring-boot/docs/4.0.x/reference/html/) (external)
