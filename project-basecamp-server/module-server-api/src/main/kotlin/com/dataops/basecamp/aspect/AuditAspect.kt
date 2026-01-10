package com.dataops.basecamp.aspect

import com.dataops.basecamp.annotation.AuditExcludeKeys
import com.dataops.basecamp.common.enums.AuditAction
import com.dataops.basecamp.common.enums.AuditResource
import com.dataops.basecamp.common.exception.AccessDeniedException
import com.dataops.basecamp.common.exception.DatasetNotFoundException
import com.dataops.basecamp.common.exception.FlagTargetNotFoundException
import com.dataops.basecamp.common.exception.MetricNotFoundException
import com.dataops.basecamp.common.exception.ProjectNotFoundException
import com.dataops.basecamp.common.exception.QualitySpecNotFoundException
import com.dataops.basecamp.common.exception.QueryExecutionTimeoutException
import com.dataops.basecamp.common.exception.ResourceNotFoundException
import com.dataops.basecamp.common.exception.WorkflowNotFoundException
import com.dataops.basecamp.domain.service.AuditService
import com.dataops.basecamp.filter.TraceIdFilter
import com.dataops.basecamp.util.SecurityContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import mu.KotlinLogging
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.MDC
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.AuthenticationException
import org.springframework.stereotype.Component
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Audit Aspect
 *
 * Automatically records audit logs for all REST controller methods.
 * Excludes methods and classes annotated with @NoAudit.
 */
