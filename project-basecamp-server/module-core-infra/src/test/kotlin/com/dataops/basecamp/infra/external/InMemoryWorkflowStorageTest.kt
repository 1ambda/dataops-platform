package com.dataops.basecamp.infra.external

import com.dataops.basecamp.common.enums.WorkflowSourceType
import com.dataops.basecamp.common.exception.WorkflowStorageException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

@DisplayName("InMemoryWorkflowStorage 테스트")
class InMemoryWorkflowStorageTest {
    private lateinit var storage: InMemoryWorkflowStorage

    @BeforeEach
    fun setUp() {
        storage = InMemoryWorkflowStorage()
        storage.clearAll()
    }

    @Test
    @DisplayName("워크플로우 YAML 저장 - MANUAL 소스타입")
    fun `saveWorkflowYaml should save YAML content for MANUAL source type`() {
        // given
        val datasetName = "test_dataset"
        val sourceType = WorkflowSourceType.MANUAL
        val yamlContent =
            """
            |name: test_workflow
            |schedule: "0 0 * * *"
            |tasks:
            |  - name: extract_data
            |    type: sql
            """.trimMargin()

        // when
        val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, yamlContent)

        // then
        assertThat(s3Path).isEqualTo("s3://workflow-bucket/workflows/manual/test_dataset.yaml")
        assertThat(storage.existsWorkflowYaml(s3Path)).isTrue()
        assertThat(storage.getWorkflowYaml(s3Path)).isEqualTo(yamlContent)
    }

    @Test
    @DisplayName("워크플로우 YAML 저장 - CODE 소스타입")
    fun `saveWorkflowYaml should save YAML content for CODE source type`() {
        // given
        val datasetName = "code_dataset"
        val sourceType = WorkflowSourceType.CODE
        val yamlContent =
            """
            |name: code_workflow
            |schedule: "0 1 * * *"
            |tasks:
            |  - name: run_code
            |    type: python
            """.trimMargin()

        // when
        val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, yamlContent)

        // then
        assertThat(s3Path).isEqualTo("s3://workflow-bucket/workflows/code/code_dataset.yaml")
        assertThat(storage.existsWorkflowYaml(s3Path)).isTrue()
        assertThat(storage.getWorkflowYaml(s3Path)).isEqualTo(yamlContent)
    }

    @Test
    @DisplayName("워크플로우 YAML 조회 - 존재하는 파일")
    fun `getWorkflowYaml should return content for existing file`() {
        // given
        val datasetName = "existing_dataset"
        val sourceType = WorkflowSourceType.MANUAL
        val yamlContent = "name: existing_workflow"
        val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, yamlContent)

        // when
        val retrievedContent = storage.getWorkflowYaml(s3Path)

        // then
        assertThat(retrievedContent).isEqualTo(yamlContent)
    }

    @Test
    @DisplayName("워크플로우 YAML 조회 - 존재하지 않는 파일")
    fun `getWorkflowYaml should throw exception for non-existing file`() {
        // given
        val nonExistingPath = "s3://workflow-bucket/workflows/manual/non_existing.yaml"

        // when & then
        assertThatThrownBy {
            storage.getWorkflowYaml(nonExistingPath)
        }.isInstanceOf(WorkflowStorageException::class.java)
            .hasMessageContaining("getWorkflowYaml")
    }

    @Test
    @DisplayName("워크플로우 YAML 삭제 - 존재하는 파일")
    fun `deleteWorkflowYaml should delete existing file and return true`() {
        // given
        val datasetName = "to_delete"
        val sourceType = WorkflowSourceType.MANUAL
        val yamlContent = "name: to_delete_workflow"
        val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, yamlContent)

        // when
        val result = storage.deleteWorkflowYaml(s3Path)

        // then
        assertThat(result).isTrue()
        assertThat(storage.existsWorkflowYaml(s3Path)).isFalse()
    }

    @Test
    @DisplayName("워크플로우 YAML 삭제 - 존재하지 않는 파일")
    fun `deleteWorkflowYaml should return false for non-existing file`() {
        // given
        val nonExistingPath = "s3://workflow-bucket/workflows/manual/non_existing.yaml"

        // when
        val result = storage.deleteWorkflowYaml(nonExistingPath)

        // then
        assertThat(result).isFalse()
    }

    @Test
    @DisplayName("워크플로우 YAML 존재 확인")
    fun `existsWorkflowYaml should check file existence correctly`() {
        // given
        val datasetName = "existence_test"
        val sourceType = WorkflowSourceType.CODE
        val yamlContent = "name: existence_test_workflow"
        val nonExistingPath = "s3://workflow-bucket/workflows/code/non_existing.yaml"

        // when
        val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, yamlContent)

        // then
        assertThat(storage.existsWorkflowYaml(s3Path)).isTrue()
        assertThat(storage.existsWorkflowYaml(nonExistingPath)).isFalse()
    }

    @Test
    @DisplayName("워크플로우 YAML 목록 조회 - MANUAL 소스타입")
    fun `listWorkflowYamls should return MANUAL source type files`() {
        // given
        storage.saveWorkflowYaml("manual_dataset1", WorkflowSourceType.MANUAL, "content1")
        storage.saveWorkflowYaml("manual_dataset2", WorkflowSourceType.MANUAL, "content2")
        storage.saveWorkflowYaml("code_dataset1", WorkflowSourceType.CODE, "content3")

        // when
        val manualFiles = storage.listWorkflowYamls(WorkflowSourceType.MANUAL)
        val codeFiles = storage.listWorkflowYamls(WorkflowSourceType.CODE)

        // then
        assertThat(manualFiles).hasSize(2)
        assertThat(manualFiles).allMatch { it.contains("workflows/manual/") }
        assertThat(codeFiles).hasSize(1)
        assertThat(codeFiles).allMatch { it.contains("workflows/code/") }
    }

    @Test
    @DisplayName("워크플로우 YAML 목록 조회 - 빈 결과")
    fun `listWorkflowYamls should return empty list when no files exist`() {
        // when
        val manualFiles = storage.listWorkflowYamls(WorkflowSourceType.MANUAL)
        val codeFiles = storage.listWorkflowYamls(WorkflowSourceType.CODE)

        // then
        assertThat(manualFiles).isEmpty()
        assertThat(codeFiles).isEmpty()
    }

    @Test
    @DisplayName("워크플로우 YAML 수정 - 존재하는 파일")
    fun `updateWorkflowYaml should update existing file content`() {
        // given
        val datasetName = "update_test"
        val sourceType = WorkflowSourceType.MANUAL
        val originalContent = "name: original_workflow"
        val updatedContent = "name: updated_workflow"
        val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, originalContent)

        // when
        val resultPath = storage.updateWorkflowYaml(s3Path, updatedContent)

        // then
        assertThat(resultPath).isEqualTo(s3Path)
        assertThat(storage.getWorkflowYaml(s3Path)).isEqualTo(updatedContent)
    }

    @Test
    @DisplayName("워크플로우 YAML 수정 - 존재하지 않는 파일")
    fun `updateWorkflowYaml should throw exception for non-existing file`() {
        // given
        val nonExistingPath = "s3://workflow-bucket/workflows/manual/non_existing.yaml"
        val updatedContent = "name: updated_workflow"

        // when & then
        assertThatThrownBy {
            storage.updateWorkflowYaml(nonExistingPath, updatedContent)
        }.isInstanceOf(WorkflowStorageException::class.java)
            .hasMessageContaining("updateWorkflowYaml")
    }

    @Test
    @DisplayName("동시성 테스트 - 여러 스레드에서 동시 파일 저장")
    fun `concurrent saveWorkflowYaml operations should be thread-safe`() {
        // given
        val executor = Executors.newFixedThreadPool(10)
        val futures = mutableListOf<CompletableFuture<String>>()

        // when
        repeat(50) { i ->
            val future =
                CompletableFuture.supplyAsync(
                    {
                        storage.saveWorkflowYaml("dataset_$i", WorkflowSourceType.MANUAL, "content_$i")
                    },
                    executor,
                )
            futures.add(future)
        }

        CompletableFuture.allOf(*futures.toTypedArray()).join()
        executor.shutdown()

        // then
        val allFiles = storage.getAllStoredFiles()
        assertThat(allFiles).hasSize(50)

        futures.forEachIndexed { index, future ->
            val s3Path = future.get()
            val expectedContent = "content_$index"
            assertThat(storage.getWorkflowYaml(s3Path)).isEqualTo(expectedContent)
        }
    }

    @Test
    @DisplayName("동시성 테스트 - 읽기/쓰기 동시 작업")
    fun `concurrent read and write operations should be thread-safe`() {
        // given
        val datasetName = "concurrent_test"
        val sourceType = WorkflowSourceType.MANUAL
        val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, "initial_content")

        val executor = Executors.newFixedThreadPool(20)
        val readFutures = mutableListOf<CompletableFuture<String>>()
        val writeFutures = mutableListOf<CompletableFuture<String>>()

        // when
        repeat(10) { i ->
            val readFuture =
                CompletableFuture.supplyAsync(
                    {
                        storage.getWorkflowYaml(s3Path)
                    },
                    executor,
                )
            readFutures.add(readFuture)

            val writeFuture =
                CompletableFuture.supplyAsync(
                    {
                        storage.updateWorkflowYaml(s3Path, "updated_content_$i")
                        s3Path
                    },
                    executor,
                )
            writeFutures.add(writeFuture)
        }

        CompletableFuture
            .allOf(*(readFutures + writeFutures).toTypedArray())
            .join()
        executor.shutdown()

        // then
        // All operations should complete without exceptions
        readFutures.forEach { it.get() }
        writeFutures.forEach { it.get() }

        // Final content should be one of the updated contents
        val finalContent = storage.getWorkflowYaml(s3Path)
        assertThat(finalContent).matches("updated_content_\\d+")
    }

    @Test
    @DisplayName("개발 헬퍼 메서드 - getAllStoredFiles")
    fun `getAllStoredFiles should return file paths with sizes`() {
        // given
        storage.saveWorkflowYaml("test1", WorkflowSourceType.MANUAL, "short")
        storage.saveWorkflowYaml("test2", WorkflowSourceType.CODE, "longer_content")

        // when
        val allFiles = storage.getAllStoredFiles()

        // then
        assertThat(allFiles).hasSize(2)
        assertThat(allFiles.values).contains(5, 14) // lengths of "short" and "longer_content"
    }

    @Test
    @DisplayName("개발 헬퍼 메서드 - clearAll")
    fun `clearAll should remove all stored files`() {
        // given
        storage.saveWorkflowYaml("test1", WorkflowSourceType.MANUAL, "content1")
        storage.saveWorkflowYaml("test2", WorkflowSourceType.CODE, "content2")
        assertThat(storage.getAllStoredFiles()).hasSize(2)

        // when
        storage.clearAll()

        // then
        assertThat(storage.getAllStoredFiles()).isEmpty()
        assertThat(storage.listWorkflowYamls(WorkflowSourceType.MANUAL)).isEmpty()
        assertThat(storage.listWorkflowYamls(WorkflowSourceType.CODE)).isEmpty()
    }

    @Test
    @DisplayName("S3 경로 생성 규칙 검증")
    fun `s3Path generation should follow correct format`() {
        // given
        val manualDataset = "manual_dataset"
        val codeDataset = "code_dataset"

        // when
        val manualPath = storage.saveWorkflowYaml(manualDataset, WorkflowSourceType.MANUAL, "content")
        val codePath = storage.saveWorkflowYaml(codeDataset, WorkflowSourceType.CODE, "content")

        // then
        assertThat(manualPath).isEqualTo("s3://workflow-bucket/workflows/manual/manual_dataset.yaml")
        assertThat(codePath).isEqualTo("s3://workflow-bucket/workflows/code/code_dataset.yaml")
    }

    @Test
    @DisplayName("빈 컨텐츠 저장 처리")
    fun `saveWorkflowYaml should handle empty content`() {
        // given
        val datasetName = "empty_test"
        val sourceType = WorkflowSourceType.MANUAL
        val emptyContent = ""

        // when
        val s3Path = storage.saveWorkflowYaml(datasetName, sourceType, emptyContent)

        // then
        assertThat(storage.getWorkflowYaml(s3Path)).isEqualTo(emptyContent)
        assertThat(storage.existsWorkflowYaml(s3Path)).isTrue()
    }
}
