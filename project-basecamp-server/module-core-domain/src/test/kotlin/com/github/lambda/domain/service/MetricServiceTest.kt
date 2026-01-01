package com.github.lambda.domain.service

import com.github.lambda.domain.exception.MetricAlreadyExistsException
import com.github.lambda.domain.exception.MetricNotFoundException
import com.github.lambda.domain.model.metric.MetricEntity
import com.github.lambda.domain.repository.MetricRepositoryDsl
import com.github.lambda.domain.repository.MetricRepositoryJpa
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

/**
 * MetricService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("MetricService Unit Tests")
class MetricServiceTest {
    private val metricRepositoryJpa: MetricRepositoryJpa = mockk()
    private val metricRepositoryDsl: MetricRepositoryDsl = mockk()
    private val metricService = MetricService(metricRepositoryJpa, metricRepositoryDsl)

    private lateinit var testMetric: MetricEntity

    @BeforeEach
    fun setUp() {
        testMetric =
            MetricEntity(
                name = "test_catalog.test_schema.test_metric",
                owner = "test@example.com",
                team = "data-team",
                description = "Test metric description",
                sql = "SELECT COUNT(*) as count FROM users",
                sourceTable = "users",
                tags = mutableSetOf("test", "user"),
                dependencies = mutableSetOf("users"),
            )
    }

    @Nested
    @DisplayName("createMetric")
    inner class CreateMetric {
        @Test
        @DisplayName("should create metric successfully when name does not exist")
        fun `should create metric successfully when name does not exist`() {
            // Given
            val name = "new_catalog.new_schema.new_metric"
            val owner = "new@example.com"
            val sql = "SELECT SUM(amount) FROM orders"

            every { metricRepositoryJpa.existsByName(name) } returns false

            val savedMetricSlot = slot<MetricEntity>()
            every { metricRepositoryJpa.save(capture(savedMetricSlot)) } answers { savedMetricSlot.captured }

            // When
            val result =
                metricService.createMetric(
                    name = name,
                    owner = owner,
                    sql = sql,
                    description = "New metric",
                    tags = listOf("new"),
                )

            // Then
            assertThat(result.name).isEqualTo(name)
            assertThat(result.owner).isEqualTo(owner)
            assertThat(result.sql).isEqualTo(sql)
            assertThat(result.tags).contains("new")
            assertThat(result.dependencies).contains("orders")

            verify(exactly = 1) { metricRepositoryJpa.existsByName(name) }
            verify(exactly = 1) { metricRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw MetricAlreadyExistsException when metric already exists")
        fun `should throw MetricAlreadyExistsException when metric already exists`() {
            // Given
            val name = "existing_catalog.schema.metric"
            every { metricRepositoryJpa.existsByName(name) } returns true

            // When & Then
            val exception =
                assertThrows<MetricAlreadyExistsException> {
                    metricService.createMetric(
                        name = name,
                        owner = "test@example.com",
                        sql = "SELECT 1",
                    )
                }

            assertThat(exception.message).contains(name)
            verify(exactly = 1) { metricRepositoryJpa.existsByName(name) }
            verify(exactly = 0) { metricRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should extract multiple dependencies from complex SQL")
        fun `should extract multiple dependencies from complex SQL`() {
            // Given
            val name = "catalog.schema.complex_metric"
            val sql =
                """
                SELECT a.id, b.name, c.value
                FROM table_a a
                JOIN table_b b ON a.id = b.a_id
                LEFT JOIN table_c c ON b.id = c.b_id
                """.trimIndent()

            every { metricRepositoryJpa.existsByName(name) } returns false

            val savedMetricSlot = slot<MetricEntity>()
            every { metricRepositoryJpa.save(capture(savedMetricSlot)) } answers { savedMetricSlot.captured }

            // When
            val result =
                metricService.createMetric(
                    name = name,
                    owner = "test@example.com",
                    sql = sql,
                )

            // Then
            assertThat(result.dependencies).containsExactlyInAnyOrder("table_a", "table_b", "table_c")
        }
    }

    @Nested
    @DisplayName("getMetric")
    inner class GetMetric {
        @Test
        @DisplayName("should return metric when found by name")
        fun `should return metric when found by name`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            every { metricRepositoryJpa.findByName(name) } returns testMetric

            // When
            val result = metricService.getMetric(name)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.name).isEqualTo(name)
            assertThat(result?.owner).isEqualTo("test@example.com")
            verify(exactly = 1) { metricRepositoryJpa.findByName(name) }
        }

        @Test
        @DisplayName("should return null when metric not found")
        fun `should return null when metric not found`() {
            // Given
            val name = "nonexistent_catalog.schema.metric"
            every { metricRepositoryJpa.findByName(name) } returns null

            // When
            val result = metricService.getMetric(name)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { metricRepositoryJpa.findByName(name) }
        }

        @Test
        @DisplayName("should return null for soft-deleted metric")
        fun `should return null for soft-deleted metric`() {
            // Given
            val name = "deleted_catalog.schema.metric"
            val deletedMetric =
                testMetric.apply {
                    deletedAt = java.time.LocalDateTime.now()
                }
            every { metricRepositoryJpa.findByName(name) } returns deletedMetric

            // When
            val result = metricService.getMetric(name)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getMetricById")
    inner class GetMetricById {
        @Test
        @DisplayName("should return metric when found by id")
        fun `should return metric when found by id`() {
            // Given
            val id = 1L
            every { metricRepositoryJpa.findById(id) } returns java.util.Optional.of(testMetric)

            // When
            val result = metricService.getMetricById(id)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.name).isEqualTo(testMetric.name)
            verify(exactly = 1) { metricRepositoryJpa.findById(id) }
        }

        @Test
        @DisplayName("should return null when metric not found by id")
        fun `should return null when metric not found by id`() {
            // Given
            val id = 999L
            every { metricRepositoryJpa.findById(id) } returns java.util.Optional.empty()

            // When
            val result = metricService.getMetricById(id)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { metricRepositoryJpa.findById(id) }
        }

        @Test
        @DisplayName("should return null for soft-deleted metric by id")
        fun `should return null for soft-deleted metric by id`() {
            // Given
            val id = 1L
            val deletedMetric =
                MetricEntity(
                    name = "deleted_catalog.schema.metric",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                ).apply {
                    deletedAt = java.time.LocalDateTime.now()
                }
            every { metricRepositoryJpa.findById(id) } returns java.util.Optional.of(deletedMetric)

            // When
            val result = metricService.getMetricById(id)

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("getMetricOrThrow")
    inner class GetMetricOrThrow {
        @Test
        @DisplayName("should return metric when found")
        fun `should return metric when found`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            every { metricRepositoryJpa.findByName(name) } returns testMetric

            // When
            val result = metricService.getMetricOrThrow(name)

            // Then
            assertThat(result.name).isEqualTo(name)
        }

        @Test
        @DisplayName("should throw MetricNotFoundException when not found")
        fun `should throw MetricNotFoundException when not found`() {
            // Given
            val name = "nonexistent_catalog.schema.metric"
            every { metricRepositoryJpa.findByName(name) } returns null

            // When & Then
            val exception =
                assertThrows<MetricNotFoundException> {
                    metricService.getMetricOrThrow(name)
                }

            assertThat(exception.message).contains(name)
        }
    }

    @Nested
    @DisplayName("listMetrics")
    inner class ListMetrics {
        @Test
        @DisplayName("should return all metrics without filters")
        fun `should return all metrics without filters`() {
            // Given
            val metrics = listOf(testMetric)
            every {
                metricRepositoryDsl.findByFilters(
                    tag = null,
                    owner = null,
                    search = null,
                    limit = 50,
                    offset = 0,
                )
            } returns metrics

            // When
            val result = metricService.listMetrics()

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].name).isEqualTo(testMetric.name)
        }

        @Test
        @DisplayName("should return filtered metrics by tag")
        fun `should return filtered metrics by tag`() {
            // Given
            val tag = "test"
            val metrics = listOf(testMetric)
            every {
                metricRepositoryDsl.findByFilters(
                    tag = tag,
                    owner = null,
                    search = null,
                    limit = 50,
                    offset = 0,
                )
            } returns metrics

            // When
            val result = metricService.listMetrics(tag = tag)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) {
                metricRepositoryDsl.findByFilters(
                    tag = tag,
                    owner = null,
                    search = null,
                    limit = 50,
                    offset = 0,
                )
            }
        }

        @Test
        @DisplayName("should return empty list when no metrics match filters")
        fun `should return empty list when no metrics match filters`() {
            // Given
            every {
                metricRepositoryDsl.findByFilters(
                    tag = "nonexistent",
                    owner = null,
                    search = null,
                    limit = 50,
                    offset = 0,
                )
            } returns emptyList()

            // When
            val result = metricService.listMetrics(tag = "nonexistent")

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("should apply limit and offset correctly")
        fun `should apply limit and offset correctly`() {
            // Given
            val metrics = listOf(testMetric)
            every {
                metricRepositoryDsl.findByFilters(
                    tag = null,
                    owner = null,
                    search = null,
                    limit = 10,
                    offset = 5,
                )
            } returns metrics

            // When
            val result = metricService.listMetrics(limit = 10, offset = 5)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) {
                metricRepositoryDsl.findByFilters(
                    tag = null,
                    owner = null,
                    search = null,
                    limit = 10,
                    offset = 5,
                )
            }
        }

        @Test
        @DisplayName("should filter metrics by search term")
        fun `should filter metrics by search term`() {
            // Given
            val search = "count"
            val metrics = listOf(testMetric)
            every {
                metricRepositoryDsl.findByFilters(
                    tag = null,
                    owner = null,
                    search = search,
                    limit = 50,
                    offset = 0,
                )
            } returns metrics

            // When
            val result = metricService.listMetrics(search = search)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) {
                metricRepositoryDsl.findByFilters(
                    tag = null,
                    owner = null,
                    search = search,
                    limit = 50,
                    offset = 0,
                )
            }
        }

        @Test
        @DisplayName("should combine multiple filters")
        fun `should combine multiple filters`() {
            // Given
            val tag = "test"
            val owner = "test@example.com"
            val search = "user"
            val metrics = listOf(testMetric)
            every {
                metricRepositoryDsl.findByFilters(
                    tag = tag,
                    owner = owner,
                    search = search,
                    limit = 25,
                    offset = 10,
                )
            } returns metrics

            // When
            val result = metricService.listMetrics(tag = tag, owner = owner, search = search, limit = 25, offset = 10)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) {
                metricRepositoryDsl.findByFilters(
                    tag = tag,
                    owner = owner,
                    search = search,
                    limit = 25,
                    offset = 10,
                )
            }
        }
    }

    @Nested
    @DisplayName("updateMetric")
    inner class UpdateMetric {
        @Test
        @DisplayName("should update metric description successfully")
        fun `should update metric description successfully`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val newDescription = "Updated description"

            every { metricRepositoryJpa.findByName(name) } returns testMetric
            every { metricRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                metricService.updateMetric(
                    name = name,
                    description = newDescription,
                )

            // Then
            assertThat(result.description).isEqualTo(newDescription)
            verify(exactly = 1) { metricRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should update SQL and re-extract dependencies")
        fun `should update SQL and re-extract dependencies`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val newSql = "SELECT * FROM new_table JOIN other_table ON 1=1"

            every { metricRepositoryJpa.findByName(name) } returns testMetric
            every { metricRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                metricService.updateMetric(
                    name = name,
                    sql = newSql,
                )

            // Then
            assertThat(result.sql).isEqualTo(newSql)
            assertThat(result.dependencies).containsExactlyInAnyOrder("new_table", "other_table")
        }

        @Test
        @DisplayName("should throw MetricNotFoundException when updating non-existent metric")
        fun `should throw MetricNotFoundException when updating non-existent metric`() {
            // Given
            val name = "nonexistent_catalog.schema.metric"
            every { metricRepositoryJpa.findByName(name) } returns null

            // When & Then
            assertThrows<MetricNotFoundException> {
                metricService.updateMetric(name = name, description = "New description")
            }
        }

        @Test
        @DisplayName("should update tags only")
        fun `should update tags only`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val newTags = listOf("tag1", "tag2", "tag3")

            every { metricRepositoryJpa.findByName(name) } returns testMetric
            every { metricRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                metricService.updateMetric(
                    name = name,
                    tags = newTags,
                )

            // Then
            assertThat(result.tags).containsExactlyInAnyOrder("tag1", "tag2", "tag3")
            assertThat(result.sql).isEqualTo(testMetric.sql) // SQL unchanged
            verify(exactly = 1) { metricRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should update multiple fields at once")
        fun `should update multiple fields at once`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val newDescription = "Updated description"
            val newSql = "SELECT * FROM updated_table"
            val newSourceTable = "updated_table"
            val newTags = listOf("updated", "multi-field")

            every { metricRepositoryJpa.findByName(name) } returns testMetric
            every { metricRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                metricService.updateMetric(
                    name = name,
                    description = newDescription,
                    sql = newSql,
                    sourceTable = newSourceTable,
                    tags = newTags,
                )

            // Then
            assertThat(result.description).isEqualTo(newDescription)
            assertThat(result.sql).isEqualTo(newSql)
            assertThat(result.sourceTable).isEqualTo(newSourceTable)
            assertThat(result.tags).containsExactlyInAnyOrder("updated", "multi-field")
            assertThat(result.dependencies).contains("updated_table")
        }

        @Test
        @DisplayName("should update sourceTable only")
        fun `should update sourceTable only`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            val newSourceTable = "new_source"

            every { metricRepositoryJpa.findByName(name) } returns testMetric
            every { metricRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result =
                metricService.updateMetric(
                    name = name,
                    sourceTable = newSourceTable,
                )

            // Then
            assertThat(result.sourceTable).isEqualTo(newSourceTable)
            assertThat(result.sql).isEqualTo(testMetric.sql) // SQL unchanged
        }
    }

    @Nested
    @DisplayName("deleteMetric")
    inner class DeleteMetric {
        @Test
        @DisplayName("should soft delete metric successfully")
        fun `should soft delete metric successfully`() {
            // Given
            val name = "test_catalog.test_schema.test_metric"
            every { metricRepositoryJpa.findByName(name) } returns testMetric
            every { metricRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = metricService.deleteMetric(name)

            // Then
            assertThat(result).isTrue()
            assertThat(testMetric.deletedAt).isNotNull()
            verify(exactly = 1) { metricRepositoryJpa.save(testMetric) }
        }

        @Test
        @DisplayName("should throw MetricNotFoundException when deleting non-existent metric")
        fun `should throw MetricNotFoundException when deleting non-existent metric`() {
            // Given
            val name = "nonexistent_catalog.schema.metric"
            every { metricRepositoryJpa.findByName(name) } returns null

            // When & Then
            assertThrows<MetricNotFoundException> {
                metricService.deleteMetric(name)
            }
        }
    }
}
