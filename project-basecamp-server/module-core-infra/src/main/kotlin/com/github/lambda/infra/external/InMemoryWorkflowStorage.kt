package com.github.lambda.infra.external

import com.github.lambda.domain.external.WorkflowStorage
import com.github.lambda.domain.external.WorkflowStorageException
import com.github.lambda.domain.model.workflow.WorkflowSourceType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

/**
 * InMemory Workflow Storage Implementation
 *
 * 개발 환경에서 S3 없이도 워크플로우 YAML 파일 저장/관리를 위한
 * 메모리 기반 저장소 구현
 */
@Repository("workflowStorage")
class InMemoryWorkflowStorage : WorkflowStorage {
    private val log = LoggerFactory.getLogger(InMemoryWorkflowStorage::class.java)

    // Mock S3 storage: path -> content
    private val fileStorage = ConcurrentHashMap<String, String>()

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
            fileStorage[s3Path] = yamlContent
            log.info("Saved workflow YAML - path: {}, size: {} bytes", s3Path, yamlContent.length)
            return s3Path
        } catch (e: Exception) {
            log.error("Failed to save workflow YAML for dataset: {}, sourceType: {}", datasetName, sourceType, e)
            throw WorkflowStorageException("saveWorkflowYaml", e)
        }
    }

    override fun getWorkflowYaml(s3Path: String): String {
        try {
            val content =
                fileStorage[s3Path]
                    ?: throw WorkflowStorageException("getWorkflowYaml", RuntimeException("File not found: $s3Path"))

            log.info("Retrieved workflow YAML - path: {}, size: {} bytes", s3Path, content.length)
            return content
        } catch (e: WorkflowStorageException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to get workflow YAML from path: {}", s3Path, e)
            throw WorkflowStorageException("getWorkflowYaml", e)
        }
    }

    override fun deleteWorkflowYaml(s3Path: String): Boolean {
        try {
            val existed = fileStorage.remove(s3Path) != null
            log.info("Deleted workflow YAML - path: {}, existed: {}", s3Path, existed)
            return existed
        } catch (e: Exception) {
            log.error("Failed to delete workflow YAML from path: {}", s3Path, e)
            throw WorkflowStorageException("deleteWorkflowYaml", e)
        }
    }

    override fun existsWorkflowYaml(s3Path: String): Boolean {
        try {
            val exists = fileStorage.containsKey(s3Path)
            log.debug("Checked workflow YAML existence - path: {}, exists: {}", s3Path, exists)
            return exists
        } catch (e: Exception) {
            log.error("Failed to check workflow YAML existence for path: {}", s3Path, e)
            throw WorkflowStorageException("existsWorkflowYaml", e)
        }
    }

    override fun listWorkflowYamls(sourceType: WorkflowSourceType): List<String> {
        try {
            val prefix = generatePathPrefix(sourceType)
            val matchingPaths = fileStorage.keys.filter { it.contains(prefix) }.sorted()
            log.info("Listed workflow YAMLs - sourceType: {}, count: {}", sourceType, matchingPaths.size)
            return matchingPaths
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
            if (!fileStorage.containsKey(s3Path)) {
                throw WorkflowStorageException("updateWorkflowYaml", RuntimeException("File not found: $s3Path"))
            }

            fileStorage[s3Path] = yamlContent
            log.info("Updated workflow YAML - path: {}, size: {} bytes", s3Path, yamlContent.length)
            return s3Path
        } catch (e: WorkflowStorageException) {
            throw e
        } catch (e: Exception) {
            log.error("Failed to update workflow YAML at path: {}", s3Path, e)
            throw WorkflowStorageException("updateWorkflowYaml", e)
        }
    }

    // === Helper Methods ===

    /**
     * S3 경로 생성 (S3WorkflowStorage와 동일한 경로 구조)
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
        return "$BASE_PATH/$prefix/"
    }

    /**
     * 소스 타입에 따른 디렉토리 prefix 반환
     */
    private fun getSourceTypePrefix(sourceType: WorkflowSourceType): String =
        when (sourceType) {
            WorkflowSourceType.MANUAL -> "manual"
            WorkflowSourceType.CODE -> "code"
        }

    // === Development Helper Methods ===

    /**
     * 개발용: 저장된 모든 파일 목록 조회
     */
    fun getAllStoredFiles(): Map<String, Int> = fileStorage.mapValues { it.value.length }

    /**
     * 개발용: 저장소 내용 전체 삭제
     */
    fun clearAll() {
        val count = fileStorage.size
        fileStorage.clear()
        log.info("Cleared all workflow storage - removed {} files", count)
    }
}
