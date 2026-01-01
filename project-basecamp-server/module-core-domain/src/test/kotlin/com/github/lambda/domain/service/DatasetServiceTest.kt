package com.github.lambda.domain.service

import com.github.lambda.common.exception.*
import com.github.lambda.domain.model.dataset.DatasetEntity
import com.github.lambda.domain.repository.DatasetRepositoryDsl
import com.github.lambda.domain.repository.DatasetRepositoryJpa
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
import org.springframework.data.domain.Sort
import java.time.LocalDateTime

/**
 * DatasetService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("DatasetService Unit Tests")
class DatasetServiceTest {
    private val datasetRepositoryJpa: DatasetRepositoryJpa = mockk()
    private val datasetRepositoryDsl: DatasetRepositoryDsl = mockk()
    private val datasetService = DatasetService(datasetRepositoryJpa, datasetRepositoryDsl)

    private lateinit var testDataset: DatasetEntity

    @BeforeEach
    fun setUp() {
        testDataset =
            DatasetEntity(
                name = "test_catalog.test_schema.test_dataset",
                owner = "test@example.com",
                team = "data-team",
                description = "Test dataset description",
                sql = "SELECT id, name, created_at FROM users WHERE created_at >= '{{date}}'",
                tags = setOf("test", "user"),
                dependencies = setOf("users"),
                scheduleCron = "0 9 * * *",
                scheduleTimezone = "UTC",
            )
    }

    @Nested
    @DisplayName("registerDataset")
    inner class RegisterDataset {
        @Test
        @DisplayName("should register dataset successfully when name does not exist")
        fun `should register dataset successfully when name does not exist`() {
            // Given
            val dataset =
                DatasetEntity(
                    name = "new_catalog.new_schema.new_dataset",
                    owner = "new@example.com",
                    sql = "SELECT * FROM new_table",
                    description = "New dataset",
                    tags = setOf("new"),
                )

            every { datasetRepositoryJpa.existsByName(dataset.name) } returns false

            val savedDatasetSlot = slot<DatasetEntity>()
            every { datasetRepositoryJpa.save(capture(savedDatasetSlot)) } answers { savedDatasetSlot.captured }

            // When
            val result = datasetService.registerDataset(dataset)

            // Then
            assertThat(result.name).isEqualTo(dataset.name)
            assertThat(result.owner).isEqualTo(dataset.owner)
            assertThat(result.sql).isEqualTo(dataset.sql)
            assertThat(result.description).isEqualTo(dataset.description)
            assertThat(result.tags).contains("new")

            verify(exactly = 1) { datasetRepositoryJpa.existsByName(dataset.name) }
            verify(exactly = 1) { datasetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw DatasetAlreadyExistsException when dataset already exists")
        fun `should throw DatasetAlreadyExistsException when dataset already exists`() {
            // Given
            val name = "existing_catalog.schema.dataset"
            val dataset =
                DatasetEntity(
                    name = name,
                    owner = "test@example.com",
                    sql = "SELECT 1",
                )
            every { datasetRepositoryJpa.existsByName(name) } returns true

            // When & Then
            val exception =
                assertThrows<DatasetAlreadyExistsException> {
                    datasetService.registerDataset(dataset)
                }

            assertThat(exception.message).contains(name)
            verify(exactly = 1) { datasetRepositoryJpa.existsByName(name) }
            verify(exactly = 0) { datasetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw InvalidDatasetNameException for invalid name format")
        fun `should throw InvalidDatasetNameException for invalid name format`() {
            // Given
            val invalidDataset =
                DatasetEntity(
                    name = "invalid-name-without-dots",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                )

            every { datasetRepositoryJpa.existsByName(invalidDataset.name) } returns false

            // When & Then
            val exception =
                assertThrows<InvalidDatasetNameException> {
                    datasetService.registerDataset(invalidDataset)
                }

            assertThat(exception.message).contains("invalid-name-without-dots")
            verify(exactly = 1) { datasetRepositoryJpa.existsByName(invalidDataset.name) }
            verify(exactly = 0) { datasetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw TooManyTagsException when tags exceed limit")
        fun `should throw TooManyTagsException when tags exceed limit`() {
            // Given
            val tooManyTags = (1..15).map { "tag$it" }.toSet() // 15 tags, limit is 10
            val invalidDataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                    tags = tooManyTags,
                )

            every { datasetRepositoryJpa.existsByName(invalidDataset.name) } returns false

            // When & Then
            val exception =
                assertThrows<TooManyTagsException> {
                    datasetService.registerDataset(invalidDataset)
                }

            assertThat(exception.message).contains("15")
            verify(exactly = 1) { datasetRepositoryJpa.existsByName(invalidDataset.name) }
            verify(exactly = 0) { datasetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw InvalidOwnerEmailException for invalid email format")
        fun `should throw InvalidOwnerEmailException for invalid email format`() {
            // Given
            val invalidDataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "not-an-email",
                    sql = "SELECT 1",
                )

            every { datasetRepositoryJpa.existsByName(invalidDataset.name) } returns false

            // When & Then
            val exception =
                assertThrows<InvalidOwnerEmailException> {
                    datasetService.registerDataset(invalidDataset)
                }

            assertThat(exception.message).contains("not-an-email")
            verify(exactly = 1) { datasetRepositoryJpa.existsByName(invalidDataset.name) }
            verify(exactly = 0) { datasetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw InvalidCronException for invalid cron expression")
        fun `should throw InvalidCronException for invalid cron expression`() {
            // Given
            val invalidDataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                    scheduleCron = "invalid cron expression",
                )

            every { datasetRepositoryJpa.existsByName(invalidDataset.name) } returns false

            // When & Then
            val exception =
                assertThrows<InvalidCronException> {
                    datasetService.registerDataset(invalidDataset)
                }

            assertThat(exception.message).contains("invalid cron expression")
            verify(exactly = 1) { datasetRepositoryJpa.existsByName(invalidDataset.name) }
            verify(exactly = 0) { datasetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should accept valid cron expressions")
        fun `should accept valid cron expressions`() {
            // Given
            val validDataset =
                DatasetEntity(
                    name = "catalog.schema.dataset",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                    scheduleCron = "0 9 * * *", // Valid cron: daily at 9 AM
                )

            every { datasetRepositoryJpa.existsByName(validDataset.name) } returns false
            every { datasetRepositoryJpa.save(any()) } returns validDataset

            // When
            val result = datasetService.registerDataset(validDataset)

            // Then
            assertThat(result.scheduleCron).isEqualTo("0 9 * * *")
            verify(exactly = 1) { datasetRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("getDataset")
    inner class GetDataset {
        @Test
        @DisplayName("should return dataset when found by name")
        fun `should return dataset when found by name`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            every { datasetRepositoryJpa.findByName(name) } returns testDataset

            // When
            val result = datasetService.getDataset(name)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.name).isEqualTo(name)
            assertThat(result?.owner).isEqualTo("test@example.com")
            verify(exactly = 1) { datasetRepositoryJpa.findByName(name) }
        }

        @Test
        @DisplayName("should return null when dataset not found")
        fun `should return null when dataset not found`() {
            // Given
            val name = "nonexistent_catalog.schema.dataset"
            every { datasetRepositoryJpa.findByName(name) } returns null

            // When
            val result = datasetService.getDataset(name)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { datasetRepositoryJpa.findByName(name) }
        }
    }

    @Nested
    @DisplayName("getDatasetOrThrow")
    inner class GetDatasetOrThrow {
        @Test
        @DisplayName("should return dataset when found")
        fun `should return dataset when found`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            every { datasetRepositoryJpa.findByName(name) } returns testDataset

            // When
            val result = datasetService.getDatasetOrThrow(name)

            // Then
            assertThat(result.name).isEqualTo(name)
        }

        @Test
        @DisplayName("should throw DatasetNotFoundException when not found")
        fun `should throw DatasetNotFoundException when not found`() {
            // Given
            val name = "nonexistent_catalog.schema.dataset"
            every { datasetRepositoryJpa.findByName(name) } returns null

            // When & Then
            val exception =
                assertThrows<DatasetNotFoundException> {
                    datasetService.getDatasetOrThrow(name)
                }

            assertThat(exception.message).contains(name)
        }
    }

    @Nested
    @DisplayName("listDatasets")
    inner class ListDatasets {
        @Test
        @DisplayName("should return all datasets without filters")
        fun `should return all datasets without filters`() {
            // Given
            val datasets = listOf(testDataset)
            val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetRepositoryDsl.findByFilters(null, null, null, pageable) } returns PageImpl(datasets)

            // When
            val result = datasetService.listDatasets(pageable = pageable)

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].name).isEqualTo(testDataset.name)
        }

        @Test
        @DisplayName("should return filtered datasets by tag")
        fun `should return filtered datasets by tag`() {
            // Given
            val tag = "test"
            val datasets = listOf(testDataset)
            val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetRepositoryDsl.findByFilters(tag, null, null, pageable) } returns PageImpl(datasets)

            // When
            val result = datasetService.listDatasets(tag = tag, pageable = pageable)

            // Then
            assertThat(result.content).hasSize(1)
            verify(exactly = 1) { datasetRepositoryDsl.findByFilters(tag, null, null, pageable) }
        }

        @Test
        @DisplayName("should return filtered datasets by owner")
        fun `should return filtered datasets by owner`() {
            // Given
            val owner = "test@example.com"
            val datasets = listOf(testDataset)
            val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetRepositoryDsl.findByFilters(null, owner, null, pageable) } returns PageImpl(datasets)

            // When
            val result = datasetService.listDatasets(owner = owner, pageable = pageable)

            // Then
            assertThat(result.content).hasSize(1)
            verify(exactly = 1) { datasetRepositoryDsl.findByFilters(null, owner, null, pageable) }
        }

        @Test
        @DisplayName("should return filtered datasets by search term")
        fun `should return filtered datasets by search term`() {
            // Given
            val search = "user"
            val datasets = listOf(testDataset)
            val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetRepositoryDsl.findByFilters(null, null, search, pageable) } returns PageImpl(datasets)

            // When
            val result = datasetService.listDatasets(search = search, pageable = pageable)

            // Then
            assertThat(result.content).hasSize(1)
            verify(exactly = 1) { datasetRepositoryDsl.findByFilters(null, null, search, pageable) }
        }

        @Test
        @DisplayName("should return empty list when no datasets match filters")
        fun `should return empty list when no datasets match filters`() {
            // Given
            val pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetRepositoryDsl.findByFilters("nonexistent", null, null, pageable) } returns
                PageImpl(emptyList())

            // When
            val result = datasetService.listDatasets(tag = "nonexistent", pageable = pageable)

            // Then
            assertThat(result.content).isEmpty()
        }

        @Test
        @DisplayName("should combine multiple filters")
        fun `should combine multiple filters`() {
            // Given
            val tag = "test"
            val owner = "test@example.com"
            val search = "user"
            val datasets = listOf(testDataset)
            val pageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "updatedAt"))
            every { datasetRepositoryDsl.findByFilters(tag, owner, search, pageable) } returns PageImpl(datasets)

            // When
            val result = datasetService.listDatasets(tag = tag, owner = owner, search = search, pageable = pageable)

            // Then
            assertThat(result.content).hasSize(1)
            verify(exactly = 1) { datasetRepositoryDsl.findByFilters(tag, owner, search, pageable) }
        }
    }

    @Nested
    @DisplayName("updateDataset")
    inner class UpdateDataset {
        @Test
        @DisplayName("should update dataset successfully")
        fun `should update dataset successfully`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            val updatedDataset =
                DatasetEntity(
                    name = testDataset.name,
                    owner = testDataset.owner,
                    team = testDataset.team,
                    description = "Updated description",
                    sql = "SELECT * FROM updated_table",
                    tags = setOf("updated", "new"),
                    dependencies = testDataset.dependencies,
                    scheduleCron = testDataset.scheduleCron,
                    scheduleTimezone = testDataset.scheduleTimezone,
                )

            every { datasetRepositoryJpa.findByName(name) } returns testDataset
            every { datasetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = datasetService.updateDataset(name, updatedDataset)

            // Then
            assertThat(result.name).isEqualTo(name) // Name should not change
            assertThat(result.description).isEqualTo("Updated description")
            assertThat(result.sql).isEqualTo("SELECT * FROM updated_table")
            assertThat(result.tags).containsExactlyInAnyOrder("updated", "new")
            verify(exactly = 1) { datasetRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw DatasetNotFoundException when updating non-existent dataset")
        fun `should throw DatasetNotFoundException when updating non-existent dataset`() {
            // Given
            val name = "nonexistent_catalog.schema.dataset"
            every { datasetRepositoryJpa.findByName(name) } returns null

            // When & Then
            assertThrows<DatasetNotFoundException> {
                datasetService.updateDataset(name, testDataset)
            }
        }

        @Test
        @DisplayName("should preserve creation timestamp during update")
        fun `should preserve creation timestamp during update`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            val originalCreatedAt = LocalDateTime.of(2024, 1, 1, 9, 0, 0)
            val existingDataset =
                DatasetEntity(
                    name = testDataset.name,
                    owner = testDataset.owner,
                    team = testDataset.team,
                    description = testDataset.description,
                    sql = testDataset.sql,
                    tags = testDataset.tags,
                    dependencies = testDataset.dependencies,
                    scheduleCron = testDataset.scheduleCron,
                    scheduleTimezone = testDataset.scheduleTimezone,
                    createdAt = originalCreatedAt,
                )
            val updatedDataset =
                DatasetEntity(
                    name = testDataset.name,
                    owner = testDataset.owner,
                    team = testDataset.team,
                    description = "Updated",
                    sql = testDataset.sql,
                    tags = testDataset.tags,
                    dependencies = testDataset.dependencies,
                    scheduleCron = testDataset.scheduleCron,
                    scheduleTimezone = testDataset.scheduleTimezone,
                )

            every { datasetRepositoryJpa.findByName(name) } returns existingDataset
            every { datasetRepositoryJpa.save(any()) } answers { firstArg() }

            // When
            val result = datasetService.updateDataset(name, updatedDataset)

            // Then
            assertThat(result.createdAt).isEqualTo(originalCreatedAt)
            assertThat(result.description).isEqualTo("Updated")
        }

        @Test
        @DisplayName("should validate updated dataset")
        fun `should validate updated dataset`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            val invalidUpdatedDataset =
                DatasetEntity(
                    name = testDataset.name,
                    owner = "invalid-email", // Invalid email format
                    team = testDataset.team,
                    description = testDataset.description,
                    sql = testDataset.sql,
                    tags = testDataset.tags,
                    dependencies = testDataset.dependencies,
                    scheduleCron = testDataset.scheduleCron,
                    scheduleTimezone = testDataset.scheduleTimezone,
                )

            every { datasetRepositoryJpa.findByName(name) } returns testDataset

            // When & Then
            assertThrows<InvalidOwnerEmailException> {
                datasetService.updateDataset(name, invalidUpdatedDataset)
            }
        }
    }

    @Nested
    @DisplayName("deleteDataset")
    inner class DeleteDataset {
        @Test
        @DisplayName("should delete dataset successfully")
        fun `should delete dataset successfully`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            every { datasetRepositoryJpa.existsByName(name) } returns true
            every { datasetRepositoryJpa.deleteByName(name) } returns 1L

            // When
            datasetService.deleteDataset(name)

            // Then
            verify(exactly = 1) { datasetRepositoryJpa.existsByName(name) }
            verify(exactly = 1) { datasetRepositoryJpa.deleteByName(name) }
        }

        @Test
        @DisplayName("should throw DatasetNotFoundException when deleting non-existent dataset")
        fun `should throw DatasetNotFoundException when deleting non-existent dataset`() {
            // Given
            val name = "nonexistent_catalog.schema.dataset"
            every { datasetRepositoryJpa.existsByName(name) } returns false

            // When & Then
            assertThrows<DatasetNotFoundException> {
                datasetService.deleteDataset(name)
            }

            verify(exactly = 1) { datasetRepositoryJpa.existsByName(name) }
            verify(exactly = 0) { datasetRepositoryJpa.deleteByName(name) }
        }
    }

    @Nested
    @DisplayName("getDatasetsByOwner")
    inner class GetDatasetsByOwner {
        @Test
        @DisplayName("should return datasets by owner")
        fun `should return datasets by owner`() {
            // Given
            val owner = "test@example.com"
            val pageable = PageRequest.of(0, 10)
            val datasets = listOf(testDataset)
            every { datasetRepositoryJpa.findByOwnerOrderByUpdatedAtDesc(owner, pageable) } returns PageImpl(datasets)

            // When
            val result = datasetService.getDatasetsByOwner(owner, pageable)

            // Then
            assertThat(result.content).hasSize(1)
            assertThat(result.content[0].owner).isEqualTo(owner)
        }
    }

    @Nested
    @DisplayName("getDatasetsByTag")
    inner class GetDatasetsByTag {
        @Test
        @DisplayName("should return datasets by tag")
        fun `should return datasets by tag`() {
            // Given
            val tag = "test"
            val datasets = listOf(testDataset)
            every { datasetRepositoryJpa.findByTagsContaining(tag) } returns datasets

            // When
            val result = datasetService.getDatasetsByTag(tag)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].tags).contains(tag)
        }
    }

    @Nested
    @DisplayName("existsDataset")
    inner class ExistsDataset {
        @Test
        @DisplayName("should return true when dataset exists")
        fun `should return true when dataset exists`() {
            // Given
            val name = "test_catalog.test_schema.test_dataset"
            every { datasetRepositoryJpa.existsByName(name) } returns true

            // When
            val result = datasetService.existsDataset(name)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return false when dataset does not exist")
        fun `should return false when dataset does not exist`() {
            // Given
            val name = "nonexistent_catalog.schema.dataset"
            every { datasetRepositoryJpa.existsByName(name) } returns false

            // When
            val result = datasetService.existsDataset(name)

            // Then
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("getDatasetStatistics")
    inner class GetDatasetStatistics {
        @Test
        @DisplayName("should return dataset statistics")
        fun `should return dataset statistics`() {
            // Given
            val stats =
                mapOf(
                    "total_datasets" to 10,
                    "total_tags" to 5,
                    "avg_dependencies_per_dataset" to 2.5,
                )
            every { datasetRepositoryDsl.getDatasetStatistics(null) } returns stats

            // When
            val result = datasetService.getDatasetStatistics()

            // Then
            assertThat(result["total_datasets"]).isEqualTo(10)
            assertThat(result["total_tags"]).isEqualTo(5)
            assertThat(result["avg_dependencies_per_dataset"]).isEqualTo(2.5)
        }

        @Test
        @DisplayName("should return statistics filtered by owner")
        fun `should return statistics filtered by owner`() {
            // Given
            val owner = "test@example.com"
            val stats =
                mapOf(
                    "total_datasets" to 3,
                    "total_tags" to 2,
                )
            every { datasetRepositoryDsl.getDatasetStatistics(owner) } returns stats

            // When
            val result = datasetService.getDatasetStatistics(owner)

            // Then
            assertThat(result["total_datasets"]).isEqualTo(3)
            verify(exactly = 1) { datasetRepositoryDsl.getDatasetStatistics(owner) }
        }
    }

    @Nested
    @DisplayName("getRecentlyUpdatedDatasets")
    inner class GetRecentlyUpdatedDatasets {
        @Test
        @DisplayName("should return recently updated datasets")
        fun `should return recently updated datasets`() {
            // Given
            val limit = 5
            val daysSince = 7
            val datasets = listOf(testDataset)
            every { datasetRepositoryDsl.findRecentlyUpdatedDatasets(limit, daysSince) } returns datasets

            // When
            val result = datasetService.getRecentlyUpdatedDatasets(limit, daysSince)

            // Then
            assertThat(result).hasSize(1)
            verify(exactly = 1) { datasetRepositoryDsl.findRecentlyUpdatedDatasets(limit, daysSince) }
        }
    }

    @Nested
    @DisplayName("getDatasetsByDependency")
    inner class GetDatasetsByDependency {
        @Test
        @DisplayName("should return datasets that depend on given dataset")
        fun `should return datasets that depend on given dataset`() {
            // Given
            val dependency = "users"
            val datasets = listOf(testDataset)
            every { datasetRepositoryDsl.findDatasetsByDependency(dependency) } returns datasets

            // When
            val result = datasetService.getDatasetsByDependency(dependency)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].dependencies).contains(dependency)
        }
    }

    @Nested
    @DisplayName("getScheduledDatasets")
    inner class GetScheduledDatasets {
        @Test
        @DisplayName("should return all scheduled datasets")
        fun `should return all scheduled datasets`() {
            // Given
            val datasets = listOf(testDataset)
            every { datasetRepositoryDsl.findScheduledDatasets(null) } returns datasets

            // When
            val result = datasetService.getScheduledDatasets()

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].hasSchedule()).isTrue()
        }

        @Test
        @DisplayName("should return scheduled datasets with specific cron pattern")
        fun `should return scheduled datasets with specific cron pattern`() {
            // Given
            val cronPattern = "0 9 * * *"
            val datasets = listOf(testDataset)
            every { datasetRepositoryDsl.findScheduledDatasets(cronPattern) } returns datasets

            // When
            val result = datasetService.getScheduledDatasets(cronPattern)

            // Then
            assertThat(result).hasSize(1)
            assertThat(result[0].scheduleCron).isEqualTo("0 9 * * *")
        }
    }
}
