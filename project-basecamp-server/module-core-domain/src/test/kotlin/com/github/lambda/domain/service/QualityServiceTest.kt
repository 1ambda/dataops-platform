package com.github.lambda.domain.service

import com.github.lambda.common.exception.QualityRunNotFoundException
import com.github.lambda.common.exception.QualitySpecAlreadyExistsException
import com.github.lambda.common.exception.QualitySpecNotFoundException
import com.github.lambda.domain.model.quality.QualityRunEntity
import com.github.lambda.domain.model.quality.QualitySpecEntity
import com.github.lambda.domain.model.quality.QualityTestEntity
import com.github.lambda.domain.model.quality.ResourceType
import com.github.lambda.domain.model.quality.RunStatus
import com.github.lambda.domain.model.quality.Severity
import com.github.lambda.domain.model.quality.TestType
import com.github.lambda.domain.repository.QualityRunRepositoryJpa
import com.github.lambda.domain.repository.QualitySpecRepositoryDsl
import com.github.lambda.domain.repository.QualitySpecRepositoryJpa
import com.github.lambda.domain.repository.QualityTestRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant
import java.time.LocalDateTime

/**
 * QualityService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("QualityService Unit Tests")
class QualityServiceTest {
    private val qualitySpecRepositoryJpa: QualitySpecRepositoryJpa = mockk()
    private val qualitySpecRepositoryDsl: QualitySpecRepositoryDsl = mockk()
    private val qualityRunRepositoryJpa: QualityRunRepositoryJpa = mockk()
    private val qualityTestRepositoryJpa: QualityTestRepositoryJpa = mockk()
    private val qualityRuleEngineService: QualityRuleEngineService = mockk()

    private val qualityService =
        QualityService(
            qualitySpecRepositoryJpa,
            qualitySpecRepositoryDsl,
            qualityRunRepositoryJpa,
            qualityTestRepositoryJpa,
            qualityRuleEngineService,
        )

    private lateinit var testQualitySpec: QualitySpecEntity
    private lateinit var testQualityTest: QualityTestEntity
    private lateinit var testQualityRun: QualityRunEntity

    @BeforeEach
    fun setUp() {
        testQualitySpec =
            QualitySpecEntity(
                name = "test_dataset_quality_spec",
                resourceName = "iceberg.analytics.users",
                resourceType = ResourceType.DATASET,
                owner = "test@example.com",
                team = "data-team",
                description = "Test quality spec for users dataset",
                tags = mutableSetOf("test", "users"),
                scheduleCron = "0 0 8 * * ?",
                scheduleTimezone = "UTC",
                enabled = true,
            )

        testQualityTest =
            QualityTestEntity(
                name = "user_id_not_null_test",
                testType = TestType.NOT_NULL,
                targetColumns = mutableListOf("user_id"),
                severity = Severity.ERROR,
                enabled = true,
                description = "User ID should not be null",
            )

        testQualityRun =
            QualityRunEntity(
                runId = "run_20250102_120000_001",
                resourceName = "iceberg.analytics.users",
                status = RunStatus.RUNNING,
                startedAt = Instant.now(),
                passedTests = 0,
                failedTests = 0,
                executedBy = "test@example.com",
            )
    }

    @Nested
    @DisplayName("getQualitySpecs")
    inner class GetQualitySpecs {
        @Test
        @DisplayName("should return quality specs without filters")
        fun `should return quality specs without filters`() {
            // Given
            val pageable = PageRequest.of(0, 50)
            val specs = listOf(testQualitySpec)
            val page = PageImpl(specs, pageable, 1)

            every {
                qualitySpecRepositoryDsl.findByFilters(
                    resourceType = null,
                    tag = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = qualityService.getQualitySpecs()

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo(testQualitySpec.name)
            verify(exactly = 1) {
                qualitySpecRepositoryDsl.findByFilters(
                    resourceType = null,
                    tag = null,
                    pageable = pageable,
                )
            }
        }

        @Test
        @DisplayName("should return quality specs with resource type filter")
        fun `should return quality specs with resource type filter`() {
            // Given
            val resourceType = "DATASET"
            val pageable = PageRequest.of(0, 50)
            val specs = listOf(testQualitySpec)
            val page = PageImpl(specs, pageable, 1)

            every {
                qualitySpecRepositoryDsl.findByFilters(
                    resourceType = ResourceType.DATASET,
                    tag = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = qualityService.getQualitySpecs(resourceType = resourceType)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].resourceType).isEqualTo(ResourceType.DATASET)
            verify(exactly = 1) {
                qualitySpecRepositoryDsl.findByFilters(
                    resourceType = ResourceType.DATASET,
                    tag = null,
                    pageable = pageable,
                )
            }
        }

        @Test
        @DisplayName("should return quality specs with tag filter")
        fun `should return quality specs with tag filter`() {
            // Given
            val tag = "test"
            val pageable = PageRequest.of(0, 50)
            val specs = listOf(testQualitySpec)
            val page = PageImpl(specs, pageable, 1)

            every {
                qualitySpecRepositoryDsl.findByFilters(
                    resourceType = null,
                    tag = tag,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = qualityService.getQualitySpecs(tag = tag)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].tags).contains(tag)
        }

        @Test
        @DisplayName("should apply limit and offset correctly")
        fun `should apply limit and offset correctly`() {
            // Given
            val limit = 10
            val offset = 5
            val pageable = PageRequest.of(0, 10) // offset/limit = 5/10 = 0
            val specs = listOf(testQualitySpec)
            val page = PageImpl(specs, pageable, 1)

            every {
                qualitySpecRepositoryDsl.findByFilters(
                    resourceType = null,
                    tag = null,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = qualityService.getQualitySpecs(limit = limit, offset = offset)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) {
                qualitySpecRepositoryDsl.findByFilters(
                    resourceType = null,
                    tag = null,
                    pageable = pageable,
                )
            }
        }

        @Test
        @DisplayName("should return empty list when no specs match filters")
        fun `should return empty list when no specs match filters`() {
            // Given
            val tag = "nonexistent"
            val pageable = PageRequest.of(0, 50)
            val page = PageImpl<QualitySpecEntity>(emptyList(), pageable, 0)

            every {
                qualitySpecRepositoryDsl.findByFilters(
                    resourceType = null,
                    tag = tag,
                    pageable = pageable,
                )
            } returns page

            // When
            val result = qualityService.getQualitySpecs(tag = tag)

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("getQualitySpec")
    inner class GetQualitySpec {
        @Test
        @DisplayName("should return quality spec when found by name")
        fun `should return quality spec when found by name`() {
            // Given
            val name = "test_dataset_quality_spec"
            every { qualitySpecRepositoryJpa.findByName(name) } returns testQualitySpec

            // When
            val result = qualityService.getQualitySpec(name)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.name).isEqualTo(name)
            assertThat(result?.owner).isEqualTo("test@example.com")
            verify(exactly = 1) { qualitySpecRepositoryJpa.findByName(name) }
        }

        @Test
        @DisplayName("should return null when quality spec not found")
        fun `should return null when quality spec not found`() {
            // Given
            val name = "nonexistent_quality_spec"
            every { qualitySpecRepositoryJpa.findByName(name) } returns null

            // When
            val result = qualityService.getQualitySpec(name)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { qualitySpecRepositoryJpa.findByName(name) }
        }

        @Test
        @DisplayName("should return null for soft-deleted quality spec")
        fun `should return null for soft-deleted quality spec`() {
            // Given
            val name = "deleted_quality_spec"
            val deletedSpec =
                testQualitySpec.apply {
                    deletedAt = LocalDateTime.now()
                }
            every { qualitySpecRepositoryJpa.findByName(name) } returns deletedSpec

            // When
            val result = qualityService.getQualitySpec(name)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getQualitySpecOrThrow")
    inner class GetQualitySpecOrThrow {
        @Test
        @DisplayName("should return quality spec when found")
        fun `should return quality spec when found`() {
            // Given
            val name = "test_dataset_quality_spec"
            every { qualitySpecRepositoryJpa.findByName(name) } returns testQualitySpec

            // When
            val result = qualityService.getQualitySpecOrThrow(name)

            // Then
            assertThat(result.name).isEqualTo(name)
        }

        @Test
        @DisplayName("should throw QualitySpecNotFoundException when not found")
        fun `should throw QualitySpecNotFoundException when not found`() {
            // Given
            val name = "nonexistent_quality_spec"
            every { qualitySpecRepositoryJpa.findByName(name) } returns null

            // When & Then
            val exception =
                assertThrows<QualitySpecNotFoundException> {
                    qualityService.getQualitySpecOrThrow(name)
                }

            assertThat(exception.message).contains(name)
        }
    }

    @Nested
    @DisplayName("createQualitySpec")
    inner class CreateQualitySpec {
        @Test
        @DisplayName("should create quality spec successfully when name does not exist")
        fun `should create quality spec successfully when name does not exist`() {
            // Given
            val newSpec =
                QualitySpecEntity(
                    name = "new_quality_spec",
                    resourceName = "iceberg.analytics.orders",
                    resourceType = ResourceType.DATASET,
                    owner = "new@example.com",
                    description = "New quality spec",
                    tags = mutableSetOf("new"),
                    enabled = true,
                )
            newSpec.addTest(testQualityTest)

            every { qualitySpecRepositoryJpa.existsByName(newSpec.name) } returns false

            val savedSpecSlot = slot<QualitySpecEntity>()
            every { qualitySpecRepositoryJpa.save(capture(savedSpecSlot)) } answers { savedSpecSlot.captured }

            val savedTestSlot = slot<QualityTestEntity>()
            every { qualityTestRepositoryJpa.save(capture(savedTestSlot)) } answers { savedTestSlot.captured }

            // When
            val result = qualityService.createQualitySpec(newSpec)

            // Then
            assertThat(result.name).isEqualTo(newSpec.name)
            assertThat(result.owner).isEqualTo(newSpec.owner)
            assertThat(result.tags).contains("new")
            assertThat(result.tests).hasSize(1)

            verify(exactly = 1) { qualitySpecRepositoryJpa.existsByName(newSpec.name) }
            verify(exactly = 1) { qualitySpecRepositoryJpa.save(any()) }
            verify(exactly = 1) { qualityTestRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw QualitySpecAlreadyExistsException when spec already exists")
        fun `should throw QualitySpecAlreadyExistsException when spec already exists`() {
            // Given
            val existingName = "existing_quality_spec"
            val newSpec =
                QualitySpecEntity(
                    name = existingName,
                    resourceName = "iceberg.analytics.orders",
                    resourceType = ResourceType.DATASET,
                    owner = "test@example.com",
                    enabled = true,
                )
            every { qualitySpecRepositoryJpa.existsByName(existingName) } returns true

            // When & Then
            val exception =
                assertThrows<QualitySpecAlreadyExistsException> {
                    qualityService.createQualitySpec(newSpec)
                }

            assertThat(exception.message).contains(existingName)
            verify(exactly = 1) { qualitySpecRepositoryJpa.existsByName(existingName) }
            verify(exactly = 0) { qualitySpecRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("updateQualitySpec")
    inner class UpdateQualitySpec {
        @Test
        @DisplayName("should update quality spec description successfully")
        fun `should update quality spec description successfully`() {
            // Given
            val name = "test_dataset_quality_spec"
            val newDescription = "Updated description"

            every { qualitySpecRepositoryJpa.findByName(name) } returns testQualitySpec
            every { qualitySpecRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                qualityService.updateQualitySpec(
                    name = name,
                    description = newDescription,
                )

            // Then
            assertThat(result.description).isEqualTo(newDescription)
            verify(exactly = 1) { qualitySpecRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should update enabled status")
        fun `should update enabled status`() {
            // Given
            val name = "test_dataset_quality_spec"
            val enabled = false

            every { qualitySpecRepositoryJpa.findByName(name) } returns testQualitySpec
            every { qualitySpecRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                qualityService.updateQualitySpec(
                    name = name,
                    enabled = enabled,
                )

            // Then
            assertThat(result.enabled).isEqualTo(enabled)
        }

        @Test
        @DisplayName("should update tests")
        fun `should update tests`() {
            // Given
            val name = "test_dataset_quality_spec"
            val newTests =
                listOf(
                    QualityTestEntity(
                        name = "new_test",
                        testType = TestType.UNIQUE,
                        targetColumns = mutableListOf("email"),
                        severity = Severity.ERROR,
                        enabled = true,
                    ),
                )

            every { qualitySpecRepositoryJpa.findByName(name) } returns testQualitySpec
            every { qualitySpecRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                qualityService.updateQualitySpec(
                    name = name,
                    tests = newTests,
                )

            // Then
            assertThat(result.tests).hasSize(1)
            assertThat(result.tests[0].name).isEqualTo("new_test")
        }

        @Test
        @DisplayName("should throw QualitySpecNotFoundException when updating non-existent spec")
        fun `should throw QualitySpecNotFoundException when updating non-existent spec`() {
            // Given
            val name = "nonexistent_quality_spec"
            every { qualitySpecRepositoryJpa.findByName(name) } returns null

            // When & Then
            assertThrows<QualitySpecNotFoundException> {
                qualityService.updateQualitySpec(name = name, description = "New description")
            }
        }
    }

    @Nested
    @DisplayName("deleteQualitySpec")
    inner class DeleteQualitySpec {
        @Test
        @DisplayName("should soft delete quality spec successfully")
        fun `should soft delete quality spec successfully`() {
            // Given
            val name = "test_dataset_quality_spec"
            every { qualitySpecRepositoryJpa.findByName(name) } returns testQualitySpec
            every { qualitySpecRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = qualityService.deleteQualitySpec(name)

            // Then
            assertThat(result).isTrue()
            assertThat(testQualitySpec.deletedAt).isNotNull()
            verify(exactly = 1) { qualitySpecRepositoryJpa.save(testQualitySpec) }
        }

        @Test
        @DisplayName("should throw QualitySpecNotFoundException when deleting non-existent spec")
        fun `should throw QualitySpecNotFoundException when deleting non-existent spec`() {
            // Given
            val name = "nonexistent_quality_spec"
            every { qualitySpecRepositoryJpa.findByName(name) } returns null

            // When & Then
            assertThrows<QualitySpecNotFoundException> {
                qualityService.deleteQualitySpec(name)
            }
        }
    }

    @Nested
    @DisplayName("executeQualityTests")
    inner class ExecuteQualityTests {
        @Test
        @DisplayName("should execute quality tests successfully")
        fun `should execute quality tests successfully`() {
            // Given
            val resourceName = "iceberg.analytics.users"
            val qualitySpecName = "test_dataset_quality_spec"
            val executedBy = "test@example.com"

            // Mock dependencies
            every { qualitySpecRepositoryJpa.findByName(qualitySpecName) } returns testQualitySpec

            val savedRunSlot = slot<QualityRunEntity>()
            every { qualityRunRepositoryJpa.save(capture(savedRunSlot)) } answers {
                savedRunSlot.captured
            }

            val enabledTests = listOf(testQualityTest)
            every { qualityTestRepositoryJpa.findBySpecNameAndEnabled(qualitySpecName, true) } returns enabledTests

            // Mock rule engine service
            every {
                qualityRuleEngineService.generateNotNullTestSql(resourceName, "user_id")
            } returns
                mockk {
                    every { sql } returns "SELECT COUNT(*) FILTER (WHERE user_id IS NULL) as failed_rows"
                }

            // When
            val result =
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    qualitySpecName = qualitySpecName,
                    executedBy = executedBy,
                )

            // Then
            assertThat(result.resourceName).isEqualTo(resourceName)
            assertThat(result.executedBy).isEqualTo(executedBy)
            assertThat(result.status).isEqualTo(RunStatus.COMPLETED)

            verify(exactly = 1) { qualitySpecRepositoryJpa.findByName(qualitySpecName) }
            verify(exactly = 2) { qualityRunRepositoryJpa.save(any()) } // Initial save + final save
        }

        @Test
        @DisplayName("should execute tests without specifying quality spec name")
        fun `should execute tests without specifying quality spec name`() {
            // Given
            val resourceName = "iceberg.analytics.users"
            val executedBy = "test@example.com"

            every {
                qualitySpecRepositoryDsl.findQualitySpecsByResource(resourceName, ResourceType.DATASET)
            } returns listOf(testQualitySpec)

            val savedRunSlot = slot<QualityRunEntity>()
            every { qualityRunRepositoryJpa.save(capture(savedRunSlot)) } answers {
                savedRunSlot.captured
            }

            val enabledTests = listOf(testQualityTest)
            every {
                qualityTestRepositoryJpa.findBySpecNameAndEnabled(testQualitySpec.name, true)
            } returns enabledTests

            every {
                qualityRuleEngineService.generateNotNullTestSql(resourceName, "user_id")
            } returns
                mockk {
                    every { sql } returns "SELECT COUNT(*) FILTER (WHERE user_id IS NULL) as failed_rows"
                }

            // When
            val result =
                qualityService.executeQualityTests(
                    resourceName = resourceName,
                    executedBy = executedBy,
                )

            // Then
            assertThat(result.resourceName).isEqualTo(resourceName)
            assertThat(result.executedBy).isEqualTo(executedBy)
        }

        @Test
        @DisplayName("should throw exception when no quality specs found for resource")
        fun `should throw exception when no quality specs found for resource`() {
            // Given
            val resourceName = "iceberg.analytics.nonexistent"

            every {
                qualitySpecRepositoryDsl.findQualitySpecsByResource(resourceName, ResourceType.DATASET)
            } returns emptyList()

            // When & Then
            assertThrows<QualitySpecNotFoundException> {
                qualityService.executeQualityTests(resourceName = resourceName)
            }
        }
    }

    @Nested
    @DisplayName("getQualityRuns")
    inner class GetQualityRuns {
        @Test
        @DisplayName("should return quality runs for a spec")
        fun `should return quality runs for a spec`() {
            // Given
            val specName = "test_dataset_quality_spec"
            val pageable = PageRequest.of(0, 50)
            val runs = listOf(testQualityRun)
            val page = PageImpl(runs, pageable, 1)

            every {
                qualityRunRepositoryJpa.findBySpecNameOrderByStartedAtDesc(specName, pageable)
            } returns page

            // When
            val result = qualityService.getQualityRuns(specName)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].runId).isEqualTo(testQualityRun.runId)
        }

        @Test
        @DisplayName("should apply limit and offset correctly")
        fun `should apply limit and offset correctly`() {
            // Given
            val specName = "test_dataset_quality_spec"
            val limit = 10
            val offset = 5
            val pageable = PageRequest.of(0, 10)
            val runs = listOf(testQualityRun)
            val page = PageImpl(runs, pageable, 1)

            every {
                qualityRunRepositoryJpa.findBySpecNameOrderByStartedAtDesc(specName, pageable)
            } returns page

            // When
            val result = qualityService.getQualityRuns(specName, limit = limit, offset = offset)

            // Then
            assertThat(result).hasSize(1)
        }
    }

    @Nested
    @DisplayName("getQualityRun")
    inner class GetQualityRun {
        @Test
        @DisplayName("should return quality run when found by run ID")
        fun `should return quality run when found by run ID`() {
            // Given
            val runId = "run_20250102_120000_001"
            every { qualityRunRepositoryJpa.findByRunId(runId) } returns testQualityRun

            // When
            val result = qualityService.getQualityRun(runId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.runId).isEqualTo(runId)
            verify(exactly = 1) { qualityRunRepositoryJpa.findByRunId(runId) }
        }

        @Test
        @DisplayName("should return null when quality run not found")
        fun `should return null when quality run not found`() {
            // Given
            val runId = "nonexistent_run"
            every { qualityRunRepositoryJpa.findByRunId(runId) } returns null

            // When
            val result = qualityService.getQualityRun(runId)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { qualityRunRepositoryJpa.findByRunId(runId) }
        }
    }

    @Nested
    @DisplayName("getQualityRunOrThrow")
    inner class GetQualityRunOrThrow {
        @Test
        @DisplayName("should return quality run when found")
        fun `should return quality run when found`() {
            // Given
            val runId = "run_20250102_120000_001"
            every { qualityRunRepositoryJpa.findByRunId(runId) } returns testQualityRun

            // When
            val result = qualityService.getQualityRunOrThrow(runId)

            // Then
            assertThat(result.runId).isEqualTo(runId)
        }

        @Test
        @DisplayName("should throw QualityRunNotFoundException when not found")
        fun `should throw QualityRunNotFoundException when not found`() {
            // Given
            val runId = "nonexistent_run"
            every { qualityRunRepositoryJpa.findByRunId(runId) } returns null

            // When & Then
            val exception =
                assertThrows<QualityRunNotFoundException> {
                    qualityService.getQualityRunOrThrow(runId)
                }

            assertThat(exception.message).contains(runId)
        }
    }
}
