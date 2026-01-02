package com.github.lambda.infra.repository

import com.github.lambda.domain.model.quality.ResourceType
import com.github.lambda.domain.model.quality.QualitySpecEntity
import com.github.lambda.domain.repository.QualitySpecRepositoryDsl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

/**
 * QualitySpecRepositoryDslImpl Integration Tests
 *
 * Uses @DataJpaTest for database layer testing with H2 in-memory database.
 * Tests complex QueryDSL-based queries.
 */
@DataJpaTest
@Import(QualitySpecRepositoryDslImpl::class)
@ComponentScan(basePackages = ["com.github.lambda.infra.repository"])
@ActiveProfiles("test")
@DisplayName("QualitySpecRepositoryDslImpl Integration Tests")
class QualitySpecRepositoryDslImplTest {

    @Autowired
    private lateinit var qualitySpecRepositoryDsl: QualitySpecRepositoryDsl

    @Autowired
    private lateinit var entityManager: TestEntityManager

    private lateinit var testQualitySpec1: QualitySpecEntity
    private lateinit var testQualitySpec2: QualitySpecEntity
    private lateinit var testQualitySpec3: QualitySpecEntity
    private lateinit var testQualitySpec4: QualitySpecEntity
    private lateinit var testQualitySpec5: QualitySpecEntity

    @BeforeEach
    fun setUp() {
        testQualitySpec1 = QualitySpecEntity(
            name = "users_quality_spec",
            resourceName = "iceberg.analytics.users",
            resourceType = ResourceType.DATASET,
            owner = "alice@example.com",
            team = "data-team",
            description = "Quality spec for users dataset with critical checks",
            tags = mutableSetOf("users", "critical"),
            scheduleCron = "0 0 8 * * ?",
            scheduleTimezone = "UTC",
            enabled = true
        )

        testQualitySpec2 = QualitySpecEntity(
            name = "orders_quality_spec",
            resourceName = "iceberg.analytics.orders",
            resourceType = ResourceType.DATASET,
            owner = "bob@example.com",
            team = "analytics-team",
            description = "Quality spec for orders dataset for business metrics",
            tags = mutableSetOf("orders", "business", "critical"),
            scheduleCron = null,
            scheduleTimezone = "UTC",
            enabled = true
        )

        testQualitySpec3 = QualitySpecEntity(
            name = "products_quality_spec",
            resourceName = "iceberg.analytics.products",
            resourceType = ResourceType.DATASET,
            owner = "alice@example.com",
            team = null,
            description = "Quality spec for products dataset",
            tags = mutableSetOf("products"),
            scheduleCron = "0 0 9 * * ?",
            scheduleTimezone = "UTC",
            enabled = false
        )

        testQualitySpec4 = QualitySpecEntity(
            name = "metrics_quality_spec",
            resourceName = "analytics.revenue.monthly_revenue",
            resourceType = ResourceType.METRIC,
            owner = "charlie@example.com",
            team = "data-team",
            description = "Quality spec for revenue metrics with monitoring",
            tags = mutableSetOf("revenue", "metrics", "monitoring"),
            scheduleCron = "0 */6 * * *",
            scheduleTimezone = "UTC",
            enabled = true
        )

        testQualitySpec5 = QualitySpecEntity(
            name = "inactive_spec",
            resourceName = "iceberg.analytics.inactive_table",
            resourceType = ResourceType.DATASET,
            owner = "diana@example.com",
            team = "analytics-team",
            description = "Inactive quality spec for testing",
            tags = mutableSetOf("inactive", "test"),
            scheduleCron = null,
            scheduleTimezone = "UTC",
            enabled = false
        )

        // Set different update times for testing ordering
        val now = LocalDateTime.now()
        testQualitySpec1.updatedAt = now.minusDays(5)
        testQualitySpec2.updatedAt = now.minusDays(3)
        testQualitySpec3.updatedAt = now.minusDays(1)
        testQualitySpec4.updatedAt = now.minusDays(4)
        testQualitySpec5.updatedAt = now.minusDays(2)
    }

