package com.dataops.basecamp.aspect

import com.dataops.basecamp.domain.service.AuditService
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.reflect.Method

// Stub controller classes for testing resolveActionAndResource
// These are defined at package level because Kotlin doesn't allow
// class declarations inside inner classes
private class StubMetricController

private class StubDatasetController

private class StubWorkflowController

private class StubFlagController

private class StubGitHubController

private class StubSqlController

private class StubExecutionController

private class StubRunController

private class StubSessionController

private class StubQueryController

private class StubLineageController

private class StubTranspileController

private class StubResourceController

private class StubCatalogController

private class StubAirflowSyncController

private class StubTeamController

private class StubQualityController

private class StubUnknownController

/**
 * AuditAspect Unit Tests
 *
 * Tests for request body filtering functionality.
 * Uses reflection to test filterAndSerialize private method.
 */
@DisplayName("AuditAspect 테스트")
class AuditAspectTest {
    private val auditService: AuditService = mockk(relaxed = true)
    private val objectMapper: ObjectMapper = ObjectMapper()

    private lateinit var auditAspect: AuditAspect

    @BeforeEach
    fun setUp() {
        auditAspect = AuditAspect(auditService, objectMapper)
    }

    @Nested
    @DisplayName("filterAndSerialize 테스트 (via reflection)")
    inner class FilterAndSerializeTest {
        private val filterMethod: Method by lazy {
            AuditAspect::class.java
                .getDeclaredMethod(
                    "filterAndSerialize",
                    Any::class.java,
                    Set::class.java,
                ).apply { isAccessible = true }
        }

        private val globalExcludeKeys =
            setOf(
                "password",
                "token",
                "secret",
                "api_key",
                "apiKey",
                "secretKey",
                "accessToken",
                "refreshToken",
                "credential",
                "credentials",
                "privateKey",
                "private_key",
            )

        @Test
        @DisplayName("global exclude keys 필터링 - password, token, secret, api_key")
        fun `filterAndSerialize_filtersGlobalExcludeKeys`() {
            // Given
            val body =
                mapOf(
                    "name" to "test-metric",
                    "password" to "secret123",
                    "token" to "jwt-token",
                    "secret" to "my-secret",
                    "api_key" to "api-key-value",
                    "description" to "normal value",
                )

            // When
            val result = filterMethod.invoke(auditAspect, body, globalExcludeKeys) as String?

            // Then
            assertThat(result).isNotNull
            val parsed = objectMapper.readTree(result)
            assertThat(parsed.get("name").asText()).isEqualTo("test-metric")
            assertThat(parsed.get("description").asText()).isEqualTo("normal value")
            assertThat(parsed.get("password").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("token").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("secret").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("api_key").asText()).isEqualTo("[FILTERED]")
        }

        @Test
        @DisplayName("annotation exclude keys 필터링 - @AuditExcludeKeys 지정 키")
        fun `filterAndSerialize_filtersAnnotationExcludeKeys`() {
            // Given
            val body =
                mapOf(
                    "name" to "test-metric",
                    "rendered_sql" to "SELECT * FROM table",
                    "sqlTemplate" to "SELECT * FROM {{ table }}",
                    "description" to "normal value",
                )
            val annotationExcludeKeys = setOf("rendered_sql", "sqlTemplate")

            // When
            val result = filterMethod.invoke(auditAspect, body, annotationExcludeKeys) as String?

            // Then
            assertThat(result).isNotNull
            val parsed = objectMapper.readTree(result)
            assertThat(parsed.get("name").asText()).isEqualTo("test-metric")
            assertThat(parsed.get("description").asText()).isEqualTo("normal value")
            assertThat(parsed.get("rendered_sql").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("sqlTemplate").asText()).isEqualTo("[FILTERED]")
        }

        @Test
        @DisplayName("global + annotation exclude keys 조합 필터링")
        fun `filterAndSerialize_combinedExcludeKeys`() {
            // Given
            val body =
                mapOf(
                    "name" to "test-metric",
                    "password" to "secret123",
                    "rendered_sql" to "SELECT * FROM table",
                    "apiKey" to "api-key-value",
                    "customSensitive" to "sensitive-data",
                    "normalField" to "normal value",
                )
            val combinedKeys = globalExcludeKeys + setOf("rendered_sql", "customSensitive")

            // When
            val result = filterMethod.invoke(auditAspect, body, combinedKeys) as String?

            // Then
            assertThat(result).isNotNull
            val parsed = objectMapper.readTree(result)
            assertThat(parsed.get("name").asText()).isEqualTo("test-metric")
            assertThat(parsed.get("normalField").asText()).isEqualTo("normal value")
            assertThat(parsed.get("password").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("rendered_sql").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("apiKey").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("customSensitive").asText()).isEqualTo("[FILTERED]")
        }

        @Test
        @DisplayName("non-sensitive keys 보존")
        fun `filterAndSerialize_preservesNonSensitiveKeys`() {
            // Given
            val body =
                mapOf(
                    "name" to "test-metric",
                    "description" to "Test description",
                    "tags" to listOf("tag1", "tag2"),
                    "enabled" to true,
                    "count" to 42,
                )

            // When
            val result = filterMethod.invoke(auditAspect, body, globalExcludeKeys) as String?

            // Then
            assertThat(result).isNotNull
            val parsed = objectMapper.readTree(result)
            assertThat(parsed.get("name").asText()).isEqualTo("test-metric")
            assertThat(parsed.get("description").asText()).isEqualTo("Test description")
            assertThat(parsed.get("tags").isArray).isTrue()
            assertThat(parsed.get("tags").size()).isEqualTo(2)
            assertThat(parsed.get("enabled").asBoolean()).isTrue()
            assertThat(parsed.get("count").asInt()).isEqualTo(42)
        }

        @Test
        @DisplayName("null body 처리 - null 반환")
        fun `filterAndSerialize_handlesNullBody`() {
            // When
            val result = filterMethod.invoke(auditAspect, null, globalExcludeKeys) as String?

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("nested object 필터링")
        fun `filterAndSerialize_handlesNestedObjects`() {
            // Given - using LinkedHashMap to maintain JSON object structure
            val nestedConfig =
                linkedMapOf(
                    "host" to "localhost",
                    "password" to "nested-password",
                    "token" to "nested-token",
                )
            // Note: "authInfo" is used instead of "credentials" as "credentials" is in global exclude keys
            val nestedAuthInfo =
                linkedMapOf(
                    "username" to "admin",
                    "secret" to "deep-nested-secret",
                )
            val nestedConnection =
                linkedMapOf<String, Any>(
                    "url" to "jdbc://localhost",
                    "authInfo" to nestedAuthInfo,
                )
            val body =
                linkedMapOf<String, Any>(
                    "name" to "test-metric",
                    "config" to nestedConfig,
                    "connection" to nestedConnection,
                )

            // When
            val result = filterMethod.invoke(auditAspect, body, globalExcludeKeys) as String?

            // Then
            assertThat(result).isNotNull
            val parsed = objectMapper.readTree(result)
            assertThat(parsed.get("name").asText()).isEqualTo("test-metric")

            // First level nested
            val config = parsed.get("config")
            assertThat(config.get("host").asText()).isEqualTo("localhost")
            assertThat(config.get("password").asText()).isEqualTo("[FILTERED]")
            assertThat(config.get("token").asText()).isEqualTo("[FILTERED]")

            // Second level nested - authInfo is not in exclude keys, so it should be recursively filtered
            val connection = parsed.get("connection")
            assertThat(connection.get("url").asText()).isEqualTo("jdbc://localhost")
            // authInfo should be an object, and its nested "secret" key should be filtered
            assertThat(connection.get("authInfo").get("username").asText()).isEqualTo("admin")
            assertThat(connection.get("authInfo").get("secret").asText()).isEqualTo("[FILTERED]")
        }

        @Test
        @DisplayName("case-insensitive 필터링")
        fun `filterAndSerialize_caseInsensitiveFiltering`() {
            // Given
            val body =
                mapOf(
                    "name" to "test-metric",
                    "PASSWORD" to "uppercase-password",
                    "Token" to "mixed-case-token",
                    "SECRET" to "uppercase-secret",
                )

            // When
            val result = filterMethod.invoke(auditAspect, body, globalExcludeKeys) as String?

            // Then
            assertThat(result).isNotNull
            val parsed = objectMapper.readTree(result)
            assertThat(parsed.get("name").asText()).isEqualTo("test-metric")
            assertThat(parsed.get("PASSWORD").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("Token").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("SECRET").asText()).isEqualTo("[FILTERED]")
        }

        @Test
        @DisplayName("array 내 object 필터링")
        fun `filterAndSerialize_filtersObjectsInArrays`() {
            // Given
            val items =
                listOf(
                    linkedMapOf("id" to 1, "password" to "pass1"),
                    linkedMapOf("id" to 2, "token" to "token2"),
                    linkedMapOf("id" to 3, "secret" to "secret3"),
                )
            val body =
                linkedMapOf<String, Any>(
                    "name" to "test-metric",
                    "items" to items,
                )

            // When
            val result = filterMethod.invoke(auditAspect, body, globalExcludeKeys) as String?

            // Then
            assertThat(result).isNotNull
            val parsed = objectMapper.readTree(result)
            assertThat(parsed.get("name").asText()).isEqualTo("test-metric")

            val parsedItems = parsed.get("items")
            assertThat(parsedItems.isArray).isTrue()
            assertThat(parsedItems.get(0).get("id").asInt()).isEqualTo(1)
            assertThat(parsedItems.get(0).get("password").asText()).isEqualTo("[FILTERED]")
            assertThat(parsedItems.get(1).get("id").asInt()).isEqualTo(2)
            assertThat(parsedItems.get(1).get("token").asText()).isEqualTo("[FILTERED]")
            assertThat(parsedItems.get(2).get("id").asInt()).isEqualTo(3)
            assertThat(parsedItems.get(2).get("secret").asText()).isEqualTo("[FILTERED]")
        }

        @Test
        @DisplayName("empty object 처리")
        fun `filterAndSerialize_handlesEmptyObject`() {
            // Given
            val body = emptyMap<String, Any>()

            // When
            val result = filterMethod.invoke(auditAspect, body, globalExcludeKeys) as String?

            // Then
            assertThat(result).isNotNull
            val parsed = objectMapper.readTree(result)
            assertThat(parsed.isEmpty).isTrue()
        }

        @Test
        @DisplayName("camelCase variations 필터링")
        fun `filterAndSerialize_filtersCamelCaseVariations`() {
            // Given
            val body =
                mapOf(
                    "name" to "test-metric",
                    "apiKey" to "api-key-value",
                    "secretKey" to "secret-key-value",
                    "accessToken" to "access-token-value",
                    "refreshToken" to "refresh-token-value",
                    "privateKey" to "private-key-value",
                )

            // When
            val result = filterMethod.invoke(auditAspect, body, globalExcludeKeys) as String?

            // Then
            assertThat(result).isNotNull
            val parsed = objectMapper.readTree(result)
            assertThat(parsed.get("name").asText()).isEqualTo("test-metric")
            assertThat(parsed.get("apiKey").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("secretKey").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("accessToken").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("refreshToken").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("privateKey").asText()).isEqualTo("[FILTERED]")
        }

        @Test
        @DisplayName("snake_case variations 필터링")
        fun `filterAndSerialize_filtersSnakeCaseVariations`() {
            // Given
            val body =
                mapOf(
                    "name" to "test-metric",
                    "api_key" to "api-key-value",
                    "private_key" to "private-key-value",
                )

            // When
            val result = filterMethod.invoke(auditAspect, body, globalExcludeKeys) as String?

            // Then
            assertThat(result).isNotNull
            val parsed = objectMapper.readTree(result)
            assertThat(parsed.get("name").asText()).isEqualTo("test-metric")
            assertThat(parsed.get("api_key").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("private_key").asText()).isEqualTo("[FILTERED]")
        }

        @Test
        @DisplayName("credential 관련 키 필터링")
        fun `filterAndSerialize_filtersCredentialKeys`() {
            // Given
            val body =
                mapOf(
                    "name" to "test-config",
                    "credential" to "my-credential",
                    "credentials" to mapOf("user" to "admin", "pass" to "secret"),
                )

            // When
            val result = filterMethod.invoke(auditAspect, body, globalExcludeKeys) as String?

            // Then
            assertThat(result).isNotNull
            val parsed = objectMapper.readTree(result)
            assertThat(parsed.get("name").asText()).isEqualTo("test-config")
            assertThat(parsed.get("credential").asText()).isEqualTo("[FILTERED]")
            assertThat(parsed.get("credentials").asText()).isEqualTo("[FILTERED]")
        }
    }

    @Nested
    @DisplayName("Global Exclude Keys 확인")
    inner class GlobalExcludeKeysTest {
        @Test
        @DisplayName("GLOBAL_EXCLUDE_KEYS contains expected keys")
        fun `should have all expected global exclude keys`() {
            // The companion object in AuditAspect should contain these keys
            val expectedKeys =
                setOf(
                    "password",
                    "token",
                    "secret",
                    "api_key",
                    "apiKey",
                    "secretKey",
                    "accessToken",
                    "refreshToken",
                    "credential",
                    "credentials",
                    "privateKey",
                    "private_key",
                )

            // Use reflection to verify the constant exists
            val field = AuditAspect::class.java.getDeclaredField("GLOBAL_EXCLUDE_KEYS")
            field.isAccessible = true

            @Suppress("UNCHECKED_CAST")
            val actualKeys = field.get(null) as Set<String>

            assertThat(actualKeys).containsAll(expectedKeys)
        }
    }

    @Nested
    @DisplayName("resolveActionAndResource 테스트 (via reflection)")
    inner class ResolveActionAndResourceTest {
        private val resolveMethod: Method by lazy {
            AuditAspect::class.java
                .getDeclaredMethod(
                    "resolveActionAndResource",
                    org.aspectj.lang.ProceedingJoinPoint::class.java,
                ).apply { isAccessible = true }
        }

        private fun createMockJoinPoint(
            target: Any,
            methodName: String,
        ): org.aspectj.lang.ProceedingJoinPoint {
            val joinPoint = mockk<org.aspectj.lang.ProceedingJoinPoint>()
            val signature = mockk<org.aspectj.lang.Signature>()

            every { joinPoint.signature } returns signature
            every { signature.name } returns methodName
            every { joinPoint.target } returns target

            return joinPoint
        }

        @Test
        @DisplayName("MetricController - list method should return LIST action and METRIC resource")
        fun `resolveActionAndResource_metricController_list`() {
            // Given
            val joinPoint = createMockJoinPoint(StubMetricController(), "listMetrics")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.LIST)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.METRIC)
        }

