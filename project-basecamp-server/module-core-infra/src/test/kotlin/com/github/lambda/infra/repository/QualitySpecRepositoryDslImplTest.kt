package com.github.lambda.infra.repository

import com.github.lambda.domain.model.quality.QualitySpecEntity
import com.github.lambda.domain.model.quality.ResourceType
import com.github.lambda.infra.config.JpaTestConfig
import com.github.lambda.infra.config.QueryDslConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles

/**
 * QualitySpecRepositoryDslImpl QueryDSL Tests
 *
 * Tests complex filter queries, tag filtering, resource type filtering,
 * owner and team filtering, pagination with filters, and statistics.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(QueryDslConfig::class, JpaTestConfig::class, QualitySpecRepositoryDslImpl::class)
@DisplayName("QualitySpecRepositoryDslImpl QueryDSL Tests")
@Execution(ExecutionMode.SAME_THREAD)
class QualitySpecRepositoryDslImplTest {
    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    @Autowired
    private lateinit var repository: QualitySpecRepositoryDslImpl

    private lateinit var spec1: QualitySpecEntity
    private lateinit var spec2: QualitySpecEntity
    private lateinit var spec3: QualitySpecEntity
    private lateinit var spec4: QualitySpecEntity

    @BeforeEach
    fun setUp() {
        // Clean up any existing data from parallel test runs
        testEntityManager.entityManager.createNativeQuery("DELETE FROM quality_spec_tags").executeUpdate()
        testEntityManager.entityManager.createNativeQuery("DELETE FROM quality_specs").executeUpdate()
        testEntityManager.flush()
        testEntityManager.clear()

        // Create test entities with various attributes for filter testing
        spec1 =
            QualitySpecEntity(
                name = "quality-spec-sales",
                resourceName = "dataset.sales",
                resourceType = ResourceType.DATASET,
                owner = "owner1@example.com",
                team = "data-team",
                description = "Quality spec for sales dataset",
                tags = mutableSetOf("sales", "critical"),
                scheduleCron = "0 0 * * *",
                enabled = true,
            )

        spec2 =
            QualitySpecEntity(
                name = "quality-spec-orders",
                resourceName = "dataset.orders",
                resourceType = ResourceType.DATASET,
                owner = "owner2@example.com",
                team = "analytics-team",
                description = "Quality spec for orders",
                tags = mutableSetOf("orders", "daily"),
                scheduleCron = null,
                enabled = true,
            )

        spec3 =
            QualitySpecEntity(
                name = "quality-spec-revenue",
                resourceName = "metric.revenue",
                resourceType = ResourceType.METRIC,
                owner = "owner1@example.com",
                team = "data-team",
                description = "Quality spec for revenue metric",
                tags = mutableSetOf("finance", "critical"),
                scheduleCron = "0 0 0 * *",
                enabled = false,
            )

        spec4 =
            QualitySpecEntity(
                name = "quality-spec-users",
                resourceName = "dataset.users",
                resourceType = ResourceType.DATASET,
                owner = "owner3@example.com",
                team = null,
                description = "Quality spec for users dataset",
                tags = mutableSetOf("users"),
                scheduleCron = null,
                enabled = true,
            )

        testEntityManager.persistAndFlush(spec1)
        testEntityManager.persistAndFlush(spec2)
        testEntityManager.persistAndFlush(spec3)
        testEntityManager.persistAndFlush(spec4)
        testEntityManager.clear()
    }

    @Nested
    @DisplayName("findByFilters()")
    inner class FindByFilters {
        @Test
        @DisplayName("should filter by resource type")
        fun `should filter by resource type`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val result =
                repository.findByFilters(
                    resourceType = ResourceType.DATASET,
                    resourceName = null,
                    tag = null,
                    owner = null,
                    team = null,
                    enabled = null,
                    search = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(3)
            assertThat(result.content).allMatch { it.resourceType == ResourceType.DATASET }
        }

        @Test
        @DisplayName("should filter by resource name")
        fun `should filter by resource name`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val result =
                repository.findByFilters(
                    resourceType = null,
                    resourceName = "dataset.sales",
                    tag = null,
                    owner = null,
                    team = null,
                    enabled = null,
                    search = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name).isEqualTo("quality-spec-sales")
        }

        @Test
        @DisplayName("should filter by tag")
        fun `should filter by tag`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val result =
                repository.findByFilters(
                    resourceType = null,
                    resourceName = null,
                    tag = "critical",
                    owner = null,
                    team = null,
                    enabled = null,
                    search = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(2)
            assertThat(result.content.map { it.name }).containsExactlyInAnyOrder(
                "quality-spec-sales",
                "quality-spec-revenue",
            )
        }

        @Test
        @DisplayName("should filter by owner (partial match)")
        fun `should filter by owner partial match`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val result =
                repository.findByFilters(
                    resourceType = null,
                    resourceName = null,
                    tag = null,
                    owner = "owner1",
                    team = null,
                    enabled = null,
                    search = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(2)
            assertThat(result.content.map { it.name }).containsExactlyInAnyOrder(
                "quality-spec-sales",
                "quality-spec-revenue",
            )
        }

        @Test
        @DisplayName("should filter by team")
        fun `should filter by team`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val result =
                repository.findByFilters(
                    resourceType = null,
                    resourceName = null,
                    tag = null,
                    owner = null,
                    team = "data-team",
                    enabled = null,
                    search = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(2)
            assertThat(result.content.map { it.name }).containsExactlyInAnyOrder(
                "quality-spec-sales",
                "quality-spec-revenue",
            )
        }

        @Test
        @DisplayName("should filter by enabled status")
        fun `should filter by enabled status`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val enabledResult =
                repository.findByFilters(
                    resourceType = null,
                    resourceName = null,
                    tag = null,
                    owner = null,
                    team = null,
                    enabled = true,
                    search = null,
                    pageable = pageable,
                )

            val disabledResult =
                repository.findByFilters(
                    resourceType = null,
                    resourceName = null,
                    tag = null,
                    owner = null,
                    team = null,
                    enabled = false,
                    search = null,
                    pageable = pageable,
                )

            // Then
            assertThat(enabledResult.content).hasSize(3)
            assertThat(disabledResult.content).hasSize(1)
            assertThat(disabledResult.content[0].name).isEqualTo("quality-spec-revenue")
        }

        @Test
        @DisplayName("should filter by search text (name or description)")
        fun `should filter by search text`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val result =
                repository.findByFilters(
                    resourceType = null,
                    resourceName = null,
                    tag = null,
                    owner = null,
                    team = null,
                    enabled = null,
                    search = "sales",
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name).isEqualTo("quality-spec-sales")
        }

        @Test
        @DisplayName("should combine multiple filters")
        fun `should combine multiple filters`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When - Filter by resource type AND enabled AND team
            val result =
                repository.findByFilters(
                    resourceType = ResourceType.DATASET,
                    resourceName = null,
                    tag = null,
                    owner = null,
                    team = "data-team",
                    enabled = true,
                    search = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name).isEqualTo("quality-spec-sales")
        }

        @Test
        @DisplayName("should return all when no filters")
        fun `should return all when no filters`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val result =
                repository.findByFilters(
                    resourceType = null,
                    resourceName = null,
                    tag = null,
                    owner = null,
                    team = null,
                    enabled = null,
                    search = null,
                    pageable = pageable,
                )

            // Then
            assertThat(result.content).hasSize(4)
            assertThat(result.totalElements).isEqualTo(4)
        }

        @Test
        @DisplayName("should paginate correctly")
        fun `should paginate correctly`() {
            // Given
            val pageable = PageRequest.of(0, 2)

            // When
            val page =
                repository.findByFilters(
                    resourceType = null,
                    resourceName = null,
                    tag = null,
                    owner = null,
                    team = null,
                    enabled = null,
                    search = null,
                    pageable = pageable,
                )

            // Then
            assertThat(page.content).hasSize(2)
            assertThat(page.totalElements).isEqualTo(4)
            assertThat(page.totalPages).isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("countByFilters()")
    inner class CountByFilters {
        @Test
        @DisplayName("should count filtered results")
        fun `should count filtered results`() {
            // When
            val datasetCount =
                repository.countByFilters(
                    resourceType = ResourceType.DATASET,
                    resourceName = null,
                    tag = null,
                    owner = null,
                    team = null,
                    enabled = null,
                    search = null,
                )

            val enabledCount =
                repository.countByFilters(
                    resourceType = null,
                    resourceName = null,
                    tag = null,
                    owner = null,
                    team = null,
                    enabled = true,
                    search = null,
                )

            // Then
            assertThat(datasetCount).isEqualTo(3)
            assertThat(enabledCount).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("getQualitySpecStatistics()")
    inner class Statistics {
        @Test
        @DisplayName("should return overall statistics")
        fun `should return overall statistics`() {
            // When
            val stats =
                repository.getQualitySpecStatistics(
                    resourceType = null,
                    owner = null,
                )

            // Then
            assertThat(stats["totalCount"]).isEqualTo(4L)
            assertThat(stats["enabledCount"]).isEqualTo(3L)
            assertThat(stats["disabledCount"]).isEqualTo(1L)
        }

        @Test
        @DisplayName("should return statistics filtered by resource type")
        fun `should return statistics filtered by resource type`() {
            // When
            val stats =
                repository.getQualitySpecStatistics(
                    resourceType = ResourceType.DATASET,
                    owner = null,
                )

            // Then
            assertThat(stats["totalCount"]).isEqualTo(3L)
            assertThat(stats["enabledCount"]).isEqualTo(3L)
            assertThat(stats["disabledCount"]).isEqualTo(0L)
            assertThat(stats["resourceType"]).isEqualTo("DATASET")
        }

        @Test
        @DisplayName("should return statistics filtered by owner")
        fun `should return statistics filtered by owner`() {
            // When
            val stats =
                repository.getQualitySpecStatistics(
                    resourceType = null,
                    owner = "owner1",
                )

            // Then
            assertThat(stats["totalCount"]).isEqualTo(2L)
            assertThat(stats["enabledCount"]).isEqualTo(1L)
            assertThat(stats["disabledCount"]).isEqualTo(1L)
        }
    }

    @Nested
    @DisplayName("findScheduledQualitySpecs()")
    inner class ScheduledQueries {
        @Test
        @DisplayName("should find all specs with schedule")
        fun `should find all specs with schedule`() {
            // When
            val result = repository.findScheduledQualitySpecs(null)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder(
                "quality-spec-sales",
                "quality-spec-revenue",
            )
        }

        @Test
        @DisplayName("should find specs matching cron pattern")
        fun `should find specs matching cron pattern`() {
            // When - Find only daily schedule with specific pattern "0 0 * *" (4 asterisks)
            val result = repository.findScheduledQualitySpecs("* * *")

            // Then - Only spec1 has "0 0 * * *" which contains "* * *"
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("quality-spec-sales")
        }
    }

    @Nested
    @DisplayName("findQualitySpecsByResource()")
    inner class ResourceQueries {
        @Test
        @DisplayName("should find specs by resource including disabled")
        fun `should find specs by resource including disabled`() {
            // When
            val result =
                repository.findQualitySpecsByResource(
                    resourceName = "metric.revenue",
                    resourceType = ResourceType.METRIC,
                    includeDisabled = true,
                )

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("quality-spec-revenue")
        }

        @Test
        @DisplayName("should exclude disabled specs when requested")
        fun `should exclude disabled specs when requested`() {
            // When
            val result =
                repository.findQualitySpecsByResource(
                    resourceName = "metric.revenue",
                    resourceType = ResourceType.METRIC,
                    includeDisabled = false,
                )

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("findActiveQualitySpecs()")
    inner class ActiveQueries {
        @Test
        @DisplayName("should find all active specs")
        fun `should find all active specs`() {
            // When
            val result =
                repository.findActiveQualitySpecs(
                    hasSchedule = null,
                    hasTests = null,
                )

            // Then
            assertThat(result).hasSize(3) // Only enabled ones
            assertThat(result).allMatch { it.enabled }
        }

        @Test
        @DisplayName("should find active specs with schedule")
        fun `should find active specs with schedule`() {
            // When
            val result =
                repository.findActiveQualitySpecs(
                    hasSchedule = true,
                    hasTests = null,
                )

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("quality-spec-sales")
        }

        @Test
        @DisplayName("should find active specs without schedule")
        fun `should find active specs without schedule`() {
            // When
            val result =
                repository.findActiveQualitySpecs(
                    hasSchedule = false,
                    hasTests = null,
                )

            // Then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder(
                "quality-spec-orders",
                "quality-spec-users",
            )
        }
    }

    @Nested
    @DisplayName("findRecentlyUpdatedQualitySpecs()")
    inner class RecentlyUpdatedQueries {
        @Test
        @DisplayName("should find recently updated specs")
        fun `should find recently updated specs`() {
            // When
            val result =
                repository.findRecentlyUpdatedQualitySpecs(
                    limit = 2,
                    daysSince = 30,
                )

            // Then
            assertThat(result).hasSizeLessThanOrEqualTo(2)
        }
    }
}