    @Nested
    @DisplayName("Complex Filter Queries")
    inner class ComplexFilterQueries {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
            entityManager.persistAndFlush(testQualitySpec4)
            entityManager.persistAndFlush(testQualitySpec5)
        }

        @Test
        @DisplayName("should find by all filters combined")
        fun `should find by all filters combined`() {
            // When
            val result = qualitySpecRepositoryDsl.findByFilters(
                resourceType = ResourceType.DATASET,
                resourceName = null,
                tag = "critical",
                owner = "alice",
                team = "data-team",
                enabled = true,
                search = "users",
                pageable = PageRequest.of(0, 10)
            )

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name).isEqualTo(testQualitySpec1.name)
            assertThat(result.totalElements).isEqualTo(1)
        }

        @Test
        @DisplayName("should find by resource type only")
        fun `should find by resource type only`() {
            // When
            val result = qualitySpecRepositoryDsl.findByFilters(
                resourceType = ResourceType.DATASET,
                pageable = PageRequest.of(0, 10)
            )

            // Then
            assertThat(result.content).hasSize(4)
            assertThat(result.content.map { it.resourceType }).allMatch { it == ResourceType.DATASET }
        }

        @Test
        @DisplayName("should find by tag filter")
        fun `should find by tag filter`() {
            // When
            val result = qualitySpecRepositoryDsl.findByFilters(
                tag = "critical",
                pageable = PageRequest.of(0, 10)
            )

            // Then
            assertThat(result.content).hasSize(2)
            assertThat(result.content.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec1.name,
                testQualitySpec2.name
            )
        }

        @Test
        @DisplayName("should find by owner filter with partial match")
        fun `should find by owner filter with partial match`() {
            // When
            val result = qualitySpecRepositoryDsl.findByFilters(
                owner = "alice",
                pageable = PageRequest.of(0, 10)
            )

            // Then
            assertThat(result.content).hasSize(2)
            assertThat(result.content.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec1.name,
                testQualitySpec3.name
            )
        }

        @Test
        @DisplayName("should find by team filter")
        fun `should find by team filter`() {
            // When
            val result = qualitySpecRepositoryDsl.findByFilters(
                team = "data-team",
                pageable = PageRequest.of(0, 10)
            )

            // Then
            assertThat(result.content).hasSize(2)
            assertThat(result.content.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec1.name,
                testQualitySpec4.name
            )
        }

        @Test
        @DisplayName("should find by enabled status")
        fun `should find by enabled status`() {
            // When
            val enabledResult = qualitySpecRepositoryDsl.findByFilters(
                enabled = true,
                pageable = PageRequest.of(0, 10)
            )
            val disabledResult = qualitySpecRepositoryDsl.findByFilters(
                enabled = false,
                pageable = PageRequest.of(0, 10)
            )

            // Then
            assertThat(enabledResult.content).hasSize(3)
            assertThat(disabledResult.content).hasSize(2)
        }

        @Test
        @DisplayName("should find by search term in name and description")
        fun `should find by search term in name and description`() {
            // When
            val result = qualitySpecRepositoryDsl.findByFilters(
                search = "metrics",
                pageable = PageRequest.of(0, 10)
            )

            // Then
            assertThat(result.content).hasSize(2)
            assertThat(result.content.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec2.name,
                testQualitySpec4.name
            )
        }

        @Test
        @DisplayName("should return results ordered by updatedAt desc")
        fun `should return results ordered by updatedAt desc`() {
            // When
            val result = qualitySpecRepositoryDsl.findByFilters(
                pageable = PageRequest.of(0, 10)
            )

            // Then - Should be ordered by updatedAt DESC (newest first)
            val names = result.content.map { it.name }
            // Based on our setup: spec3 (1 day ago), spec5 (2 days ago), spec2 (3 days ago), spec4 (4 days ago), spec1 (5 days ago)
            assertThat(names).containsExactly(
                testQualitySpec3.name,
                testQualitySpec5.name,
                testQualitySpec2.name,
                testQualitySpec4.name,
                testQualitySpec1.name
            )
        }

        @Test
        @DisplayName("should handle pagination correctly")
        fun `should handle pagination correctly`() {
            // When
            val page0 = qualitySpecRepositoryDsl.findByFilters(
                pageable = PageRequest.of(0, 2)
            )
            val page1 = qualitySpecRepositoryDsl.findByFilters(
                pageable = PageRequest.of(1, 2)
            )

            // Then
            assertThat(page0.content).hasSize(2)
            assertThat(page0.totalElements).isEqualTo(5)
            assertThat(page0.totalPages).isEqualTo(3)
            assertThat(page0.hasNext()).isTrue

            assertThat(page1.content).hasSize(2)
            assertThat(page1.isLast).isFalse
        }

        @Test
        @DisplayName("should return empty page when no matches")
        fun `should return empty page when no matches`() {
            // When
            val result = qualitySpecRepositoryDsl.findByFilters(
                search = "nonexistent_term",
                pageable = PageRequest.of(0, 10)
            )

            // Then
            assertThat(result.content).isEmpty()
            assertThat(result.totalElements).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Count By Filters")
    inner class CountByFilters {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
            entityManager.persistAndFlush(testQualitySpec4)
            entityManager.persistAndFlush(testQualitySpec5)
        }

        @Test
        @DisplayName("should count by resource type")
        fun `should count by resource type`() {
            // When
            val datasetCount = qualitySpecRepositoryDsl.countByFilters(
                resourceType = ResourceType.DATASET
            )
            val metricCount = qualitySpecRepositoryDsl.countByFilters(
                resourceType = ResourceType.METRIC
            )

            // Then
            assertThat(datasetCount).isEqualTo(4)
            assertThat(metricCount).isEqualTo(1)
        }

        @Test
        @DisplayName("should count by enabled status")
        fun `should count by enabled status`() {
            // When
            val enabledCount = qualitySpecRepositoryDsl.countByFilters(enabled = true)
            val disabledCount = qualitySpecRepositoryDsl.countByFilters(enabled = false)

            // Then
            assertThat(enabledCount).isEqualTo(3)
            assertThat(disabledCount).isEqualTo(2)
        }

        @Test
        @DisplayName("should count by tag")
        fun `should count by tag`() {
            // When
            val criticalCount = qualitySpecRepositoryDsl.countByFilters(tag = "critical")
            val productCount = qualitySpecRepositoryDsl.countByFilters(tag = "products")

            // Then
            assertThat(criticalCount).isEqualTo(2)
            assertThat(productCount).isEqualTo(1)
        }

        @Test
        @DisplayName("should count by multiple filters")
        fun `should count by multiple filters`() {
            // When
            val count = qualitySpecRepositoryDsl.countByFilters(
                resourceType = ResourceType.DATASET,
                enabled = true,
                tag = "critical"
            )

            // Then
            assertThat(count).isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("Statistics Operations")
    inner class StatisticsOperations {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
            entityManager.persistAndFlush(testQualitySpec4)
            entityManager.persistAndFlush(testQualitySpec5)
        }

        @Test
        @DisplayName("should get quality spec statistics")
        fun `should get quality spec statistics`() {
            // When
            val stats = qualitySpecRepositoryDsl.getQualitySpecStatistics()

            // Then
            assertThat(stats["totalCount"]).isEqualTo(5L)
            assertThat(stats["enabledCount"]).isEqualTo(3L)
            assertThat(stats["disabledCount"]).isEqualTo(2L)
            assertThat(stats["resourceType"]).isEqualTo("ALL")
            assertThat(stats["owner"]).isEqualTo("ALL")
        }

        @Test
        @DisplayName("should get filtered quality spec statistics")
        fun `should get filtered quality spec statistics`() {
            // When
            val datasetStats = qualitySpecRepositoryDsl.getQualitySpecStatistics(
                resourceType = ResourceType.DATASET
            )
            val aliceStats = qualitySpecRepositoryDsl.getQualitySpecStatistics(
                owner = "alice"
            )

            // Then
            assertThat(datasetStats["totalCount"]).isEqualTo(4L)
            assertThat(datasetStats["resourceType"]).isEqualTo("DATASET")

            assertThat(aliceStats["totalCount"]).isEqualTo(2L)
            assertThat(aliceStats["owner"]).isEqualTo("alice")
        }

        @Test
        @DisplayName("should get quality spec count by resource type")
        fun `should get quality spec count by resource type`() {
            // When
            val counts = qualitySpecRepositoryDsl.getQualitySpecCountByResourceType()

            // Then
            assertThat(counts).hasSize(2)
            val countMap = counts.associateBy { it["resourceType"] }
            assertThat(countMap["DATASET"]?.get("count")).isEqualTo(4L)
            assertThat(countMap["METRIC"]?.get("count")).isEqualTo(1L)
        }

        @Test
        @DisplayName("should get quality spec count by owner")
        fun `should get quality spec count by owner`() {
            // When
            val counts = qualitySpecRepositoryDsl.getQualitySpecCountByOwner()

            // Then
            assertThat(counts).hasSize(4)
            val countMap = counts.associateBy { it["owner"] }
            assertThat(countMap["alice@example.com"]?.get("count")).isEqualTo(2L)
            assertThat(countMap["bob@example.com"]?.get("count")).isEqualTo(1L)
            assertThat(countMap["charlie@example.com"]?.get("count")).isEqualTo(1L)
            assertThat(countMap["diana@example.com"]?.get("count")).isEqualTo(1L)
        }

        @Test
        @DisplayName("should get quality spec count by tag")
        fun `should get quality spec count by tag`() {
            // When
            val counts = qualitySpecRepositoryDsl.getQualitySpecCountByTag()

            // Then
            assertThat(counts).isNotEmpty
            val countMap = counts.associateBy { it["tag"] }
            assertThat(countMap["critical"]?.get("count")).isEqualTo(2)
            assertThat(countMap["users"]?.get("count")).isEqualTo(1)
            assertThat(countMap["products"]?.get("count")).isEqualTo(1)
        }

        @Test
        @DisplayName("should get quality spec count by team")
        fun `should get quality spec count by team`() {
            // When
            val counts = qualitySpecRepositoryDsl.getQualitySpecCountByTeam()

            // Then
            assertThat(counts).hasSize(2)
            val countMap = counts.associateBy { it["team"] }
            assertThat(countMap["data-team"]?.get("count")).isEqualTo(2L)
            assertThat(countMap["analytics-team"]?.get("count")).isEqualTo(2L)
        }
    }

    @Nested
    @DisplayName("Time-Based Queries")
    inner class TimeBasedQueries {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
            entityManager.persistAndFlush(testQualitySpec4)
            entityManager.persistAndFlush(testQualitySpec5)
        }

        @Test
        @DisplayName("should find recently updated quality specs")
        fun `should find recently updated quality specs`() {
            // When
            val recentSpecs = qualitySpecRepositoryDsl.findRecentlyUpdatedQualitySpecs(
                limit = 3,
                daysSince = 3
            )

            // Then - Should return specs updated within 3 days (spec3, spec5, spec2)
            assertThat(recentSpecs).hasSize(3)
            assertThat(recentSpecs.map { it.name }).containsExactly(
                testQualitySpec3.name,
                testQualitySpec5.name,
                testQualitySpec2.name
            )
        }

        @Test
        @DisplayName("should limit recently updated quality specs")
        fun `should limit recently updated quality specs`() {
            // When
            val limitedSpecs = qualitySpecRepositoryDsl.findRecentlyUpdatedQualitySpecs(
                limit = 2,
                daysSince = 10
            )

            // Then
            assertThat(limitedSpecs).hasSize(2)
        }
    }

    @Nested
    @DisplayName("Schedule-Based Queries")
    inner class ScheduleBasedQueries {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
            entityManager.persistAndFlush(testQualitySpec4)
            entityManager.persistAndFlush(testQualitySpec5)
        }

        @Test
        @DisplayName("should find all scheduled quality specs")
        fun `should find all scheduled quality specs`() {
            // When
            val scheduledSpecs = qualitySpecRepositoryDsl.findScheduledQualitySpecs()

            // Then
            assertThat(scheduledSpecs).hasSize(3)
            assertThat(scheduledSpecs.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec1.name,
                testQualitySpec3.name,
                testQualitySpec4.name
            )
        }

        @Test
        @DisplayName("should find scheduled quality specs by cron pattern")
        fun `should find scheduled quality specs by cron pattern`() {
            // When
            val dailySpecs = qualitySpecRepositoryDsl.findScheduledQualitySpecs("0 0")
            val hourlySpecs = qualitySpecRepositoryDsl.findScheduledQualitySpecs("*/6")

            // Then
            assertThat(dailySpecs).hasSize(2)
            assertThat(dailySpecs.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec1.name,
                testQualitySpec3.name
            )

            assertThat(hourlySpecs).hasSize(1)
            assertThat(hourlySpecs[0].name).isEqualTo(testQualitySpec4.name)
        }
    }

    @Nested
    @DisplayName("Resource-Based Queries")
    inner class ResourceBasedQueries {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
            entityManager.persistAndFlush(testQualitySpec4)
            entityManager.persistAndFlush(testQualitySpec5)
        }

        @Test
        @DisplayName("should find quality specs by resource including disabled")
        fun `should find quality specs by resource including disabled`() {
            // When
            val specs = qualitySpecRepositoryDsl.findQualitySpecsByResource(
                resourceName = "iceberg.analytics.products",
                resourceType = ResourceType.DATASET,
                includeDisabled = true
            )

            // Then
            assertThat(specs).hasSize(1)
            assertThat(specs[0].name).isEqualTo(testQualitySpec3.name)
        }

        @Test
        @DisplayName("should find quality specs by resource excluding disabled")
        fun `should find quality specs by resource excluding disabled`() {
            // When
            val specs = qualitySpecRepositoryDsl.findQualitySpecsByResource(
                resourceName = "iceberg.analytics.products",
                resourceType = ResourceType.DATASET,
                includeDisabled = false
            )

            // Then
            assertThat(specs).isEmpty()
        }
    }

    @Nested
    @DisplayName("Active Quality Specs")
    inner class ActiveQualitySpecs {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
            entityManager.persistAndFlush(testQualitySpec4)
            entityManager.persistAndFlush(testQualitySpec5)
        }

        @Test
        @DisplayName("should find all active quality specs")
        fun `should find all active quality specs`() {
            // When
            val activeSpecs = qualitySpecRepositoryDsl.findActiveQualitySpecs()

            // Then - Should only return enabled specs
            assertThat(activeSpecs).hasSize(3)
            assertThat(activeSpecs.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec1.name,
                testQualitySpec2.name,
                testQualitySpec4.name
            )
        }

        @Test
        @DisplayName("should find active specs with schedule")
        fun `should find active specs with schedule`() {
            // When
            val activeScheduledSpecs = qualitySpecRepositoryDsl.findActiveQualitySpecs(
                hasSchedule = true
            )

            // Then
            assertThat(activeScheduledSpecs).hasSize(2)
            assertThat(activeScheduledSpecs.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec1.name,
                testQualitySpec4.name
            )
        }

        @Test
        @DisplayName("should find active specs without schedule")
        fun `should find active specs without schedule`() {
            // When
            val activeNonScheduledSpecs = qualitySpecRepositoryDsl.findActiveQualitySpecs(
                hasSchedule = false
            )

            // Then
            assertThat(activeNonScheduledSpecs).hasSize(1)
            assertThat(activeNonScheduledSpecs[0].name).isEqualTo(testQualitySpec2.name)
        }
    }
}