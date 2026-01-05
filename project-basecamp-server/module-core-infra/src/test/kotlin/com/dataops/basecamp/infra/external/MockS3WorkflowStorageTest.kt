package com.dataops.basecamp.infra.external

import com.dataops.basecamp.common.enums.WorkflowSourceType
import com.dataops.basecamp.common.exception.WorkflowStorageException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * MockS3WorkflowStorage Unit Tests
 *
 * Tests for file-system based mock S3 workflow storage.
 */
@DisplayName("MockS3WorkflowStorage Unit Tests")
class MockS3WorkflowStorageTest {
    @TempDir
    lateinit var tempDir: Path

    private lateinit var storage: MockS3WorkflowStorage

    @BeforeEach
    fun setUp() {
        storage = MockS3WorkflowStorage(tempDir.toString())
    }

    @AfterEach
    fun tearDown() {
        // Clean up stored files
        storage.clearAll()
    }

    @Nested
    @DisplayName("saveWorkflowYaml")
    inner class SaveWorkflowYaml {
        @Test
        @DisplayName("should save YAML file successfully")
        fun `should save YAML file successfully`() {
            // Given
            val datasetName = "catalog.schema.dataset"
            val sourceType = WorkflowSourceType.CODE
            val yamlContent =
                """
                name: catalog.schema.dataset
                owner: user@example.com
                """.trimIndent()

            // When
            val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, yamlContent)

            // Then
            assertThat(s3Path).isNotBlank()
            assertThat(s3Path).startsWith("s3://workflow-bucket/")
            assertThat(s3Path).contains("code")
            assertThat(s3Path).endsWith(".yaml")
        }

        @Test
        @DisplayName("should save YAML file for MANUAL source type")
        fun `should save YAML file for MANUAL source type`() {
            // Given
            val datasetName = "catalog.schema.manual_dataset"
            val sourceType = WorkflowSourceType.MANUAL
            val yamlContent = "name: test"

            // When
            val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, yamlContent)

