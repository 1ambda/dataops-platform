package com.dataops.basecamp.infra.repository.quality

import com.dataops.basecamp.common.enums.ResourceType
import com.dataops.basecamp.domain.entity.quality.QualitySpecEntity
import com.dataops.basecamp.infra.config.JpaTestConfig
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
 * QualitySpecRepositoryJpaImpl Integration Tests
 *
 * Tests CRUD operations, resource queries, owner queries, tag queries,
 * enabled status queries, schedule queries, team queries, and pagination.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaTestConfig::class)
@DisplayName("QualitySpecRepositoryJpaImpl Integration Tests")
@Execution(ExecutionMode.SAME_THREAD)
class QualitySpecRepositoryJpaImplTest {
    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    @Autowired
    private lateinit var repository: QualitySpecRepositoryJpaImpl

    private lateinit var spec1: QualitySpecEntity
    private lateinit var spec2: QualitySpecEntity
    private lateinit var spec3: QualitySpecEntity

    @BeforeEach
    fun setUp() {
        // Clean up any existing data from parallel test runs
        testEntityManager.entityManager.createNativeQuery("DELETE FROM quality_spec_tags").executeUpdate()
        testEntityManager.entityManager.createNativeQuery("DELETE FROM quality_specs").executeUpdate()
        testEntityManager.flush()
        testEntityManager.clear()

        // Create test entities
        spec1 =
            QualitySpecEntity(
                name = "quality-spec-1",
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
                name = "quality-spec-2",
                resourceName = "dataset.orders",
                resourceType = ResourceType.DATASET,
                owner = "owner2@example.com",
                team = "analytics-team",
                description = "Quality spec for orders dataset",
                tags = mutableSetOf("orders", "daily"),
                scheduleCron = null,
                enabled = true,
            )

        spec3 =
            QualitySpecEntity(
                name = "quality-spec-3",
                resourceName = "metric.revenue",
                resourceType = ResourceType.METRIC,
                owner = "owner1@example.com",
                team = null,
                description = "Quality spec for revenue metric",
                tags = mutableSetOf("finance"),
                scheduleCron = "0 0 0 * *",
                enabled = false,
            )

        testEntityManager.persistAndFlush(spec1)
        testEntityManager.persistAndFlush(spec2)
        testEntityManager.persistAndFlush(spec3)
        testEntityManager.clear()
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    inner class CrudOperations {
        @Test
        @DisplayName("should save and generate ID")
        fun `should save entity with generated id`() {
            // Given
            val newSpec =
                QualitySpecEntity(
                    name = "new-quality-spec",
                    resourceName = "dataset.new",
                    resourceType = ResourceType.DATASET,
                    owner = "new@example.com",
                )

            // When
            val saved = repository.save(newSpec)
            testEntityManager.flush()

            // Then
            assertThat(saved.id).isNotNull()
            assertThat(saved.name).isEqualTo("new-quality-spec")
        }

        @Test
        @DisplayName("should set auditing fields on persist")
        fun `should set createdAt and updatedAt on persist`() {
            // Given
            val newSpec =
                QualitySpecEntity(
                    name = "audit-test-spec",
                    resourceName = "dataset.audit",
                    resourceType = ResourceType.DATASET,
                    owner = "audit@example.com",
                )

            // When
            val saved = repository.save(newSpec)
            testEntityManager.flush()

            // Then
            assertThat(saved.createdAt).isNotNull()
            assertThat(saved.updatedAt).isNotNull()
        }

        @Test
        @DisplayName("should find by name")
        fun `should return entity when name exists`() {
            // When
            val found = repository.findByName("quality-spec-1")

            // Then
            assertThat(found).isNotNull
            assertThat(found!!.name).isEqualTo("quality-spec-1")
            assertThat(found.resourceName).isEqualTo("dataset.sales")
        }

        @Test
        @DisplayName("should return null when name not found")
        fun `should return null when name not found`() {
            // When
            val found = repository.findByName("nonexistent")

            // Then
            assertThat(found).isNull()
        }

        @Test
        @DisplayName("should check existence by name")
        fun `should return true when name exists`() {
            // When & Then
            assertThat(repository.existsByName("quality-spec-1")).isTrue()
            assertThat(repository.existsByName("nonexistent")).isFalse()
        }

        @Test
        @DisplayName("should delete by name")
        fun `should delete entity by name`() {
            // When
            val deletedCount = repository.deleteByName("quality-spec-1")
            testEntityManager.flush()

            // Then
            assertThat(deletedCount).isEqualTo(1)
            assertThat(repository.findByName("quality-spec-1")).isNull()
        }
    }

    @Nested
    @DisplayName("Resource-based Queries")
    inner class ResourceQueries {
        @Test
        @DisplayName("should find by resource name")
        fun `should find specs by resource name`() {
            // When
            val result = repository.findByResourceName("dataset.sales")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("quality-spec-1")
        }

        @Test
        @DisplayName("should find by resource type")
        fun `should find specs by resource type`() {
            // When
            val datasets = repository.findByResourceType(ResourceType.DATASET)
            val metrics = repository.findByResourceType(ResourceType.METRIC)

            // Then
            assertThat(datasets).hasSize(2)
            assertThat(metrics).hasSize(1)
            assertThat(metrics[0].name).isEqualTo("quality-spec-3")
        }

        @Test
        @DisplayName("should find by resource name and type")
        fun `should find specs by resource name and type`() {
            // When
            val result =
                repository.findByResourceNameAndResourceType(
                    "dataset.sales",
                    ResourceType.DATASET,
                )

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("quality-spec-1")
        }
    }

    @Nested
    @DisplayName("Owner-based Queries")
    inner class OwnerQueries {
        @Test
        @DisplayName("should find by owner")
        fun `should find specs by owner`() {
            // When
            val result = repository.findByOwner("owner1@example.com")

            // Then
            assertThat(result).hasSize(2)
            assertThat(result.map { it.name }).containsExactlyInAnyOrder("quality-spec-1", "quality-spec-3")
        }

        @Test
        @DisplayName("should count by owner")
        fun `should count specs by owner`() {
            // When & Then
            assertThat(repository.countByOwner("owner1@example.com")).isEqualTo(2)
            assertThat(repository.countByOwner("owner2@example.com")).isEqualTo(1)
            assertThat(repository.countByOwner("nonexistent@example.com")).isEqualTo(0)
        }
    }

    @Nested
    @DisplayName("Tag-based Queries")
    inner class TagQueries {
        @Test
        @DisplayName("should find by tag")
        fun `should find specs containing tag`() {
            // When
            val salesResult = repository.findByTagsContaining("sales")
            val criticalResult = repository.findByTagsContaining("critical")

            // Then
            assertThat(salesResult).hasSize(1)
            assertThat(salesResult[0].name).isEqualTo("quality-spec-1")
            assertThat(criticalResult).hasSize(1)
        }
    }

    @Nested
    @DisplayName("Enabled Status Queries")
    inner class EnabledQueries {
        @Test
        @DisplayName("should find by enabled status")
        fun `should find specs by enabled status`() {
            // When
            val enabled = repository.findByEnabled(true)
            val disabled = repository.findByEnabled(false)

            // Then
            assertThat(enabled).hasSize(2)
            assertThat(disabled).hasSize(1)
            assertThat(disabled[0].name).isEqualTo("quality-spec-3")
        }

        @Test
        @DisplayName("should count by enabled status")
        fun `should count specs by enabled status`() {
            // When & Then
            assertThat(repository.countByEnabled(true)).isEqualTo(2)
            assertThat(repository.countByEnabled(false)).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Schedule-based Queries")
    inner class ScheduleQueries {
        @Test
        @DisplayName("should find specs with schedule")
        fun `should find specs with schedule cron set`() {
            // When
            val withSchedule = repository.findByScheduleCronIsNotNull()

            // Then
            assertThat(withSchedule).hasSize(2)
            assertThat(withSchedule.map { it.name }).containsExactlyInAnyOrder("quality-spec-1", "quality-spec-3")
        }

        @Test
        @DisplayName("should find specs without schedule")
        fun `should find specs without schedule cron`() {
            // When
            val withoutSchedule = repository.findByScheduleCronIsNull()

            // Then
            assertThat(withoutSchedule).hasSize(1)
            assertThat(withoutSchedule[0].name).isEqualTo("quality-spec-2")
        }
    }

    @Nested
    @DisplayName("Team-based Queries")
    inner class TeamQueries {
        @Test
        @DisplayName("should find by team")
        fun `should find specs by team`() {
            // When
            val dataTeam = repository.findByTeam("data-team")
            val analyticsTeam = repository.findByTeam("analytics-team")

            // Then
            assertThat(dataTeam).hasSize(1)
            assertThat(dataTeam[0].name).isEqualTo("quality-spec-1")
            assertThat(analyticsTeam).hasSize(1)
            assertThat(analyticsTeam[0].name).isEqualTo("quality-spec-2")
        }

        @Test
        @DisplayName("should find specs with null team")
        fun `should find specs with null team`() {
            // When
            val noTeam = repository.findByTeamIsNull()

            // Then
            assertThat(noTeam).hasSize(1)
            assertThat(noTeam[0].name).isEqualTo("quality-spec-3")
        }
    }

    @Nested
    @DisplayName("Search Operations")
    inner class SearchOperations {
        @Test
        @DisplayName("should search by name pattern")
        fun `should find specs by name pattern`() {
            // When
            val result = repository.findByNameContainingIgnoreCase("spec-1")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("quality-spec-1")
        }

        @Test
        @DisplayName("should search by description pattern")
        fun `should find specs by description pattern`() {
            // When
            val result = repository.findByDescriptionContainingIgnoreCase("sales")

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo("quality-spec-1")
        }
    }

    @Nested
    @DisplayName("Statistics and Counts")
    inner class StatisticsQueries {
        @Test
        @DisplayName("should count by resource type")
        fun `should count specs by resource type`() {
            // When & Then
            assertThat(repository.countByResourceType(ResourceType.DATASET)).isEqualTo(2)
            assertThat(repository.countByResourceType(ResourceType.METRIC)).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Pagination Queries")
    inner class PaginationQueries {
        @Test
        @DisplayName("should return paginated results ordered by updatedAt desc")
        fun `should return paginated results`() {
            // Given
            val pageable = PageRequest.of(0, 2)

            // When
            val page = repository.findAllByOrderByUpdatedAtDesc(pageable)

            // Then
            assertThat(page.content).hasSize(2)
            assertThat(page.totalElements).isEqualTo(3)
            assertThat(page.totalPages).isEqualTo(2)
        }

        @Test
        @DisplayName("should return owner's specs with pagination")
        fun `should return owner specs paginated`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val page = repository.findByOwnerOrderByUpdatedAtDesc("owner1@example.com", pageable)

            // Then
            assertThat(page.content).hasSize(2)
            assertThat(page.totalElements).isEqualTo(2)
        }

        @Test
        @DisplayName("should return enabled specs with pagination")
        fun `should return enabled specs paginated`() {
            // Given
            val pageable = PageRequest.of(0, 10)

            // When
            val page = repository.findByEnabledOrderByUpdatedAtDesc(true, pageable)

            // Then
            assertThat(page.content).hasSize(2)
            assertThat(page.totalElements).isEqualTo(2)
        }
    }
}
