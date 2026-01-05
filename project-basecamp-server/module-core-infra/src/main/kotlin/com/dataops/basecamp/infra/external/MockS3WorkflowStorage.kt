package com.dataops.basecamp.infra.external

import com.dataops.basecamp.common.enums.WorkflowSourceType
import com.dataops.basecamp.common.exception.WorkflowStorageException
import com.dataops.basecamp.domain.external.storage.WorkflowStorage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Repository
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Mock S3 Workflow Storage Implementation (File System Based)
 *
 * 개발 환경에서 S3 없이도 워크플로우 YAML 파일 저장/관리를 위한
 * 로컬 파일 시스템 기반 저장소 구현.
 *
 * - 실제 S3와 동일한 경로 구조 유지
 * - 서버 재시작 후에도 데이터 유지
 * - 디렉토리 자동 생성
 *
 * 활성화 조건: basecamp.workflow.storage.type=mock (기본값)
 */
@Repository("workflowStorage")
@ConditionalOnProperty(
    name = ["basecamp.workflow.storage.type"],
    havingValue = "mock",
    matchIfMissing = true,
)
class MockS3WorkflowStorage(
    @Value("\${basecamp.workflow.storage.mock-dir:./data/workflows}")
    private val mockDir: String,
) : WorkflowStorage {
    private val log = LoggerFactory.getLogger(MockS3WorkflowStorage::class.java)

    private val basePath: Path by lazy {
        val path = Paths.get(mockDir).toAbsolutePath()
        ensureDirectoryExists(path)
        log.info("MockS3WorkflowStorage initialized - base path: {}", path)
        path
    }

    companion object {
        private const val BUCKET_NAME = "workflow-bucket"
        private const val BASE_PATH = "workflows"
    }

    override fun saveWorkflowYaml(
        datasetName: String,
        sourceType: WorkflowSourceType,
        yamlContent: String,
    ): String {
        try {
            val s3Path = generateS3Path(datasetName, sourceType)
            val filePath = s3PathToFilePath(s3Path)

            ensureDirectoryExists(filePath.parent)
            filePath.writeText(
                yamlContent,
                Charsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )

            log.info(
                "Saved workflow YAML - s3Path: {}, localPath: {}, size: {} bytes",
                s3Path,
                filePath,
                yamlContent.length,
            )
            return s3Path
        } catch (e: IOException) {
            log.error("Failed to save workflow YAML for dataset: {}, sourceType: {}", datasetName, sourceType, e)
            throw WorkflowStorageException("saveWorkflowYaml", e)
        } catch (e: Exception) {
            log.error("Failed to save workflow YAML for dataset: {}, sourceType: {}", datasetName, sourceType, e)
            throw WorkflowStorageException("saveWorkflowYaml", e)
        }
    }

    override fun getWorkflowYaml(s3Path: String): String {
        try {
            val filePath = s3PathToFilePath(s3Path)

            if (!filePath.exists()) {
                throw WorkflowStorageException("getWorkflowYaml", RuntimeException("File not found: $s3Path"))
            }

            val content = filePath.readText(Charsets.UTF_8)
            log.info(
                "Retrieved workflow YAML - s3Path: {}, localPath: {}, size: {} bytes",
                s3Path,
                filePath,
                content.length,
            )
            return content
        } catch (e: WorkflowStorageException) {
            throw e
        } catch (e: IOException) {
            log.error("Failed to get workflow YAML from path: {}", s3Path, e)
            throw WorkflowStorageException("getWorkflowYaml", e)
        } catch (e: Exception) {
            log.error("Failed to get workflow YAML from path: {}", s3Path, e)
            throw WorkflowStorageException("getWorkflowYaml", e)
        }
    }

    override fun deleteWorkflowYaml(s3Path: String): Boolean {
        try {
            val filePath = s3PathToFilePath(s3Path)

            if (!filePath.exists()) {
                log.info("File does not exist for deletion - s3Path: {}", s3Path)
                return false
            }

            Files.delete(filePath)
            log.info("Deleted workflow YAML - s3Path: {}, localPath: {}", s3Path, filePath)
            return true
        } catch (e: IOException) {
            log.error("Failed to delete workflow YAML from path: {}", s3Path, e)
            throw WorkflowStorageException("deleteWorkflowYaml", e)
        } catch (e: Exception) {
            log.error("Failed to delete workflow YAML from path: {}", s3Path, e)
            throw WorkflowStorageException("deleteWorkflowYaml", e)
        }
    }

    override fun existsWorkflowYaml(s3Path: String): Boolean {
        try {
            val filePath = s3PathToFilePath(s3Path)
            val exists = filePath.exists()
            log.debug("Checked workflow YAML existence - s3Path: {}, exists: {}", s3Path, exists)
            return exists
        } catch (e: Exception) {
            log.error("Failed to check workflow YAML existence for path: {}", s3Path, e)
            throw WorkflowStorageException("existsWorkflowYaml", e)
        }
    }

    override fun listWorkflowYamls(sourceType: WorkflowSourceType): List<String> {
        try {
            val prefix = generatePathPrefix(sourceType)
            val dirPath = basePath.resolve(prefix)

            if (!dirPath.exists() || !dirPath.isDirectory()) {
                log.info("Directory does not exist for sourceType: {} - path: {}", sourceType, dirPath)
                return emptyList()
            }

            val matchingPaths =
                Files
                    .walk(dirPath)
                    .filter { Files.isRegularFile(it) }
                    .filter { it.name.endsWith(".yaml") || it.name.endsWith(".yml") }
                    .map { filePathToS3Path(it) }
                    .sorted()
                    .toList()

            log.info("Listed workflow YAMLs - sourceType: {}, count: {}", sourceType, matchingPaths.size)
            return matchingPaths
        } catch (e: IOException) {
            log.error("Failed to list workflow YAMLs for sourceType: {}", sourceType, e)
            throw WorkflowStorageException("listWorkflowYamls", e)
        } catch (e: Exception) {
            log.error("Failed to list workflow YAMLs for sourceType: {}", sourceType, e)
            throw WorkflowStorageException("listWorkflowYamls", e)
        }
    }

    override fun updateWorkflowYaml(
        s3Path: String,
        yamlContent: String,
    ): String {
        try {
            val filePath = s3PathToFilePath(s3Path)

            if (!filePath.exists()) {
                throw WorkflowStorageException("updateWorkflowYaml", RuntimeException("File not found: $s3Path"))
            }

            filePath.writeText(yamlContent, Charsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)
            log.info(
                "Updated workflow YAML - s3Path: {}, localPath: {}, size: {} bytes",
                s3Path,
                filePath,
                yamlContent.length,
            )
            return s3Path
        } catch (e: WorkflowStorageException) {
            throw e
        } catch (e: IOException) {
            log.error("Failed to update workflow YAML at path: {}", s3Path, e)
            throw WorkflowStorageException("updateWorkflowYaml", e)
        } catch (e: Exception) {
            log.error("Failed to update workflow YAML at path: {}", s3Path, e)
            throw WorkflowStorageException("updateWorkflowYaml", e)
        }
    }

    override fun listAllSpecs(): List<String> {
        try {
            if (!basePath.exists() || !basePath.isDirectory()) {
                log.info("Base directory does not exist - path: {}", basePath)
                return emptyList()
            }

            val allPaths =
                Files
                    .walk(basePath)
                    .filter { Files.isRegularFile(it) }
                    .filter { it.name.endsWith(".yaml") || it.name.endsWith(".yml") }
                    .map { filePathToS3Path(it) }
                    .sorted()
                    .toList()

            log.info("Listed all specs - count: {}", allPaths.size)
            return allPaths
        } catch (e: IOException) {
            log.error("Failed to list all specs", e)
            throw WorkflowStorageException("listAllSpecs", e)
        } catch (e: Exception) {
            log.error("Failed to list all specs", e)
            throw WorkflowStorageException("listAllSpecs", e)
        }
    }

    override fun listSpecsByPrefix(prefix: String): List<String> {
        try {
            val dirPath = basePath.resolve(prefix.removePrefix("/"))

            if (!dirPath.exists()) {
                log.info("Directory does not exist for prefix: {} - path: {}", prefix, dirPath)
                return emptyList()
            }

            // If it's a file, return it if it matches YAML extension
            if (Files.isRegularFile(dirPath)) {
                return if (dirPath.name.endsWith(".yaml") || dirPath.name.endsWith(".yml")) {
                    listOf(filePathToS3Path(dirPath))
                } else {
                    emptyList()
                }
            }

            val matchingPaths =
                Files
                    .walk(dirPath)
                    .filter { Files.isRegularFile(it) }
                    .filter { it.name.endsWith(".yaml") || it.name.endsWith(".yml") }
                    .map { filePathToS3Path(it) }
                    .sorted()
                    .toList()

            log.info("Listed specs by prefix - prefix: {}, count: {}", prefix, matchingPaths.size)
            return matchingPaths
        } catch (e: IOException) {
            log.error("Failed to list specs by prefix: {}", prefix, e)
            throw WorkflowStorageException("listSpecsByPrefix", e)
        } catch (e: Exception) {
            log.error("Failed to list specs by prefix: {}", prefix, e)
            throw WorkflowStorageException("listSpecsByPrefix", e)
        }
    }

    // === Helper Methods ===

    /**
     * S3 경로 생성 (실제 S3와 동일한 경로 구조)
     */
    private fun generateS3Path(
        datasetName: String,
        sourceType: WorkflowSourceType,
    ): String {
        val prefix = getSourceTypePrefix(sourceType)
        val key = "$BASE_PATH/$prefix/$datasetName.yaml"
        return "s3://$BUCKET_NAME/$key"
    }

    /**
     * 소스 타입별 경로 prefix 생성
     */
    private fun generatePathPrefix(sourceType: WorkflowSourceType): String {
        val prefix = getSourceTypePrefix(sourceType)
        return "$BASE_PATH/$prefix"
    }

    /**
     * 소스 타입에 따른 디렉토리 prefix 반환
     */
    private fun getSourceTypePrefix(sourceType: WorkflowSourceType): String =
        when (sourceType) {
            WorkflowSourceType.MANUAL -> "manual"
            WorkflowSourceType.CODE -> "code"
        }

    /**
     * S3 경로를 로컬 파일 경로로 변환
     * s3://workflow-bucket/workflows/manual/dataset.yaml -> ./data/workflows/workflows/manual/dataset.yaml
     */
    private fun s3PathToFilePath(s3Path: String): Path {
        val key =
            s3Path
                .removePrefix("s3://")
                .substringAfter("/") // Remove bucket name
        return basePath.resolve(key)
    }

    /**
     * 로컬 파일 경로를 S3 경로로 변환
     */
    private fun filePathToS3Path(filePath: Path): String {
        val relativePath = basePath.relativize(filePath)
        return "s3://$BUCKET_NAME/$relativePath"
    }

    /**
     * 디렉토리가 존재하지 않으면 생성
     */
    private fun ensureDirectoryExists(path: Path) {
        if (!path.exists()) {
            Files.createDirectories(path)
            log.debug("Created directory: {}", path)
        }
    }

    // === Development Helper Methods ===

    /**
     * 개발용: 저장된 모든 파일 목록 조회
     */
    fun getAllStoredFiles(): Map<String, Long> {
        if (!basePath.exists()) {
            return emptyMap()
        }

        return Files
            .walk(basePath)
            .filter { Files.isRegularFile(it) }
            .filter { it.name.endsWith(".yaml") || it.name.endsWith(".yml") }
            .toList()
            .associate { filePathToS3Path(it) to Files.size(it) }
    }

    /**
     * 개발용: 저장소 내용 전체 삭제
     */
    fun clearAll() {
        if (!basePath.exists()) {
            log.info("Base directory does not exist, nothing to clear")
            return
        }

        var count = 0
        Files
            .walk(basePath)
            .filter { Files.isRegularFile(it) }
            .forEach {
                Files.delete(it)
                count++
            }
        log.info("Cleared all workflow storage - removed {} files", count)
    }
}