        @Test
        @DisplayName("DatasetController - create method should return CREATE action and DATASET resource")
        fun `resolveActionAndResource_datasetController_create`() {
            // Given
            val joinPoint = createMockJoinPoint(StubDatasetController(), "createDataset")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.CREATE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.DATASET)
        }

        @Test
        @DisplayName("WorkflowController - get method should return READ action and WORKFLOW resource")
        fun `resolveActionAndResource_workflowController_get`() {
            // Given
            val joinPoint = createMockJoinPoint(StubWorkflowController(), "getWorkflow")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.READ)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.WORKFLOW)
        }

        @Test
        @DisplayName("WorkflowController - pause method should return PAUSE action")
        fun `resolveActionAndResource_workflowController_pause`() {
            // Given
            val joinPoint = createMockJoinPoint(StubWorkflowController(), "pauseWorkflow")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.PAUSE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.WORKFLOW)
        }

        @Test
        @DisplayName("WorkflowController - unpause method should return UNPAUSE action")
        fun `resolveActionAndResource_workflowController_unpause`() {
            // Given
            val joinPoint = createMockJoinPoint(StubWorkflowController(), "unpauseWorkflow")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.UNPAUSE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.WORKFLOW)
        }

        @Test
        @DisplayName("FlagController - setOverride method should return OVERRIDE_SET action and FLAG_OVERRIDE resource")
        fun `resolveActionAndResource_flagController_setOverride`() {
            // Given
            val joinPoint = createMockJoinPoint(StubFlagController(), "setOverride")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.OVERRIDE_SET)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.FLAG_OVERRIDE)
        }

        @Test
        @DisplayName("FlagController - setPermission -> PERMISSION_SET + FLAG_PERMISSION")
        fun `resolveActionAndResource_flagController_setPermission`() {
            // Given
            val joinPoint = createMockJoinPoint(StubFlagController(), "setPermission")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.PERMISSION_SET)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.FLAG_PERMISSION)
        }

        @Test
        @DisplayName("GitHubController - getBranch method should return READ action and GITHUB_BRANCH resource")
        fun `resolveActionAndResource_githubController_getBranch`() {
            // Given
            val joinPoint = createMockJoinPoint(StubGitHubController(), "getBranch")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.READ)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.GITHUB_BRANCH)
        }

        @Test
        @DisplayName("GitHubController - createPullRequest -> CREATE + GITHUB_PULL_REQUEST")
        fun `resolveActionAndResource_githubController_createPullRequest`() {
            // Given
            val joinPoint = createMockJoinPoint(StubGitHubController(), "createPullRequest")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.CREATE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.GITHUB_PULL_REQUEST)
        }

        @Test
        @DisplayName("SqlController - createSnippet method should return CREATE action and SQL_SNIPPET resource")
        fun `resolveActionAndResource_sqlController_createSnippet`() {
            // Given
            val joinPoint = createMockJoinPoint(StubSqlController(), "createSnippet")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.CREATE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.SQL_SNIPPET)
        }

        @Test
        @DisplayName("SqlController - createFolder method should return CREATE action and SQL_FOLDER resource")
        fun `resolveActionAndResource_sqlController_createFolder`() {
            // Given
            val joinPoint = createMockJoinPoint(StubSqlController(), "createFolder")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.CREATE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.SQL_FOLDER)
        }

        @Test
        @DisplayName("ExecutionController - execute method should return EXECUTE action and EXECUTION resource")
        fun `resolveActionAndResource_executionController_execute`() {
            // Given
            val joinPoint = createMockJoinPoint(StubExecutionController(), "executeQuery")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.EXECUTE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.EXECUTION)
        }

        @Test
        @DisplayName("RunController - triggerRun method should return TRIGGER action and RUN resource")
        fun `resolveActionAndResource_runController_trigger`() {
            // Given
            val joinPoint = createMockJoinPoint(StubRunController(), "triggerRun")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.TRIGGER)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.RUN)
        }

        @Test
        @DisplayName("WorkflowController - backfillWorkflow method should return BACKFILL action")
        fun `resolveActionAndResource_workflowController_backfill`() {
            // Given
            val joinPoint = createMockJoinPoint(StubWorkflowController(), "backfillWorkflow")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.BACKFILL)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.WORKFLOW)
        }

        @Test
        @DisplayName("UnknownController - unknown method should default to READ action and SYSTEM resource")
        fun `resolveActionAndResource_unknownController_defaultsToReadAndSystem`() {
            // Given
            val joinPoint = createMockJoinPoint(StubUnknownController(), "unknownMethod")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.READ)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.SYSTEM)
        }

        @Test
        @DisplayName("SessionController - login method should return LOGIN action")
        fun `resolveActionAndResource_sessionController_login`() {
            // Given
            val joinPoint = createMockJoinPoint(StubSessionController(), "login")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.LOGIN)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.SESSION)
        }

        @Test
        @DisplayName("SessionController - logout method should return LOGOUT action")
        fun `resolveActionAndResource_sessionController_logout`() {
            // Given
            val joinPoint = createMockJoinPoint(StubSessionController(), "logout")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.LOGOUT)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.SESSION)
        }

        @Test
        @DisplayName("WorkflowController - unregister method should return DELETE action")
        fun `resolveActionAndResource_workflowController_unregister`() {
            // Given
            val joinPoint = createMockJoinPoint(StubWorkflowController(), "unregisterWorkflow")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.DELETE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.WORKFLOW)
        }

        @Test
        @DisplayName("WorkflowController - register method should return CREATE action")
        fun `resolveActionAndResource_workflowController_register`() {
            // Given
            val joinPoint = createMockJoinPoint(StubWorkflowController(), "registerWorkflow")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.CREATE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.WORKFLOW)
        }

        @Test
        @DisplayName("MetricController - updateMetric method should return UPDATE action")
        fun `resolveActionAndResource_metricController_update`() {
            // Given
            val joinPoint = createMockJoinPoint(StubMetricController(), "updateMetric")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.UPDATE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.METRIC)
        }

        @Test
        @DisplayName("DatasetController - deleteDataset method should return DELETE action")
        fun `resolveActionAndResource_datasetController_delete`() {
            // Given
            val joinPoint = createMockJoinPoint(StubDatasetController(), "deleteDataset")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.DELETE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.DATASET)
        }

        @Test
        @DisplayName("QueryController - cancelQuery method should return CANCEL action and QUERY resource")
        fun `resolveActionAndResource_queryController_cancel`() {
            // Given
            val joinPoint = createMockJoinPoint(StubQueryController(), "cancelQuery")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.CANCEL)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.QUERY)
        }

        @Test
        @DisplayName("LineageController - tracelineage method should return LINEAGE action (case-sensitive match)")
        fun `resolveActionAndResource_lineageController_tracelineage`() {
            // Given - use "tracelineage" with lowercase "lineage" because:
            // 1. "getLineage" starts with "get" -> matches READ first
            // 2. Implementation uses case-sensitive contains("lineage")
            val joinPoint = createMockJoinPoint(StubLineageController(), "tracelineage")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.LINEAGE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.LINEAGE)
        }

        @Test
        @DisplayName("LineageController - getLineage method should return READ action (get* pattern takes precedence)")
        fun `resolveActionAndResource_lineageController_getLineage_returnsRead`() {
            // Given - "getLineage" starts with "get" which matches READ before lineage check
            val joinPoint = createMockJoinPoint(StubLineageController(), "getLineage")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then - READ because "get*" pattern matches before "lineage" check
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.READ)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.LINEAGE)
        }

        @Test
        @DisplayName("TranspileController - transpileRule method should return TRANSPILE action")
        fun `resolveActionAndResource_transpileController_transpile`() {
            // Given
            val joinPoint = createMockJoinPoint(StubTranspileController(), "transpileRule")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.TRANSPILE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.TRANSPILE_RULE)
        }

        @Test
        @DisplayName("ResourceController - lockResource method should return LOCK action and RESOURCE_LOCK resource")
        fun `resolveActionAndResource_resourceController_lock`() {
            // Given
            val joinPoint = createMockJoinPoint(StubResourceController(), "lockResource")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.LOCK)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.RESOURCE_LOCK)
        }

        @Test
        @DisplayName("ResourceController - releaseResource method should return RELEASE action")
        fun `resolveActionAndResource_resourceController_release`() {
            // Given
            val joinPoint = createMockJoinPoint(StubResourceController(), "releaseResource")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.RELEASE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.RESOURCE_LOCK)
        }

        @Test
        @DisplayName("CatalogController - searchTables method should return SEARCH action and CATALOG resource")
        fun `resolveActionAndResource_catalogController_search`() {
            // Given
            val joinPoint = createMockJoinPoint(StubCatalogController(), "searchTables")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.SEARCH)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.CATALOG)
        }

        @Test
        @DisplayName("AirflowSyncController - syncDags method should return SYNC action and AIRFLOW_SYNC resource")
        fun `resolveActionAndResource_airflowSyncController_sync`() {
            // Given
            val joinPoint = createMockJoinPoint(StubAirflowSyncController(), "syncDags")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.SYNC)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.AIRFLOW_SYNC)
        }

        @Test
        @DisplayName("FlagController - evaluate method should return EVALUATE action")
        fun `resolveActionAndResource_flagController_evaluate`() {
            // Given
            val joinPoint = createMockJoinPoint(StubFlagController(), "evaluateFlags")

            // When
            @Suppress("UNCHECKED_CAST")
            val result = resolveMethod.invoke(auditAspect, joinPoint) as Pair<*, *>

            // Then
            assertThat(result.first).isEqualTo(com.dataops.basecamp.common.enums.AuditAction.EVALUATE)
            assertThat(result.second).isEqualTo(com.dataops.basecamp.common.enums.AuditResource.FLAG)
        }
    }

    @Nested
    @DisplayName("extractClientType 테스트 (via reflection)")
    inner class ExtractClientTypeTest {
        private val extractMethod: Method by lazy {
            AuditAspect::class.java
                .getDeclaredMethod(
                    "extractClientType",
                    jakarta.servlet.http.HttpServletRequest::class.java,
                ).apply { isAccessible = true }
        }

        @Test
        @DisplayName("X-Client-Type header 우선 사용")
        fun `extractClientType_prefersXClientTypeHeader`() {
            // Given
            val request = mockk<jakarta.servlet.http.HttpServletRequest>()
            every { request.getHeader("X-Client-Type") } returns "CUSTOM_CLIENT"

            // When
            val result = extractMethod.invoke(auditAspect, request) as String?

            // Then
            assertThat(result).isEqualTo("CUSTOM_CLIENT")
        }

        @Test
        @DisplayName("CLI User-Agent 감지 (dli/)")
        fun `extractClientType_detectsCliUserAgent`() {
            // Given
            val request = mockk<jakarta.servlet.http.HttpServletRequest>()
            every { request.getHeader("X-Client-Type") } returns null
            every { request.getHeader("User-Agent") } returns "dli/0.9.1 Python/3.12"

            // When
            val result = extractMethod.invoke(auditAspect, request) as String?

            // Then
            assertThat(result).isEqualTo("CLI")
        }

        @Test
        @DisplayName("WEB User-Agent 감지 (Mozilla)")
        fun `extractClientType_detectsMozillaAsWeb`() {
            // Given
            val request = mockk<jakarta.servlet.http.HttpServletRequest>()
            every { request.getHeader("X-Client-Type") } returns null
            every { request.getHeader("User-Agent") } returns "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"

            // When
            val result = extractMethod.invoke(auditAspect, request) as String?

            // Then
            assertThat(result).isEqualTo("WEB")
        }

        @Test
        @DisplayName("WEB User-Agent 감지 (Chrome)")
        fun `extractClientType_detectsChromeAsWeb`() {
            // Given
            val request = mockk<jakarta.servlet.http.HttpServletRequest>()
            every { request.getHeader("X-Client-Type") } returns null
            every { request.getHeader("User-Agent") } returns "Chrome/120.0.0.0 Safari/537.36"

            // When
            val result = extractMethod.invoke(auditAspect, request) as String?

            // Then
            assertThat(result).isEqualTo("WEB")
        }

        @Test
        @DisplayName("API fallback (unknown User-Agent)")
        fun `extractClientType_fallbacksToApiForUnknownAgent`() {
            // Given
            val request = mockk<jakarta.servlet.http.HttpServletRequest>()
            every { request.getHeader("X-Client-Type") } returns null
            every { request.getHeader("User-Agent") } returns "curl/7.79.1"

            // When
            val result = extractMethod.invoke(auditAspect, request) as String?

            // Then
            assertThat(result).isEqualTo("API")
        }

        @Test
        @DisplayName("null request 처리")
        fun `extractClientType_handlesNullRequest`() {
            // When
            val result = extractMethod.invoke(auditAspect, null) as String?

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("null User-Agent 처리")
        fun `extractClientType_handlesNullUserAgent`() {
            // Given
            val request = mockk<jakarta.servlet.http.HttpServletRequest>()
            every { request.getHeader("X-Client-Type") } returns null
            every { request.getHeader("User-Agent") } returns null

            // When
            val result = extractMethod.invoke(auditAspect, request) as String?

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("extractClientIp 테스트 (via reflection)")
    inner class ExtractClientIpTest {
        private val extractMethod: Method by lazy {
            AuditAspect::class.java
                .getDeclaredMethod(
                    "extractClientIp",
                    jakarta.servlet.http.HttpServletRequest::class.java,
                ).apply { isAccessible = true }
        }

        @Test
        @DisplayName("X-Forwarded-For header 우선 사용")
        fun `extractClientIp_prefersXForwardedFor`() {
            // Given
            val request = mockk<jakarta.servlet.http.HttpServletRequest>()
            every { request.getHeader("X-Forwarded-For") } returns "203.0.113.195"
            every { request.getHeader("X-Real-IP") } returns "10.0.0.1"
            every { request.remoteAddr } returns "192.168.1.1"

            // When
            val result = extractMethod.invoke(auditAspect, request) as String?

            // Then
            assertThat(result).isEqualTo("203.0.113.195")
        }

        @Test
        @DisplayName("X-Forwarded-For 체인에서 첫 번째 IP 추출")
        fun `extractClientIp_extractsFirstIpFromForwardedChain`() {
            // Given
            val request = mockk<jakarta.servlet.http.HttpServletRequest>()
            every { request.getHeader("X-Forwarded-For") } returns "203.0.113.195, 70.41.3.18, 150.172.238.178"
            every { request.getHeader("X-Real-IP") } returns null
            every { request.remoteAddr } returns "192.168.1.1"

            // When
            val result = extractMethod.invoke(auditAspect, request) as String?

            // Then
            assertThat(result).isEqualTo("203.0.113.195")
        }

        @Test
        @DisplayName("X-Real-IP header fallback")
        fun `extractClientIp_fallbacksToXRealIp`() {
            // Given
            val request = mockk<jakarta.servlet.http.HttpServletRequest>()
            every { request.getHeader("X-Forwarded-For") } returns null
            every { request.getHeader("X-Real-IP") } returns "10.0.0.1"
            every { request.remoteAddr } returns "192.168.1.1"

            // When
            val result = extractMethod.invoke(auditAspect, request) as String?

            // Then
            assertThat(result).isEqualTo("10.0.0.1")
        }

        @Test
        @DisplayName("remoteAddr fallback")
        fun `extractClientIp_fallbacksToRemoteAddr`() {
            // Given
            val request = mockk<jakarta.servlet.http.HttpServletRequest>()
            every { request.getHeader("X-Forwarded-For") } returns null
            every { request.getHeader("X-Real-IP") } returns null
            every { request.remoteAddr } returns "192.168.1.1"

            // When
            val result = extractMethod.invoke(auditAspect, request) as String?

            // Then
            assertThat(result).isEqualTo("192.168.1.1")
        }

        @Test
        @DisplayName("null request 처리")
        fun `extractClientIp_handlesNullRequest`() {
            // When
            val result = extractMethod.invoke(auditAspect, null) as String?

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("X-Forwarded-For 공백 처리")
        fun `extractClientIp_trimsWhitespace`() {
            // Given
            val request = mockk<jakarta.servlet.http.HttpServletRequest>()
            every { request.getHeader("X-Forwarded-For") } returns "  203.0.113.195  , 70.41.3.18"
            every { request.getHeader("X-Real-IP") } returns null
            every { request.remoteAddr } returns "192.168.1.1"

            // When
            val result = extractMethod.invoke(auditAspect, request) as String?

            // Then
            assertThat(result).isEqualTo("203.0.113.195")
        }
    }
}
