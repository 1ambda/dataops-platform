package com.github.lambda.domain.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.lambda.domain.model.workflow.WorkflowParseResult
import com.github.lambda.domain.model.workflow.WorkflowSpec
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Workflow YAML Parser
 *
 * YAML 형식의 워크플로우 명세를 파싱하여 WorkflowSpec 객체로 변환합니다.
 *
 * 지원하는 YAML 형식:
 * ```yaml
 * name: catalog.schema.dataset_name
 * owner: user@example.com
 * team: data-platform
 * description: Daily user activity aggregation
 * schedule:
 *   cron: "0 0 * * *"
 *   timezone: Asia/Seoul
 * metadata:
 *   tags:
 *     - production
 *     - user-data
 * ```
 */
@Service
class WorkflowYamlParser {
    private val log = LoggerFactory.getLogger(WorkflowYamlParser::class.java)

    private val yamlMapper: ObjectMapper by lazy {
        ObjectMapper(YAMLFactory())
            .registerModule(KotlinModule.Builder().build())
    }

    /**
     * YAML 문자열을 WorkflowSpec으로 파싱
     *
     * @param yamlContent YAML 형식의 워크플로우 명세
     * @return 파싱 결과 (성공 시 WorkflowSpec, 실패 시 에러 목록)
     */
    fun parse(yamlContent: String): WorkflowParseResult {
        if (yamlContent.isBlank()) {
            log.warn("Empty YAML content provided")
            return WorkflowParseResult.Failure(listOf("YAML content is empty"))
        }

        return try {
            val spec = yamlMapper.readValue<WorkflowSpec>(yamlContent)
            val validationErrors = spec.validate()

            if (validationErrors.isNotEmpty()) {
                log.warn("YAML validation failed - errors: {}", validationErrors)
                WorkflowParseResult.Failure(validationErrors)
            } else {
                log.debug("Successfully parsed workflow spec - name: {}", spec.name)
                WorkflowParseResult.Success(spec)
            }
        } catch (e: Exception) {
            log.error("Failed to parse YAML content", e)
            WorkflowParseResult.Failure(listOf("Failed to parse YAML: ${e.message}"))
        }
    }

    /**
     * YAML 문자열을 WorkflowSpec으로 파싱 (검증 없이)
     *
     * @param yamlContent YAML 형식의 워크플로우 명세
     * @return 파싱 결과 (성공 시 WorkflowSpec, 실패 시 에러 목록)
     */
    fun parseWithoutValidation(yamlContent: String): WorkflowParseResult {
        if (yamlContent.isBlank()) {
            log.warn("Empty YAML content provided")
            return WorkflowParseResult.Failure(listOf("YAML content is empty"))
        }

        return try {
            val spec = yamlMapper.readValue<WorkflowSpec>(yamlContent)
            log.debug("Successfully parsed workflow spec (without validation) - name: {}", spec.name)
            WorkflowParseResult.Success(spec)
        } catch (e: Exception) {
            log.error("Failed to parse YAML content", e)
            WorkflowParseResult.Failure(listOf("Failed to parse YAML: ${e.message}"))
        }
    }

    /**
     * WorkflowSpec을 YAML 문자열로 직렬화
     *
     * @param spec WorkflowSpec 객체
     * @return YAML 형식의 문자열
     */
    fun serialize(spec: WorkflowSpec): String =
        try {
            yamlMapper.writeValueAsString(spec)
        } catch (e: Exception) {
            log.error("Failed to serialize workflow spec", e)
            throw IllegalArgumentException("Failed to serialize workflow spec: ${e.message}", e)
        }

    /**
     * YAML 형식 검증 (파싱 가능 여부)
     *
     * @param yamlContent YAML 형식의 워크플로우 명세
     * @return 유효한 YAML 형식인지 여부
     */
    fun isValidYaml(yamlContent: String): Boolean {
        if (yamlContent.isBlank()) {
            return false
        }

        return try {
            yamlMapper.readValue<WorkflowSpec>(yamlContent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * YAML 문자열에서 특정 필드 추출 (name 필드)
     *
     * @param yamlContent YAML 형식의 워크플로우 명세
     * @return name 필드 값 (없으면 null)
     */
    fun extractName(yamlContent: String): String? =
        try {
            val spec = yamlMapper.readValue<WorkflowSpec>(yamlContent)
            spec.name.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            log.debug("Failed to extract name from YAML content", e)
            null
        }

    /**
     * YAML 문자열에서 특정 필드 추출 (owner 필드)
     *
     * @param yamlContent YAML 형식의 워크플로우 명세
     * @return owner 필드 값 (없으면 null)
     */
    fun extractOwner(yamlContent: String): String? =
        try {
            val spec = yamlMapper.readValue<WorkflowSpec>(yamlContent)
            spec.owner.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            log.debug("Failed to extract owner from YAML content", e)
            null
        }
}
