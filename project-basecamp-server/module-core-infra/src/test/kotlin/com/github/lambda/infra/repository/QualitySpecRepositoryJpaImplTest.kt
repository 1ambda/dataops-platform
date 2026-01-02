package com.github.lambda.infra.repository

import com.github.lambda.domain.model.quality.ResourceType
import com.github.lambda.domain.model.quality.QualitySpecEntity
import com.github.lambda.domain.repository.QualitySpecRepositoryJpa
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

/**
 * QualitySpecRepositoryJpaImpl Integration Tests
 *
 * Uses @DataJpaTest for database layer testing with H2 in-memory database.
 */
@DataJpaTest
@Import(QualitySpecRepositoryJpaImpl::class)
@ComponentScan(basePackages = ["com.github.lambda.infra.repository"])
@ActiveProfiles("test")
@DisplayName("QualitySpecRepositoryJpaImpl Integration Tests")
class QualitySpecRepositoryJpaImplTest {

    @Autowired
    private lateinit var qualitySpecRepository: QualitySpecRepositoryJpa

    @Autowired
    private lateinit var entityManager: TestEntityManager

    private lateinit var testQualitySpec1: QualitySpecEntity
    private lateinit var testQualitySpec2: QualitySpecEntity
    private lateinit var testQualitySpec3: QualitySpecEntity

    @BeforeEach
    fun setUp() {
        testQualitySpec1 = QualitySpecEntity(
            name = "users_quality_spec",
            resourceName = "iceberg.analytics.users",
            resourceType = ResourceType.DATASET,
            owner = "alice@example.com",
            team = "data-team",
            description = "Quality spec for users dataset",
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
            description = "Quality spec for orders dataset",
            tags = mutableSetOf("orders", "business"),
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
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    inner class BasicCrudOperations {

        @Test
        @DisplayName("should save quality spec successfully")
        fun `should save quality spec successfully`() {
            // When
            val saved = qualitySpecRepository.save(testQualitySpec1)
            entityManager.flush()

            // Then
            assertThat(saved).isNotNull
            assertThat(saved.id).isNotNull
            assertThat(saved.name).isEqualTo(testQualitySpec1.name)
            assertThat(saved.resourceName).isEqualTo(testQualitySpec1.resourceName)
            assertThat(saved.owner).isEqualTo(testQualitySpec1.owner)
            assertThat(saved.team).isEqualTo(testQualitySpec1.team)
            assertThat(saved.tags).containsExactlyInAnyOrder("users", "critical")
        }

        @Test
        @DisplayName("should find quality spec by name")
        fun `should find quality spec by name`() {
            // Given
            entityManager.persistAndFlush(testQualitySpec1)

            // When
            val found = qualitySpecRepository.findByName(testQualitySpec1.name)

            // Then
            assertThat(found).isNotNull
            assertThat(found?.name).isEqualTo(testQualitySpec1.name)
            assertThat(found?.resourceName).isEqualTo(testQualitySpec1.resourceName)
        }

        @Test
        @DisplayName("should return null when quality spec not found by name")
        fun `should return null when quality spec not found by name`() {
            // When
            val found = qualitySpecRepository.findByName("nonexistent_spec")

            // Then
            assertThat(found).isNull()
        }

        @Test
        @DisplayName("should check if quality spec exists by name")
        fun `should check if quality spec exists by name`() {
            // Given
            entityManager.persistAndFlush(testQualitySpec1)

            // When
            val exists = qualitySpecRepository.existsByName(testQualitySpec1.name)
            val notExists = qualitySpecRepository.existsByName("nonexistent_spec")

            // Then
            assertThat(exists).isTrue
            assertThat(notExists).isFalse
        }

        @Test
        @DisplayName("should delete quality spec by name")
        fun `should delete quality spec by name`() {
            // Given
            entityManager.persistAndFlush(testQualitySpec1)
            val initialCount = qualitySpecRepository.count()

            // When
            val deletedCount = qualitySpecRepository.deleteByName(testQualitySpec1.name)

            // Then
            assertThat(deletedCount).isEqualTo(1L)
            assertThat(qualitySpecRepository.count()).isEqualTo(initialCount - 1)
            assertThat(qualitySpecRepository.findByName(testQualitySpec1.name)).isNull()
        }
    }

    @Nested
    @DisplayName("Resource Based Queries")
    inner class ResourceBasedQueries {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
        }

        @Test
        @DisplayName("should find quality specs by resource name")
        fun `should find quality specs by resource name`() {
            // When
            val found = qualitySpecRepository.findByResourceName("iceberg.analytics.users")

            // Then
            assertThat(found).hasSize(1)
            assertThat(found[0].name).isEqualTo(testQualitySpec1.name)
        }

        @Test
        @DisplayName("should find quality specs by resource type")
        fun `should find quality specs by resource type`() {
            // When
            val found = qualitySpecRepository.findByResourceType(ResourceType.DATASET)

            // Then
            assertThat(found).hasSize(3)
            assertThat(found.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec1.name,
                testQualitySpec2.name,
                testQualitySpec3.name
            )
        }

        @Test
        @DisplayName("should find quality specs by resource name and type")
        fun `should find quality specs by resource name and type`() {
            // When
            val found = qualitySpecRepository.findByResourceNameAndResourceType(
                "iceberg.analytics.orders",
                ResourceType.DATASET
            )

            // Then
            assertThat(found).hasSize(1)
            assertThat(found[0].name).isEqualTo(testQualitySpec2.name)
        }
    }

    @Nested
    @DisplayName("Owner Based Queries")
    inner class OwnerBasedQueries {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
        }

        @Test
        @DisplayName("should find quality specs by owner")
        fun `should find quality specs by owner`() {
            // When
            val found = qualitySpecRepository.findByOwner("alice@example.com")

            // Then
            assertThat(found).hasSize(2)
            assertThat(found.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec1.name,
                testQualitySpec3.name
            )
        }

        @Test
        @DisplayName("should find quality specs by owner with pagination")
        fun `should find quality specs by owner with pagination`() {
            // When
            val page = qualitySpecRepository.findByOwnerOrderByUpdatedAtDesc(
                "alice@example.com",
                PageRequest.of(0, 1)
            )

            // Then
            assertThat(page.content).hasSize(1)
            assertThat(page.totalElements).isEqualTo(2)
            assertThat(page.totalPages).isEqualTo(2)
        }

        @Test
        @DisplayName("should count quality specs by owner")
        fun `should count quality specs by owner`() {
            // When
            val count = qualitySpecRepository.countByOwner("alice@example.com")

            // Then
            assertThat(count).isEqualTo(2)
        }
    }