@Aspect
@Component
class AuditAspect(
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        // Global keys to always exclude from request body (case-insensitive matching)
        private val GLOBAL_EXCLUDE_KEYS =
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

        // Pre-computed lowercase version for efficient case-insensitive matching
        private val GLOBAL_EXCLUDE_KEYS_LOWERCASE = GLOBAL_EXCLUDE_KEYS.map { it.lowercase() }.toSet()

        // Pre-compiled regex pattern for dli CLI User-Agent parsing
        // Format: dli/0.9.0 (darwin; Python/3.12.1) command/workflow-backfill
        private val DLI_USER_AGENT_PATTERN =
            Regex("""dli/(\d+\.\d+\.\d+)\s*\(([^;]+);\s*Python/([^)]+)\)\s*command/(.+)""")
    }

    /**
     * Around advice for all @RestController methods
     * Excludes methods and classes with @NoAudit annotation
     */
    @Around(
        """
        @within(org.springframework.web.bind.annotation.RestController) &&
        !@annotation(com.dataops.basecamp.annotation.NoAudit) &&
        !@within(com.dataops.basecamp.annotation.NoAudit)
        """,
    )
    fun audit(joinPoint: ProceedingJoinPoint): Any? {
        val startTime = System.currentTimeMillis()
        val request = getCurrentHttpRequest()

        var responseStatus = 200
        var responseMessage: String? = null
        var result: Any? = null

        try {
            result = joinPoint.proceed()

            // Extract status code from ResponseEntity
            if (result is ResponseEntity<*>) {
                responseStatus = result.statusCode.value()
            }

            return result
        } catch (e: Exception) {
            responseStatus = extractStatusFromException(e)
            responseMessage = e.message?.take(500)
            throw e
        } finally {
            val duration = System.currentTimeMillis() - startTime

            try {
                saveAuditLog(
                    request = request,
                    joinPoint = joinPoint,
                    responseStatus = responseStatus,
                    responseMessage = responseMessage,
                    durationMs = duration,
                )
            } catch (e: Exception) {
                logger.error(e) { "Failed to save audit log" }
            }
        }
    }

    private fun saveAuditLog(
        request: HttpServletRequest?,
        joinPoint: ProceedingJoinPoint,
        responseStatus: Int,
        responseMessage: String?,
        durationMs: Long,
    ) {
        val (action, resource) = resolveActionAndResource(joinPoint)

        // Use existing SecurityContext object + MDC for Trace ID
        val userId =
            try {
                SecurityContext.getCurrentUserIdOrThrow().toString()
            } catch (e: Exception) {
                "anonymous"
            }

        auditService.saveLog(
            userId = userId,
            userEmail = SecurityContext.getCurrentUsername(),
            traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY),
            action = action,
            resource = resource,
            httpMethod = request?.method ?: "UNKNOWN",
            requestUrl = request?.requestURL?.toString() ?: "",
            pathVariables = extractPathVariables(joinPoint),
            queryParameters = extractQueryParameters(request),
            requestBody = extractRequestBody(joinPoint),
            responseStatus = responseStatus,
            responseMessage = responseMessage,
            durationMs = durationMs,
            clientType = extractClientType(request),
            clientIp = extractClientIp(request),
            userAgent = request?.getHeader("User-Agent"),
            clientMetadata = extractClientMetadata(request),
            resourceId = extractResourceId(joinPoint),
            teamId = extractTeamId(joinPoint),
        )
    }

    private fun resolveActionAndResource(joinPoint: ProceedingJoinPoint): Pair<AuditAction, AuditResource> {
        val methodName = joinPoint.signature.name
        val className = joinPoint.target.javaClass.simpleName

        // Infer Resource from Controller name
        val resource =
            when {
                className.contains("Metric") -> AuditResource.METRIC
                className.contains("Dataset") -> AuditResource.DATASET
                className.contains("Workflow") -> AuditResource.WORKFLOW
                className.contains("Quality") -> AuditResource.QUALITY
                className.contains("Query") -> AuditResource.QUERY
                className.contains("Catalog") -> AuditResource.CATALOG
                className.contains(
                    "Sql",
                ) &&
                    methodName.contains("Snippet", ignoreCase = true) -> AuditResource.SQL_SNIPPET
                className.contains(
                    "Sql",
                ) &&
                    methodName.contains("Folder", ignoreCase = true) -> AuditResource.SQL_FOLDER
                className.contains("Sql") -> AuditResource.SQL_SNIPPET
                className.contains("Team") -> AuditResource.TEAM
                className.contains(
                    "Flag",
                ) &&
                    methodName.contains("Override", ignoreCase = true) -> AuditResource.FLAG_OVERRIDE
                className.contains(
                    "Flag",
                ) &&
                    methodName.contains("Permission", ignoreCase = true) -> AuditResource.FLAG_PERMISSION
                className.contains("Flag") -> AuditResource.FLAG
                className.contains("GitHub") ->
                    when {
                        methodName.contains("Branch", ignoreCase = true) -> AuditResource.GITHUB_BRANCH
                        methodName.contains("PullRequest", ignoreCase = true) -> AuditResource.GITHUB_PULL_REQUEST
                        else -> AuditResource.GITHUB_REPOSITORY
                    }
                className.contains("Lineage") -> AuditResource.LINEAGE
                className.contains("Transpile") -> AuditResource.TRANSPILE_RULE
                className.contains("Run") -> AuditResource.RUN
                className.contains("Execution") -> AuditResource.EXECUTION
                className.contains("Resource") -> AuditResource.RESOURCE_LOCK
                className.contains("AirflowSync") -> AuditResource.AIRFLOW_SYNC
                className.contains("Session") -> AuditResource.SESSION
                className.contains("Audit") -> AuditResource.SYSTEM
                else -> AuditResource.SYSTEM
            }

        // Infer Action from method name (specific patterns first)
        val action =
            when {
                // Exact matches first
                methodName == "login" -> AuditAction.LOGIN
                methodName == "logout" -> AuditAction.LOGOUT

                // Specific patterns (order matters)
                methodName.startsWith("unpause") -> AuditAction.UNPAUSE
                methodName.startsWith("pause") -> AuditAction.PAUSE
                methodName.startsWith("unregister") -> AuditAction.DELETE

                // CRUD patterns
                methodName.startsWith("list") -> AuditAction.LIST
                methodName.startsWith("get") || methodName.startsWith("find") -> AuditAction.READ
                methodName.startsWith("create") || methodName.startsWith("register") -> AuditAction.CREATE
                methodName.startsWith("update") -> AuditAction.UPDATE
                methodName.startsWith("delete") || methodName.startsWith("remove") -> AuditAction.DELETE

                // Execution patterns
                methodName.contains("execute") || methodName.contains("run") -> AuditAction.EXECUTE
                methodName.contains("cancel") -> AuditAction.CANCEL
                methodName.contains("stop") -> AuditAction.STOP
                methodName.contains("download") -> AuditAction.DOWNLOAD
                methodName.contains("trigger") -> AuditAction.TRIGGER
                methodName.contains("backfill") -> AuditAction.BACKFILL

                // Resource patterns
                methodName.contains("lock") -> AuditAction.LOCK
                methodName.contains("release") -> AuditAction.RELEASE
                methodName.contains("search") -> AuditAction.SEARCH
                methodName.contains("lineage") -> AuditAction.LINEAGE
                methodName.contains("transpile") -> AuditAction.TRANSPILE
                methodName.contains("sync") -> AuditAction.SYNC
                methodName.contains("compare") -> AuditAction.COMPARE

                // Flag management
                methodName.contains(
                    "Override",
                ) &&
                    methodName.contains("set", ignoreCase = true) -> AuditAction.OVERRIDE_SET
                methodName.contains(
                    "Override",
                ) &&
                    methodName.contains("remove", ignoreCase = true) -> AuditAction.OVERRIDE_REMOVE
                methodName.contains(
                    "Permission",
                ) &&
                    methodName.contains("set", ignoreCase = true) -> AuditAction.PERMISSION_SET
                methodName.contains(
                    "Permission",
                ) &&
                    methodName.contains("remove", ignoreCase = true) -> AuditAction.PERMISSION_REMOVE
                methodName.contains("evaluate") -> AuditAction.EVALUATE

                else -> AuditAction.READ // Default
            }

        return action to resource
    }

    private fun extractPathVariables(joinPoint: ProceedingJoinPoint): String? {
        val method = (joinPoint.signature as MethodSignature).method
        val parameters = method.parameters
        val args = joinPoint.args

        val pathVars = mutableMapOf<String, Any?>()
        parameters.forEachIndexed { index, param ->
            param.getAnnotation(PathVariable::class.java)?.let { annotation ->
                val name = annotation.value.ifEmpty { param.name }
                pathVars[name] = args[index]
            }
        }

        return if (pathVars.isNotEmpty()) {
            try {
                objectMapper.writeValueAsString(pathVars)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    private fun extractQueryParameters(request: HttpServletRequest?): String? {
        val queryParams =
            request
                ?.parameterMap
                ?.mapValues { it.value.toList() }
                ?.filterNot { it.value.isEmpty() }

        return if (!queryParams.isNullOrEmpty()) {
            try {
                objectMapper.writeValueAsString(queryParams)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * Extracts and filters the request body from @RequestBody parameter.
     * Filters out sensitive keys (global + endpoint-specific via @AuditExcludeKeys).
     */
    private fun extractRequestBody(joinPoint: ProceedingJoinPoint): String? {
        val method = (joinPoint.signature as MethodSignature).method

        // Get endpoint-specific exclude keys from @AuditExcludeKeys annotation
        val excludeAnnotation = method.getAnnotation(AuditExcludeKeys::class.java)
        val endpointExcludeKeys = excludeAnnotation?.keys?.toSet() ?: emptySet()
        val allExcludeKeys = GLOBAL_EXCLUDE_KEYS + endpointExcludeKeys

        // Extract @RequestBody argument
        val requestBodyArg = extractRequestBodyArg(joinPoint)
        if (requestBodyArg != null) {
            return filterAndSerialize(requestBodyArg, allExcludeKeys)
        }

        return null
    }

    /**
     * Extracts the @RequestBody annotated argument from the join point.
     */
    private fun extractRequestBodyArg(joinPoint: ProceedingJoinPoint): Any? {
        val method = (joinPoint.signature as MethodSignature).method
        val parameters = method.parameters
        val args = joinPoint.args

        parameters.forEachIndexed { index, param ->
            if (param.getAnnotation(RequestBody::class.java) != null) {
                return args[index]
            }
        }
        return null
    }

    /**
     * Filters sensitive keys and serializes the body to JSON.
     */
    private fun filterAndSerialize(
        body: Any?,
        excludeKeys: Set<String>,
    ): String? {
        if (body == null) return null

        return try {
            // Convert to JSON tree for filtering
            val jsonNode = objectMapper.valueToTree<JsonNode>(body)
            if (jsonNode.isObject) {
                // Pre-compute lowercase keys once for efficient case-insensitive matching
                val lowercaseExcludeKeys = excludeKeys.map { it.lowercase() }.toSet()
                val filtered = filterJsonObject(jsonNode as ObjectNode, lowercaseExcludeKeys)
                objectMapper.writeValueAsString(filtered)
            } else {
                objectMapper.writeValueAsString(jsonNode)
            }
        } catch (e: Exception) {
            logger.debug { "Failed to serialize request body: ${e.message}" }
            null
        }
    }

    /**
     * Recursively filters sensitive keys from a JSON object.
     * Keys are matched case-insensitively.
     *
     * @param node The JSON object to filter
     * @param lowercaseExcludeKeys Pre-computed lowercase keys for efficient matching (computed once in filterAndSerialize)
     */
    private fun filterJsonObject(
        node: ObjectNode,
        lowercaseExcludeKeys: Set<String>,
    ): ObjectNode {
        val result = objectMapper.createObjectNode()

        node.fields().forEach { (key, value) ->
            if (key.lowercase() in lowercaseExcludeKeys) {
                result.put(key, "[FILTERED]")
            } else if (value.isObject) {
                // Recursively filter nested objects (pass pre-computed keys)
                result.set<JsonNode>(key, filterJsonObject(value as ObjectNode, lowercaseExcludeKeys))
            } else if (value.isArray) {
                // Filter objects within arrays
                val arrayNode = objectMapper.createArrayNode()
                value.forEach { element ->
                    if (element.isObject) {
                        arrayNode.add(filterJsonObject(element as ObjectNode, lowercaseExcludeKeys))
                    } else {
                        arrayNode.add(element)
                    }
                }
                result.set<JsonNode>(key, arrayNode)
            } else {
                result.set<JsonNode>(key, value)
            }
        }
        return result
    }

    private fun extractClientType(request: HttpServletRequest?): String? =
        request?.getHeader("X-Client-Type")
            ?: request?.getHeader("User-Agent")?.let { ua ->
                when {
                    ua.contains("dli/") -> "CLI"
                    ua.contains("Mozilla") || ua.contains("Chrome") -> "WEB"
                    else -> "API"
                }
            }

    private fun extractClientIp(request: HttpServletRequest?): String? =
        request
            ?.getHeader("X-Forwarded-For")
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?: request?.getHeader("X-Real-IP")
            ?: request?.remoteAddr

    /**
     * Parse User-Agent to extract client metadata
     *
     * User-Agent format: dli/0.9.0 (darwin; Python/3.12.1) command/workflow-backfill
     */
    private fun extractClientMetadata(request: HttpServletRequest?): String? {
        val userAgent = request?.getHeader("User-Agent") ?: return null

        // Parse dli CLI User-Agent using pre-compiled pattern
        val match = DLI_USER_AGENT_PATTERN.find(userAgent)

        return if (match != null) {
            try {
                val (version, os, pythonVersion, command) = match.destructured
                val metadata =
                    mapOf(
                        "name" to "dli",
                        "version" to version,
                        "os" to os,
                        "python" to pythonVersion,
                        "command" to command.replace("-", " "),
                    )
                objectMapper.writeValueAsString(metadata)
            } catch (e: Exception) {
                null
            }
        } else {
            // Web UI or other clients - store basic info only
            try {
                val metadata = mapOf("raw_user_agent" to userAgent.take(200))
                objectMapper.writeValueAsString(metadata)
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun extractResourceId(joinPoint: ProceedingJoinPoint): String? {
        val method = (joinPoint.signature as MethodSignature).method
        val parameters = method.parameters
        val args = joinPoint.args

        parameters.forEachIndexed { index, param ->
            param.getAnnotation(PathVariable::class.java)?.let { annotation ->
                val name = annotation.value.ifEmpty { param.name }
                if (name in listOf("name", "id", "key", "snippetId", "folderId", "worksheetId")) {
                    return args[index]?.toString()
                }
            }
        }
        return null
    }

    private fun extractTeamId(joinPoint: ProceedingJoinPoint): Long? {
        val method = (joinPoint.signature as MethodSignature).method
        val parameters = method.parameters
        val args = joinPoint.args

        parameters.forEachIndexed { index, param ->
            param.getAnnotation(PathVariable::class.java)?.let { annotation ->
                val name = annotation.value.ifEmpty { param.name }
                if (name == "teamId") {
                    return (args[index] as? Number)?.toLong()
                }
            }
        }
        return null
    }

    /**
     * Maps exceptions to HTTP status codes
     * Synchronized with GlobalExceptionHandler
     */
    private fun extractStatusFromException(e: Exception): Int =
        when (e) {
            // 400 Bad Request
            is IllegalArgumentException -> 400
            is IllegalStateException -> 400
            is MethodArgumentNotValidException -> 400
            is ConstraintViolationException -> 400
            is DataIntegrityViolationException -> 400

            // 401 Unauthorized
            is AuthenticationException -> 401

            // 403 Forbidden
            is AccessDeniedException -> 403

            // 404 Not Found
            is ResourceNotFoundException -> 404
            is MetricNotFoundException -> 404
            is DatasetNotFoundException -> 404
            is WorkflowNotFoundException -> 404
            is QualitySpecNotFoundException -> 404
            is ProjectNotFoundException -> 404
            is FlagTargetNotFoundException -> 404

            // 408 Request Timeout
            is QueryExecutionTimeoutException -> 408

            // 500 Internal Server Error
            else -> 500
        }

    private fun getCurrentHttpRequest(): HttpServletRequest? =
        try {
            (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        } catch (e: Exception) {
            null
        }
}