            // Then
            assertThat(s3Path).contains("manual")
        }

        @Test
        @DisplayName("should overwrite existing file")
        fun `should overwrite existing file`() {
            // Given
            val datasetName = "catalog.schema.dataset"
            val sourceType = WorkflowSourceType.CODE
            val originalContent = "original: content"
            val newContent = "new: content"

            // When
            val s3Path1 = storage.saveWorkflowYaml(datasetName, sourceType, originalContent)
            val s3Path2 = storage.saveWorkflowYaml(datasetName, sourceType, newContent)
            val retrievedContent = storage.getWorkflowYaml(s3Path2)

            // Then
            assertThat(s3Path1).isEqualTo(s3Path2)
            assertThat(retrievedContent).isEqualTo(newContent)
        }
    }

    @Nested
    @DisplayName("getWorkflowYaml")
    inner class GetWorkflowYaml {
        @Test
        @DisplayName("should retrieve saved YAML content")
        fun `should retrieve saved YAML content`() {
            // Given
            val datasetName = "catalog.schema.dataset"
            val sourceType = WorkflowSourceType.CODE
            val yamlContent = "name: test\nowner: user@example.com"
            val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, yamlContent)

            // When
            val retrievedContent = storage.getWorkflowYaml(s3Path)

            // Then
            assertThat(retrievedContent).isEqualTo(yamlContent)
        }

        @Test
        @DisplayName("should throw exception for non-existent file")
        fun `should throw exception for non-existent file`() {
            // Given
            val nonExistentPath = "s3://workflow-bucket/workflows/code/non_existent.yaml"

            // When & Then
            val exception =
                assertThrows<WorkflowStorageException> {
                    storage.getWorkflowYaml(nonExistentPath)
                }

            assertThat(exception.message).contains("getWorkflowYaml")
        }
    }

    @Nested
    @DisplayName("deleteWorkflowYaml")
    inner class DeleteWorkflowYaml {
        @Test
        @DisplayName("should delete existing file")
        fun `should delete existing file`() {
            // Given
            val datasetName = "catalog.schema.dataset"
            val sourceType = WorkflowSourceType.CODE
            val yamlContent = "name: test"
            val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, yamlContent)

            // When
            val deleted = storage.deleteWorkflowYaml(s3Path)

            // Then
            assertThat(deleted).isTrue()
            assertThat(storage.existsWorkflowYaml(s3Path)).isFalse()
        }

        @Test
        @DisplayName("should return false for non-existent file")
        fun `should return false for non-existent file`() {
            // Given
            val nonExistentPath = "s3://workflow-bucket/workflows/code/non_existent.yaml"

            // When
            val deleted = storage.deleteWorkflowYaml(nonExistentPath)

            // Then
            assertThat(deleted).isFalse()
        }
    }

    @Nested
    @DisplayName("existsWorkflowYaml")
    inner class ExistsWorkflowYaml {
        @Test
        @DisplayName("should return true for existing file")
        fun `should return true for existing file`() {
            // Given
            val datasetName = "catalog.schema.dataset"
            val sourceType = WorkflowSourceType.CODE
            val yamlContent = "name: test"
            val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, yamlContent)

            // When
            val exists = storage.existsWorkflowYaml(s3Path)

            // Then
            assertThat(exists).isTrue()
        }

        @Test
        @DisplayName("should return false for non-existent file")
        fun `should return false for non-existent file`() {
            // Given
            val nonExistentPath = "s3://workflow-bucket/workflows/code/non_existent.yaml"

            // When
            val exists = storage.existsWorkflowYaml(nonExistentPath)

            // Then
            assertThat(exists).isFalse()
        }
    }

    @Nested
    @DisplayName("listAllSpecs")
    inner class ListAllSpecs {
        @Test
        @DisplayName("should list all saved specs")
        fun `should list all saved specs`() {
            // Given
            storage.saveWorkflowYaml("catalog.schema.dataset1", WorkflowSourceType.CODE, "name: test1")
            storage.saveWorkflowYaml("catalog.schema.dataset2", WorkflowSourceType.MANUAL, "name: test2")
            storage.saveWorkflowYaml("catalog.schema.dataset3", WorkflowSourceType.CODE, "name: test3")

            // When
            val allSpecs = storage.listAllSpecs()

            // Then
            assertThat(allSpecs).hasSize(3)
            assertThat(allSpecs.all { it.endsWith(".yaml") }).isTrue()
        }

        @Test
        @DisplayName("should return empty list when no specs exist")
        fun `should return empty list when no specs exist`() {
            // When
            val allSpecs = storage.listAllSpecs()

            // Then
            assertThat(allSpecs).isEmpty()
        }
    }

    @Nested
    @DisplayName("listSpecsByPrefix")
    inner class ListSpecsByPrefix {
        @Test
        @DisplayName("should list specs by prefix")
        fun `should list specs by prefix`() {
            // Given
            storage.saveWorkflowYaml("catalog.schema.dataset1", WorkflowSourceType.CODE, "name: test1")
            storage.saveWorkflowYaml("catalog.schema.dataset2", WorkflowSourceType.CODE, "name: test2")
            storage.saveWorkflowYaml("catalog.schema.dataset3", WorkflowSourceType.MANUAL, "name: test3")

            // When
            val codeSpecs = storage.listSpecsByPrefix("workflows/code")

            // Then
            assertThat(codeSpecs).hasSize(2)
            assertThat(codeSpecs.all { it.contains("code") }).isTrue()
        }

        @Test
        @DisplayName("should return empty list for non-existent prefix")
        fun `should return empty list for non-existent prefix`() {
            // Given
            storage.saveWorkflowYaml("catalog.schema.dataset", WorkflowSourceType.CODE, "name: test")

            // When
            val specs = storage.listSpecsByPrefix("workflows/nonexistent")

            // Then
            assertThat(specs).isEmpty()
        }
    }

    @Nested
    @DisplayName("listWorkflowYamls")
    inner class ListWorkflowYamls {
        @Test
        @DisplayName("should list YAML files by source type")
        fun `should list YAML files by source type`() {
            // Given
            storage.saveWorkflowYaml("catalog.schema.dataset1", WorkflowSourceType.CODE, "name: test1")
            storage.saveWorkflowYaml("catalog.schema.dataset2", WorkflowSourceType.CODE, "name: test2")
            storage.saveWorkflowYaml("catalog.schema.dataset3", WorkflowSourceType.MANUAL, "name: test3")

            // When
            val codeSpecs = storage.listWorkflowYamls(WorkflowSourceType.CODE)
            val manualSpecs = storage.listWorkflowYamls(WorkflowSourceType.MANUAL)

            // Then
            assertThat(codeSpecs).hasSize(2)
            assertThat(manualSpecs).hasSize(1)
        }

        @Test
        @DisplayName("should return empty list for source type with no files")
        fun `should return empty list for source type with no files`() {
            // When
            val specs = storage.listWorkflowYamls(WorkflowSourceType.CODE)

            // Then
            assertThat(specs).isEmpty()
        }
    }

    @Nested
    @DisplayName("updateWorkflowYaml")
    inner class UpdateWorkflowYaml {
        @Test
        @DisplayName("should update existing file")
        fun `should update existing file`() {
            // Given
            val datasetName = "catalog.schema.dataset"
            val sourceType = WorkflowSourceType.CODE
            val originalContent = "original: content"
            val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, originalContent)

            val newContent = "updated: content"

            // When
            val updatedPath = storage.updateWorkflowYaml(s3Path, newContent)
            val retrievedContent = storage.getWorkflowYaml(updatedPath)

            // Then
            assertThat(updatedPath).isEqualTo(s3Path)
            assertThat(retrievedContent).isEqualTo(newContent)
        }

        @Test
        @DisplayName("should throw exception for non-existent file")
        fun `should throw exception for non-existent file`() {
            // Given
            val nonExistentPath = "s3://workflow-bucket/workflows/code/non_existent.yaml"
            val newContent = "updated: content"

            // When & Then
            val exception =
                assertThrows<WorkflowStorageException> {
                    storage.updateWorkflowYaml(nonExistentPath, newContent)
                }

            assertThat(exception.message).contains("updateWorkflowYaml")
        }
    }

    @Nested
    @DisplayName("Development Helper Methods")
    inner class DevelopmentHelperMethods {
        @Test
        @DisplayName("should get all stored files with sizes")
        fun `should get all stored files with sizes`() {
            // Given
            storage.saveWorkflowYaml("catalog.schema.dataset1", WorkflowSourceType.CODE, "name: test1")
            storage.saveWorkflowYaml(
                "catalog.schema.dataset2",
                WorkflowSourceType.MANUAL,
                "name: test2 with more content",
            )

            // When
            val storedFiles = storage.getAllStoredFiles()

            // Then
            assertThat(storedFiles).hasSize(2)
            storedFiles.values.forEach { size ->
                assertThat(size).isGreaterThan(0L)
            }
        }

        @Test
        @DisplayName("should clear all stored files")
        fun `should clear all stored files`() {
            // Given
            storage.saveWorkflowYaml("catalog.schema.dataset1", WorkflowSourceType.CODE, "name: test1")
            storage.saveWorkflowYaml("catalog.schema.dataset2", WorkflowSourceType.MANUAL, "name: test2")
            assertThat(storage.listAllSpecs()).hasSize(2)

            // When
            storage.clearAll()

            // Then
            assertThat(storage.listAllSpecs()).isEmpty()
        }
    }
}