    @Nested
    @DisplayName("Tag Based Queries")
    inner class TagBasedQueries {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
        }

        @Test
        @DisplayName("should find quality specs containing specific tag")
        fun `should find quality specs containing specific tag`() {
            // When
            val found = qualitySpecRepository.findByTagsContaining("users")

            // Then
            assertThat(found).hasSize(1)
            assertThat(found[0].name).isEqualTo(testQualitySpec1.name)
        }

        @Test
        @DisplayName("should return empty list when no specs contain tag")
        fun `should return empty list when no specs contain tag`() {
            // When
            val found = qualitySpecRepository.findByTagsContaining("nonexistent")

            // Then
            assertThat(found).isEmpty()
        }
    }

    @Nested
    @DisplayName("Enabled Status Queries")
    inner class EnabledStatusQueries {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
        }

        @Test
        @DisplayName("should find enabled quality specs")
        fun `should find enabled quality specs`() {
            // When
            val found = qualitySpecRepository.findByEnabled(true)

            // Then
            assertThat(found).hasSize(2)
            assertThat(found.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec1.name,
                testQualitySpec2.name
            )
        }

        @Test
        @DisplayName("should find disabled quality specs")
        fun `should find disabled quality specs`() {
            // When
            val found = qualitySpecRepository.findByEnabled(false)

            // Then
            assertThat(found).hasSize(1)
            assertThat(found[0].name).isEqualTo(testQualitySpec3.name)
        }

        @Test
        @DisplayName("should find enabled specs with pagination")
        fun `should find enabled specs with pagination`() {
            // When
            val page = qualitySpecRepository.findByEnabledOrderByUpdatedAtDesc(
                true,
                PageRequest.of(0, 1)
            )

            // Then
            assertThat(page.content).hasSize(1)
            assertThat(page.totalElements).isEqualTo(2)
        }

        @Test
        @DisplayName("should count enabled and disabled specs")
        fun `should count enabled and disabled specs`() {
            // When
            val enabledCount = qualitySpecRepository.countByEnabled(true)
            val disabledCount = qualitySpecRepository.countByEnabled(false)

            // Then
            assertThat(enabledCount).isEqualTo(2)
            assertThat(disabledCount).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Schedule Based Queries")
    inner class ScheduleBasedQueries {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
        }

        @Test
        @DisplayName("should find specs with schedule")
        fun `should find specs with schedule`() {
            // When
            val found = qualitySpecRepository.findByScheduleCronIsNotNull()

            // Then
            assertThat(found).hasSize(2)
            assertThat(found.map { it.name }).containsExactlyInAnyOrder(
                testQualitySpec1.name,
                testQualitySpec3.name
            )
        }

        @Test
        @DisplayName("should find specs without schedule")
        fun `should find specs without schedule`() {
            // When
            val found = qualitySpecRepository.findByScheduleCronIsNull()

            // Then
            assertThat(found).hasSize(1)
            assertThat(found[0].name).isEqualTo(testQualitySpec2.name)
        }
    }

    @Nested
    @DisplayName("Team Based Queries")
    inner class TeamBasedQueries {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
        }

        @Test
        @DisplayName("should find quality specs by team")
        fun `should find quality specs by team`() {
            // When
            val found = qualitySpecRepository.findByTeam("data-team")

            // Then
            assertThat(found).hasSize(1)
            assertThat(found[0].name).isEqualTo(testQualitySpec1.name)
        }

        @Test
        @DisplayName("should find specs with no team assigned")
        fun `should find specs with no team assigned`() {
            // When
            val found = qualitySpecRepository.findByTeamIsNull()

            // Then
            assertThat(found).hasSize(1)
            assertThat(found[0].name).isEqualTo(testQualitySpec3.name)
        }
    }

    @Nested
    @DisplayName("Search Operations")
    inner class SearchOperations {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
        }

        @Test
        @DisplayName("should search quality specs by name pattern")
        fun `should search quality specs by name pattern`() {
            // When
            val found = qualitySpecRepository.findByNameContainingIgnoreCase("users")

            // Then
            assertThat(found).hasSize(1)
            assertThat(found[0].name).isEqualTo(testQualitySpec1.name)
        }

        @Test
        @DisplayName("should search quality specs by description pattern")
        fun `should search quality specs by description pattern`() {
            // When
            val found = qualitySpecRepository.findByDescriptionContainingIgnoreCase("orders")

            // Then
            assertThat(found).hasSize(1)
            assertThat(found[0].name).isEqualTo(testQualitySpec2.name)
        }
    }

    @Nested
    @DisplayName("Statistics and Counts")
    inner class StatisticsAndCounts {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
        }

        @Test
        @DisplayName("should count by resource type")
        fun `should count by resource type`() {
            // When
            val count = qualitySpecRepository.countByResourceType(ResourceType.DATASET)

            // Then
            assertThat(count).isEqualTo(3)
        }

        @Test
        @DisplayName("should count total specs")
        fun `should count total specs`() {
            // When
            val count = qualitySpecRepository.count()

            // Then
            assertThat(count).isEqualTo(3)
        }
    }

    @Nested
    @DisplayName("Pagination Queries")
    inner class PaginationQueries {

        @BeforeEach
        fun setUpData() {
            entityManager.persistAndFlush(testQualitySpec1)
            entityManager.persistAndFlush(testQualitySpec2)
            entityManager.persistAndFlush(testQualitySpec3)
        }

        @Test
        @DisplayName("should return all specs with pagination")
        fun `should return all specs with pagination`() {
            // When
            val page = qualitySpecRepository.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, 2))

            // Then
            assertThat(page.content).hasSize(2)
            assertThat(page.totalElements).isEqualTo(3)
            assertThat(page.totalPages).isEqualTo(2)
            assertThat(page.hasNext()).isTrue
        }
    }
}