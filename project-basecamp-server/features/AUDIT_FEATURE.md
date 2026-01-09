# Audit Feature Specification

> **Version**: 1.2.0
> **Status**: Design Review Complete (CLI Integration Support Added)
> **Last Updated**: 2026-01-09

## 1. Overview

### 1.1 목적

DataOps Platform의 모든 API 호출을 자동으로 기록하여 사용자 행동 패턴 분석, 이력 추적, 시스템 감사를 지원합니다.

### 1.2 핵심 원칙

| 원칙 | 설명 |
|------|------|
| **자동 기록** | 모든 API 호출을 기본적으로 기록, `@NoAudit`으로 제외 |
| **사용 편의성** | Controller에 어노테이션 없이 자동 동작 |
| **완전한 추적** | URL, Path Variable, Query Parameter 모두 별도 컬럼으로 저장 |
| **확장 가능성** | Action/Resource ENUM 기반으로 쉬운 확장 |
| **요청 추적** | Trace ID로 분산 시스템 요청 추적 지원 |

### 1.3 클라이언트

- **CLI (dli)**: 데이터 전문가의 개발/실행 요청
- **Web UI**: 마케터 등 비개발자의 쿼리 실행
- **System**: 스케줄러, 내부 배치 작업

---

## 2. Architecture

### 2.1 Module Placement

```
module-core-common/
├── enums/
│   └── AuditEnums.kt          # AuditAction, AuditResource ENUM

module-core-domain/
├── entity/audit/
│   └── AuditLogEntity.kt      # 통합 Audit Entity (기존 Entity 대체)
├── repository/audit/
│   ├── AuditLogRepositoryJpa.kt
│   └── AuditLogRepositoryDsl.kt
└── service/
    └── AuditService.kt        # 기존 서비스 리팩토링

module-core-infra/
└── repository/audit/
    ├── AuditLogRepositoryJpaImpl.kt
    └── AuditLogRepositoryDslImpl.kt

module-server-api/
├── annotation/
│   └── NoAudit.kt             # Audit 제외 어노테이션
├── aspect/
│   └── AuditAspect.kt         # AOP 기반 자동 기록
├── filter/
│   └── TraceIdFilter.kt       # 요청별 Trace ID 생성 (v1.1.0)
├── controller/
│   └── AuditController.kt     # Management API
└── dto/audit/
    └── AuditDtos.kt           # 응답 DTO
```

### 2.2 데이터 흐름

```
┌─────────────┐     ┌──────────────┐     ┌─────────────┐     ┌─────────────┐
│  HTTP 요청   │────▶│ TraceIdFilter│────▶│ AuditAspect │────▶│   Controller │
└─────────────┘     │ (UUID 생성)   │     │  (Before)   │     │   Method     │
                    │ MDC에 저장    │     └─────────────┘     └──────┬───────┘
                    └──────────────┘                                │
                                                                    ▼
                    ┌─────────────┐                          ┌─────────────┐
                    │ AuditAspect │◀─────────────────────────│   Response   │
                    │  (After)    │                          └─────────────┘
                    │ MDC에서 조회 │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
                    │ AuditService│
                    │   .save()   │
                    └──────┬──────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  audit_log  │
                    │   (table)   │
                    └─────────────┘
```

---

## 3. Data Model

### 3.1 AuditLogEntity

> **Note**: Audit 로그는 불변(immutable) 특성상 `BaseEntity`를 상속하지 않음

```kotlin
@Entity
@Table(
    name = "audit_log",
    indexes = [
        Index(name = "idx_audit_log_user_id", columnList = "user_id"),
        Index(name = "idx_audit_log_action", columnList = "action"),
        Index(name = "idx_audit_log_resource", columnList = "resource"),
        Index(name = "idx_audit_log_created_at", columnList = "created_at"),
        Index(name = "idx_audit_log_user_action", columnList = "user_id,action"),
        Index(name = "idx_audit_log_resource_action", columnList = "resource,action"),
        Index(name = "idx_audit_log_trace_id", columnList = "trace_id"),  // v1.1.0
    ]
)
class AuditLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    val id: Long? = null,  // 프로젝트 패턴: nullable ID

    // 사용자 정보
    @Column(name = "user_id", nullable = false, updatable = false, length = 100)
    val userId: String,

    @Column(name = "user_email", nullable = true, updatable = false, length = 255)
    val userEmail: String? = null,

    // Trace ID (v1.1.0) - 분산 시스템 요청 추적
    @Column(name = "trace_id", nullable = true, updatable = false, length = 36)
    val traceId: String? = null,  // UUID format

    // Action & Resource Type
    @Column(name = "action", nullable = false, updatable = false, length = 50)
    @Enumerated(EnumType.STRING)
    val action: AuditAction,

    @Column(name = "resource", nullable = false, updatable = false, length = 50)
    @Enumerated(EnumType.STRING)
    val resource: AuditResource,

    // HTTP 요청 정보
    @Column(name = "http_method", nullable = false, updatable = false, length = 10)
    val httpMethod: String,

    @Column(name = "request_url", nullable = false, updatable = false, length = 2000)
    val requestUrl: String,

    @JdbcTypeCode(SqlTypes.JSON)  // 프로젝트 패턴: Hibernate JSON 타입
    @Column(name = "path_variables", nullable = true, updatable = false, columnDefinition = "json")
    val pathVariables: String? = null,  // JSON: {"name": "cpu_usage", "id": "123"}

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_parameters", nullable = true, updatable = false, columnDefinition = "json")
    val queryParameters: String? = null,  // JSON: {"page": "1", "size": "10"}

    // 응답 정보
    @Column(name = "response_status", nullable = false, updatable = false)
    val responseStatus: Int,

    @Column(name = "response_message", nullable = true, updatable = false, length = 500)
    val responseMessage: String? = null,

    // 성능 측정
    @Column(name = "duration_ms", nullable = false, updatable = false)
    val durationMs: Long,

    // 클라이언트 정보
    @Column(name = "client_type", nullable = true, updatable = false, length = 20)
    val clientType: String? = null,  // CLI, WEB, API

    @Column(name = "client_ip", nullable = true, updatable = false, length = 45)
    val clientIp: String? = null,

    @Column(name = "user_agent", nullable = true, updatable = false, length = 500)
    val userAgent: String? = null,

    // 클라이언트 메타데이터 (v1.2.0) - User-Agent 파싱 결과
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "client_metadata", nullable = true, updatable = false, columnDefinition = "json")
    val clientMetadata: String? = null,  // JSON: {"name": "dli", "version": "0.9.0", "os": "darwin", "python": "3.12.1", "command": "workflow backfill"}

    // 추가 컨텍스트
    @Column(name = "resource_id", nullable = true, updatable = false, length = 255)
    val resourceId: String? = null,  // e.g., metric name, dataset id

    @Column(name = "project_id", nullable = true, updatable = false)
    val projectId: Long? = null,

    // 타임스탬프 - 프로젝트 패턴: LocalDateTime 사용
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AuditLogEntity) return false
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
}
```

### 3.2 기존 Entity 처리

| Entity | 처리 방안 |
|--------|----------|
| `AuditAccessEntity` | **삭제** - AuditLogEntity로 통합 |
| `AuditResourceEntity` | **삭제** - AuditLogEntity로 통합 |

> **Migration Note**: 기존 테이블에 데이터가 없으므로 단순 삭제 후 신규 Entity 생성

---

## 4. ENUM Definitions

### 4.1 AuditAction

Controller 분석 기반 완전한 Action Type 정의:

```kotlin
enum class AuditAction(val value: String, val description: String) {
    // Session
    LOGIN("LOGIN", "사용자 로그인"),
    LOGOUT("LOGOUT", "사용자 로그아웃"),

    // CRUD Operations
    LIST("LIST", "목록 조회"),
    READ("READ", "단건 조회"),
    CREATE("CREATE", "생성"),
    UPDATE("UPDATE", "수정"),
    DELETE("DELETE", "삭제"),

    // Execution Operations
    EXECUTE("EXECUTE", "실행"),
    CANCEL("CANCEL", "실행 취소"),
    STOP("STOP", "실행 중지"),
    DOWNLOAD("DOWNLOAD", "결과 다운로드"),

    // Workflow Operations
    REGISTER("REGISTER", "워크플로우 등록"),
    UNREGISTER("UNREGISTER", "워크플로우 해제"),
    TRIGGER("TRIGGER", "워크플로우 트리거"),
    BACKFILL("BACKFILL", "백필 실행"),
    PAUSE("PAUSE", "일시 중지"),
    UNPAUSE("UNPAUSE", "일시 중지 해제"),

    // Resource Operations
    LOCK("LOCK", "리소스 잠금"),
    RELEASE("RELEASE", "리소스 해제"),

    // Catalog & Search
    SEARCH("SEARCH", "검색"),

    // Lineage
    LINEAGE("LINEAGE", "리니지 조회"),

    // Transpile
    TRANSPILE("TRANSPILE", "SQL 변환"),

    // Sync
    SYNC("SYNC", "동기화"),

    // Compare
    COMPARE("COMPARE", "비교"),

    // Flag Management
    OVERRIDE_SET("OVERRIDE_SET", "오버라이드 설정"),
    OVERRIDE_REMOVE("OVERRIDE_REMOVE", "오버라이드 제거"),
    PERMISSION_SET("PERMISSION_SET", "권한 설정"),
    PERMISSION_REMOVE("PERMISSION_REMOVE", "권한 제거"),
    EVALUATE("EVALUATE", "플래그 평가"),

    // Health Check (주로 @NoAudit 대상)
    HEALTH_CHECK("HEALTH_CHECK", "헬스 체크"),
}
```

### 4.2 AuditResource

모든 Controller의 리소스 타입 정의:

```kotlin
enum class AuditResource(val value: String, val description: String) {
    // Core Resources
    METRIC("METRIC", "메트릭"),
    DATASET("DATASET", "데이터셋"),
    WORKFLOW("WORKFLOW", "워크플로우"),
    QUALITY("QUALITY", "품질 스펙"),
    QUERY("QUERY", "쿼리"),

    // Catalog
    CATALOG("CATALOG", "카탈로그"),
    TABLE("TABLE", "테이블"),

    // Project & SQL Management
    PROJECT("PROJECT", "프로젝트"),
    SQL_FOLDER("SQL_FOLDER", "SQL 폴더"),
    SQL_SNIPPET("SQL_SNIPPET", "SQL 스니펫"),

    // Feature Flag
    FLAG("FLAG", "피처 플래그"),
    FLAG_OVERRIDE("FLAG_OVERRIDE", "플래그 오버라이드"),
    FLAG_PERMISSION("FLAG_PERMISSION", "플래그 권한"),

    // GitHub Integration
    GITHUB_REPOSITORY("GITHUB_REPOSITORY", "GitHub 저장소"),
    GITHUB_BRANCH("GITHUB_BRANCH", "GitHub 브랜치"),
    GITHUB_PULL_REQUEST("GITHUB_PULL_REQUEST", "GitHub PR"),

    // Lineage & Transpile
    LINEAGE("LINEAGE", "리니지"),
    TRANSPILE_RULE("TRANSPILE_RULE", "변환 규칙"),

    // Execution
    RUN("RUN", "실행"),
    EXECUTION("EXECUTION", "CLI 위임 실행"),

    // Resource Lock
    RESOURCE_LOCK("RESOURCE_LOCK", "리소스 잠금"),

    // Airflow Sync
    AIRFLOW_SYNC("AIRFLOW_SYNC", "Airflow 동기화"),

    // Session & System
    SESSION("SESSION", "세션"),
    SYSTEM("SYSTEM", "시스템"),
}
```

---

## 5. Annotation

### 5.1 @NoAudit

Audit 기록에서 제외할 엔드포인트에 적용:

```kotlin
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class NoAudit(
    val reason: String = "",  // 제외 사유 (문서화 목적)
)
```

### 5.2 @NoAudit 적용 대상

| Controller | Method | 사유 |
|------------|--------|------|
| `HealthController` | `health()`, `healthV1()`, `healthExtended()`, `info()` | 시스템 헬스 체크 |
| `SessionController` | `whoami()` | 빈번한 호출, 민감 정보 없음 |

---

## 6. AOP Implementation

### 6.1 AuditAspect

```kotlin
@Aspect
@Component
class AuditAspect(
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper,
) {
    private val logger = KotlinLogging.logger {}

    // v1.1.0: SecurityContext는 기존 프로젝트의 object를 직접 사용 (DI 불필요)

    /**
     * 모든 @RestController 메서드에 적용
     * @NoAudit이 있는 경우 제외
     */
    @Around("""
        @within(org.springframework.web.bind.annotation.RestController) &&
        !@annotation(com.dataops.basecamp.annotation.NoAudit) &&
        !@within(com.dataops.basecamp.annotation.NoAudit)
    """)
    fun audit(joinPoint: ProceedingJoinPoint): Any? {
        val startTime = System.currentTimeMillis()
        val request = getCurrentHttpRequest()

        var responseStatus = 200
        var responseMessage: String? = null
        var result: Any? = null

        try {
            result = joinPoint.proceed()

            // ResponseEntity에서 상태 코드 추출
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

        // v1.1.0: 기존 SecurityContext object 사용 + MDC에서 Trace ID 조회
        val userId = try {
            SecurityContext.getCurrentUserIdOrThrow().toString()
        } catch (e: Exception) {
            "anonymous"
        }

        auditService.saveLog(
            userId = userId,
            userEmail = SecurityContext.getCurrentUsername(),  // email 반환
            traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY),  // v1.1.0: MDC에서 조회
            action = action,
            resource = resource,
            httpMethod = request?.method ?: "UNKNOWN",
            requestUrl = request?.requestURL?.toString() ?: "",
            pathVariables = extractPathVariables(joinPoint),
            queryParameters = extractQueryParameters(request),
            responseStatus = responseStatus,
            responseMessage = responseMessage,
            durationMs = durationMs,
            clientType = extractClientType(request),
            clientIp = extractClientIp(request),
            userAgent = request?.getHeader("User-Agent"),
            clientMetadata = extractClientMetadata(request),  // v1.2.0: User-Agent 파싱 메타데이터
            resourceId = extractResourceId(joinPoint),
            projectId = extractProjectId(joinPoint),
        )
    }

    private fun resolveActionAndResource(joinPoint: ProceedingJoinPoint): Pair<AuditAction, AuditResource> {
        val methodName = joinPoint.signature.name
        val className = joinPoint.target.javaClass.simpleName

        // Controller 이름에서 Resource 추론
        val resource = when {
            className.contains("Metric") -> AuditResource.METRIC
            className.contains("Dataset") -> AuditResource.DATASET
            className.contains("Workflow") -> AuditResource.WORKFLOW
            className.contains("Quality") -> AuditResource.QUALITY
            className.contains("Query") -> AuditResource.QUERY
            className.contains("Catalog") -> AuditResource.CATALOG
            className.contains("Project") && methodName.contains("Snippet") -> AuditResource.SQL_SNIPPET
            className.contains("Project") && methodName.contains("Folder") -> AuditResource.SQL_FOLDER
            className.contains("Project") -> AuditResource.PROJECT
            className.contains("Flag") -> AuditResource.FLAG
            className.contains("GitHub") -> when {
                methodName.contains("Branch") -> AuditResource.GITHUB_BRANCH
                methodName.contains("PullRequest") -> AuditResource.GITHUB_PULL_REQUEST
                else -> AuditResource.GITHUB_REPOSITORY
            }
            className.contains("Lineage") -> AuditResource.LINEAGE
            className.contains("Transpile") -> AuditResource.TRANSPILE_RULE
            className.contains("Run") -> AuditResource.RUN
            className.contains("Execution") -> AuditResource.EXECUTION
            className.contains("Resource") -> AuditResource.RESOURCE_LOCK
            className.contains("AirflowSync") -> AuditResource.AIRFLOW_SYNC
            className.contains("Session") -> AuditResource.SESSION
            else -> AuditResource.SYSTEM
        }

        // Method 이름에서 Action 추론 (순서 중요: 구체적인 매칭 우선)
        val action = when {
            // Exact matches first
            methodName == "login" -> AuditAction.LOGIN
            methodName == "logout" -> AuditAction.LOGOUT

            // Specific patterns (order matters)
            methodName.startsWith("unpause") -> AuditAction.UNPAUSE  // "pause" 보다 먼저
            methodName.startsWith("pause") -> AuditAction.PAUSE
            methodName.startsWith("unregister") -> AuditAction.DELETE  // "register" 보다 먼저

            // CRUD patterns
            methodName.startsWith("list") -> AuditAction.LIST
            methodName.startsWith("get") || methodName.startsWith("find") -> AuditAction.READ
            methodName.startsWith("create") || methodName.startsWith("register") -> AuditAction.CREATE
            methodName.startsWith("update") -> AuditAction.UPDATE
            methodName.startsWith("delete") -> AuditAction.DELETE

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
            methodName.contains("Override") && methodName.contains("set") -> AuditAction.OVERRIDE_SET
            methodName.contains("Override") && methodName.contains("remove") -> AuditAction.OVERRIDE_REMOVE
            methodName.contains("Permission") && methodName.contains("set") -> AuditAction.PERMISSION_SET
            methodName.contains("Permission") && methodName.contains("remove") -> AuditAction.PERMISSION_REMOVE
            methodName.contains("evaluate") -> AuditAction.EVALUATE

            else -> AuditAction.READ  // 기본값
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

        return if (pathVars.isNotEmpty()) objectMapper.writeValueAsString(pathVars) else null
    }

    private fun extractQueryParameters(request: HttpServletRequest?): String? {
        val queryParams = request?.parameterMap
            ?.mapValues { it.value.toList() }
            ?.filterNot { it.value.isEmpty() }

        return if (!queryParams.isNullOrEmpty()) objectMapper.writeValueAsString(queryParams) else null
    }

    private fun extractClientType(request: HttpServletRequest?): String? {
        return request?.getHeader("X-Client-Type")
            ?: request?.getHeader("User-Agent")?.let { ua ->
                when {
                    ua.contains("dli/") -> "CLI"
                    ua.contains("Mozilla") || ua.contains("Chrome") -> "WEB"
                    else -> "API"
                }
            }
    }

    private fun extractClientIp(request: HttpServletRequest?): String? {
        return request?.getHeader("X-Forwarded-For")?.split(",")?.firstOrNull()?.trim()
            ?: request?.getHeader("X-Real-IP")
            ?: request?.remoteAddr
    }

    /**
     * User-Agent 파싱하여 클라이언트 메타데이터 JSON 생성 (v1.2.0)
     *
     * User-Agent 형식: dli/0.9.0 (darwin; Python/3.12.1) command/workflow-backfill
     */
    private fun extractClientMetadata(request: HttpServletRequest?): String? {
        val userAgent = request?.getHeader("User-Agent") ?: return null

        // dli CLI User-Agent 파싱
        val dliPattern = Regex("""dli/(\d+\.\d+\.\d+)\s*\(([^;]+);\s*Python/([^)]+)\)\s*command/(.+)""")
        val match = dliPattern.find(userAgent)

        return if (match != null) {
            val (version, os, pythonVersion, command) = match.destructured
            val metadata = mapOf(
                "name" to "dli",
                "version" to version,
                "os" to os,
                "python" to pythonVersion,
                "command" to command.replace("-", " ")  // workflow-backfill → workflow backfill
            )
            objectMapper.writeValueAsString(metadata)
        } else {
            // Web UI 또는 기타 클라이언트 - 기본 정보만 저장
            val metadata = mapOf("raw_user_agent" to userAgent.take(200))
            objectMapper.writeValueAsString(metadata)
        }
    }

    private fun extractResourceId(joinPoint: ProceedingJoinPoint): String? {
        // PathVariable에서 name, id 등 추출
        val method = (joinPoint.signature as MethodSignature).method
        val parameters = method.parameters
        val args = joinPoint.args

        parameters.forEachIndexed { index, param ->
            param.getAnnotation(PathVariable::class.java)?.let { annotation ->
                val name = annotation.value.ifEmpty { param.name }
                if (name in listOf("name", "id", "key", "snippetId", "folderId")) {
                    return args[index]?.toString()
                }
            }
        }
        return null
    }

    private fun extractProjectId(joinPoint: ProceedingJoinPoint): Long? {
        val method = (joinPoint.signature as MethodSignature).method
        val parameters = method.parameters
        val args = joinPoint.args

        parameters.forEachIndexed { index, param ->
            param.getAnnotation(PathVariable::class.java)?.let { annotation ->
                val name = annotation.value.ifEmpty { param.name }
                if (name == "projectId") {
                    return (args[index] as? Number)?.toLong()
                }
            }
        }
        return null
    }

    /**
     * GlobalExceptionHandler와 동일한 예외-HTTP 상태 매핑 (v1.1.0 확장)
     */
    private fun extractStatusFromException(e: Exception): Int {
        return when (e) {
            // 400 Bad Request
            is IllegalArgumentException -> 400
            is IllegalStateException -> 400
            is MethodArgumentNotValidException -> 400
            is ConstraintViolationException -> 400
            is DataIntegrityViolationException -> 400
            is CatalogTableNotFoundException -> 400  // 프로젝트 특정 예외
            is TranspileException -> 400

            // 401 Unauthorized
            is AuthenticationException -> 401

            // 403 Forbidden
            is AccessDeniedException -> 403

            // 404 Not Found
            is ResourceNotFoundException -> 404
            is MetricNotFoundException -> 404
            is DatasetNotFoundException -> 404
            is WorkflowNotFoundException -> 404
            is QualityNotFoundException -> 404
            is ProjectNotFoundException -> 404
            is FlagTargetNotFoundException -> 404
            is GitHubServiceException -> 404  // Repository/Branch not found

            // 408 Request Timeout
            is TimeoutException -> 408
            is WorkflowExecutionTimeoutException -> 408

            // 409 Conflict
            is DataAlreadyExistsException -> 409
            is ResourceLockedException -> 409

            // 500 Internal Server Error
            else -> 500
        }
    }

    private fun getCurrentHttpRequest(): HttpServletRequest? {
        return try {
            (RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request
        } catch (e: Exception) {
            null
        }
    }
}
```

### 6.2 TraceIdFilter (v1.1.0)

요청별 고유 Trace ID를 생성하여 분산 시스템에서 요청 추적을 지원합니다.

```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // 가장 먼저 실행
class TraceIdFilter : OncePerRequestFilter() {

    companion object {
        const val TRACE_ID_KEY = "traceId"
        const val TRACE_ID_HEADER = "X-Trace-Id"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // 클라이언트가 전달한 Trace ID 사용, 없으면 새로 생성
        val traceId = request.getHeader(TRACE_ID_HEADER)
            ?: UUID.randomUUID().toString()

        try {
            // MDC에 저장 (AuditAspect에서 조회)
            MDC.put(TRACE_ID_KEY, traceId)

            // 응답 헤더에도 포함 (클라이언트 추적 지원)
            response.setHeader(TRACE_ID_HEADER, traceId)

            filterChain.doFilter(request, response)
        } finally {
            // 요청 완료 후 반드시 정리 (메모리 누수 방지)
            MDC.remove(TRACE_ID_KEY)
        }
    }
}
```

**Trace ID 활용:**

| 위치 | 설명 |
|------|------|
| **클라이언트 헤더** | `X-Trace-Id`로 전달 시 동일 ID 유지 |
| **MDC** | AuditAspect에서 `MDC.get("traceId")`로 조회 |
| **응답 헤더** | `X-Trace-Id`로 반환하여 클라이언트 추적 |
| **로그** | Logback 패턴에 `%X{traceId}` 추가 가능 |

---

## 7. Service Layer

### 7.1 AuditService

```kotlin
@Service
@Transactional(readOnly = true)  // 프로젝트 패턴: 기본 읽기 전용
class AuditService(
    private val auditLogRepositoryJpa: AuditLogRepositoryJpa,
    private val auditLogRepositoryDsl: AuditLogRepositoryDsl,
) {
    @Transactional  // 쓰기 작업
    fun saveLog(
        userId: String,
        userEmail: String?,
        traceId: String?,  // v1.1.0
        action: AuditAction,
        resource: AuditResource,
        httpMethod: String,
        requestUrl: String,
        pathVariables: String?,
        queryParameters: String?,
        responseStatus: Int,
        responseMessage: String?,
        durationMs: Long,
        clientType: String?,
        clientIp: String?,
        userAgent: String?,
        clientMetadata: String?,  // v1.2.0
        resourceId: String?,
        projectId: Long?,
    ): AuditLogEntity {
        val entity = AuditLogEntity(
            userId = userId,
            userEmail = userEmail,
            traceId = traceId,  // v1.1.0
            action = action,
            resource = resource,
            httpMethod = httpMethod,
            requestUrl = requestUrl,
            pathVariables = pathVariables,
            queryParameters = queryParameters,
            responseStatus = responseStatus,
            responseMessage = responseMessage,
            durationMs = durationMs,
            clientType = clientType,
            clientIp = clientIp,
            userAgent = userAgent,
            clientMetadata = clientMetadata,  // v1.2.0
            resourceId = resourceId,
            projectId = projectId,
        )
        return auditLogRepositoryJpa.save(entity)
    }

    fun findById(id: Long): AuditLogEntity? {
        return auditLogRepositoryJpa.findById(id)
    }

    fun findByUserId(userId: String, pageable: Pageable): Page<AuditLogEntity> {
        return auditLogRepositoryDsl.findByUserId(userId, pageable)
    }

    fun findByAction(action: AuditAction, pageable: Pageable): Page<AuditLogEntity> {
        return auditLogRepositoryDsl.findByAction(action, pageable)
    }

    fun findByResource(resource: AuditResource, pageable: Pageable): Page<AuditLogEntity> {
        return auditLogRepositoryDsl.findByResource(resource, pageable)
    }

    fun searchLogs(
        userId: String?,
        action: AuditAction?,
        resource: AuditResource?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        pageable: Pageable,
    ): Page<AuditLogEntity> {
        return auditLogRepositoryDsl.search(
            userId = userId,
            action = action,
            resource = resource,
            startDate = startDate,
            endDate = endDate,
            pageable = pageable,
        )
    }

    fun getStats(startDate: LocalDateTime?, endDate: LocalDateTime?): AuditStatsDto {
        return auditLogRepositoryDsl.getStats(startDate, endDate)
    }
}
```

---

## 8. Management API

### 8.1 Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/api/v1/management/audit/logs` | Audit 로그 목록 조회 | Admin |
| `GET` | `/api/v1/management/audit/logs/{id}` | Audit 로그 상세 조회 | Admin |
| `GET` | `/api/v1/management/audit/stats` | Audit 통계 조회 | Admin |

### 8.2 Request/Response Examples

**로그 목록 조회:**

```http
GET /api/v1/management/audit/logs?userId=john&action=EXECUTE&resource=METRIC&page=0&size=20
```

```json
{
  "content": [
    {
      "id": 1,
      "userId": "john",
      "userEmail": "john@company.com",
      "action": "EXECUTE",
      "resource": "METRIC",
      "httpMethod": "POST",
      "requestUrl": "/api/v1/metrics/cpu_usage/run",
      "pathVariables": {"name": "cpu_usage"},
      "queryParameters": null,
      "responseStatus": 200,
      "durationMs": 1234,
      "clientType": "CLI",
      "resourceId": "cpu_usage",
      "createdAt": "2026-01-09T10:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8
}
```

### 8.3 AuditController

```kotlin
@RestController
@RequestMapping("/api/v1/management/audit")
@Validated  // 프로젝트 패턴: 검증 어노테이션
@Tag(name = "Audit Management", description = "Audit 로그 관리 API")
class AuditController(
    private val auditService: AuditService,
) {
    @NoAudit(reason = "Audit 조회 자체를 기록하지 않음")
    @GetMapping("/logs")
    @Operation(summary = "Audit 로그 목록 조회")
    fun listLogs(
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) action: AuditAction?,
        @RequestParam(required = false) resource: AuditResource?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC) pageable: Pageable,
    ): ResponseEntity<Page<AuditLogDto>> {
        val logs = auditService.searchLogs(userId, action, resource, startDate, endDate, pageable)
        return ResponseEntity.ok(logs.map { it.toDto() })
    }

    @NoAudit(reason = "Audit 조회 자체를 기록하지 않음")
    @GetMapping("/logs/{id}")
    @Operation(summary = "Audit 로그 상세 조회")
    fun getLog(@PathVariable id: Long): ResponseEntity<AuditLogDto> {
        val log = auditService.findById(id)
            ?: throw ResourceNotFoundException("AuditLog", id)
        return ResponseEntity.ok(log.toDto())
    }

    @NoAudit(reason = "Audit 조회 자체를 기록하지 않음")
    @GetMapping("/stats")
    @Operation(summary = "Audit 통계 조회")
    fun getStats(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?,
    ): ResponseEntity<AuditStatsDto> {
        val stats = auditService.getStats(startDate, endDate)
        return ResponseEntity.ok(stats)
    }
}
```

---

## 9. Exception Handling

기존 Exception 계층 활용, Audit 전용 Exception 불필요:

- `ResourceNotFoundException`: Audit 로그 미발견 시
- `BusinessException`: 잘못된 필터 파라미터 등

---

## 10. Implementation Phases

### Phase 1 (MVP) - 예상 3-4일

| Priority | Task | Description |
|----------|------|-------------|
| P0 | ENUM 정의 | `AuditAction`, `AuditResource` ENUM 생성 |
| P0 | Entity 생성 | `AuditLogEntity` 생성 (trace_id 포함), 기존 Entity 삭제 |
| P0 | Repository 구현 | JPA + DSL Repository 구현 |
| P0 | Service 리팩토링 | `AuditService` 신규 메서드 추가 |
| P0 | `@NoAudit` 어노테이션 | 제외 어노테이션 구현 |
| P0 | `TraceIdFilter` 구현 | 요청별 Trace ID 생성/MDC 저장 (v1.1.0) |
| P0 | `AuditAspect` 구현 | AOP 기반 자동 기록 |
| P1 | Management API | 조회/검색/통계 API |
| P1 | 기존 Controller에 `@NoAudit` 적용 | HealthController, SessionController.whoami |

### Phase 2 (Enhancement)

| Priority | Task | Description |
|----------|------|-------------|
| P2 | 비동기 기록 | `@Async` 적용으로 성능 최적화 |
| P2 | 데이터 보존 정책 | 오래된 Audit 로그 아카이빙/삭제 |
| P2 | 대시보드 통계 | 시간별/사용자별/리소스별 집계 API |

---

## 11. Trade-off Decisions

| 결정 | 이유 | 대안 |
|------|------|------|
| 동기 기록 (Phase 1) | 구현 단순성, 데이터 일관성 보장 | 비동기 (성능 우선) |
| JSON 컬럼 사용 | Path/Query 파라미터 유연한 저장 | 별도 테이블 (정규화) |
| ENUM으로 Action/Resource 정의 | 타입 안전성, IDE 지원 | 문자열 (유연성) |
| AOP 자동 기록 | 개발자 편의성, 누락 방지 | 수동 호출 (선택적 기록) |
| 기존 Entity 삭제 | 미사용 코드 정리 | 유지 (하위 호환) |
| Management API 경로 | Admin API와 구분, 역할 명확화 | /api/v1/admin/audit |
| Trace ID Phase 1 포함 (v1.1.0) | 분산 시스템 요청 추적, 디버깅 지원 | Phase 2 연기 (복잡성) |
| MDC 기반 Trace ID | 스레드 로컬 활용, 로깅 통합 | RequestAttribute 저장 |

---

## 12. Files to Create/Modify

### 12.1 신규 생성

| File | Location |
|------|----------|
| `AuditEnums.kt` | `module-core-common/src/.../enums/` |
| `AuditLogEntity.kt` | `module-core-domain/src/.../entity/audit/` |
| `AuditLogRepositoryJpa.kt` | `module-core-domain/src/.../repository/audit/` |
| `AuditLogRepositoryDsl.kt` | `module-core-domain/src/.../repository/audit/` |
| `AuditLogRepositoryJpaImpl.kt` | `module-core-infra/src/.../repository/audit/` |
| `AuditLogRepositoryDslImpl.kt` | `module-core-infra/src/.../repository/audit/` |
| `NoAudit.kt` | `module-server-api/src/.../annotation/` |
| `AuditAspect.kt` | `module-server-api/src/.../aspect/` |
| `TraceIdFilter.kt` | `module-server-api/src/.../filter/` (v1.1.0) |
| `AuditController.kt` | `module-server-api/src/.../controller/` |
| `AuditDtos.kt` | `module-server-api/src/.../dto/audit/` |

### 12.2 수정

| File | Change |
|------|--------|
| `AuditService.kt` | 신규 메서드 추가 (기존 유지하며 확장) |
| `HealthController.kt` | `@NoAudit` 추가 |
| `SessionController.kt` | `whoami()`에 `@NoAudit` 추가 |

### 12.3 삭제

| File | Reason |
|------|--------|
| `AuditAccessEntity.kt` | AuditLogEntity로 통합 |
| `AuditResourceEntity.kt` | AuditLogEntity로 통합 |
| `AuditAccessRepository*.kt` | 신규 Repository로 대체 |
| `AuditResourceRepository*.kt` | 신규 Repository로 대체 |

---

## 13. Usage Examples

### 13.1 일반 Controller (자동 기록)

```kotlin
@RestController
@RequestMapping("/api/v1/metrics")
class MetricController(
    private val metricService: MetricService,
) {
    // 자동으로 Audit 기록됨: action=EXECUTE, resource=METRIC
    @PostMapping("/{name}/run")
    fun runMetric(@PathVariable name: String): ResponseEntity<MetricResult> {
        return ResponseEntity.ok(metricService.run(name))
    }
}
```

### 13.2 @NoAudit 적용

```kotlin
@RestController
@RequestMapping("/api/health")
@NoAudit(reason = "시스템 헬스 체크는 Audit 불필요")  // 클래스 레벨
class HealthController {
    @GetMapping
    fun health(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(HealthResponse("UP"))
    }
}

@RestController
@RequestMapping("/api/v1/session")
class SessionController {
    @NoAudit(reason = "빈번한 호출, 민감 정보 없음")  // 메서드 레벨
    @GetMapping("/whoami")
    fun whoami(): ResponseEntity<UserInfo> {
        // ...
    }

    // 이 메서드는 Audit 기록됨
    @PostMapping("/login")
    fun login(): ResponseEntity<TokenResponse> {
        // ...
    }
}
```

---

## 14. Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.2.0 | 2026-01-09 | - | CLI Integration: client_metadata JSON 컬럼 추가, User-Agent 파싱 로직 추가 |
| 1.1.0 | 2026-01-09 | - | Spring Boot 4 Best Practices 적용: Trace ID 지원, 예외 매핑 확장, 기존 SecurityContext object 활용 |
| 1.0.1 | 2026-01-09 | - | Agent review feedback 반영: Entity 패턴 수정, Service 메서드 추가, AOP 순서 수정 |
| 1.0.0 | 2026-01-09 | - | Initial specification |

---

## 15. Review Notes

### 15.1 feature-basecamp-server Agent 리뷰 (2026-01-09)

**적용된 피드백:**

| Category | Issue | Resolution |
|----------|-------|------------|
| Entity Design | `Instant` 대신 `LocalDateTime` 사용 | ✅ `createdAt: LocalDateTime` 으로 변경 |
| Entity Design | JSON 컬럼에 `@JdbcTypeCode(SqlTypes.JSON)` 사용 | ✅ 프로젝트 패턴 적용 |
| Entity Design | ID nullable 패턴 (`id: Long? = null`) | ✅ 프로젝트 패턴 적용 |
| Entity Design | `equals/hashCode` 오버라이드 필요 | ✅ 추가됨 |
| Service Layer | `@Transactional(readOnly = true)` 누락 | ✅ 클래스 레벨 추가 |
| Service Layer | `findById`, `getStats` 메서드 누락 | ✅ 추가됨 |
| AOP | Action 매칭 순서 이슈 | ✅ 구체적 패턴 우선 순서로 재정렬 |
| Controller | `@Validated` 어노테이션 누락 | ✅ 추가됨 |
| Controller | `LocalDateTime` 타입 일관성 | ✅ `Instant` → `LocalDateTime` 변경 |

**향후 고려사항 (Phase 2):**

| Item | Description |
|------|-------------|
| 비동기 로깅 | `@Async` 또는 `TransactionalEventListener` 고려 |
| 데이터 보존 정책 | 오래된 Audit 로그 아카이빙/삭제 정책 |
| PII 처리 | 민감 정보 마스킹 (비밀번호, 토큰 등) |
| 테스트 커버리지 | `resolveActionAndResource` 단위 테스트, AOP 통합 테스트 |

### 15.2 SecurityContext 활용 (v1.1.0 업데이트)

> **결정**: 기존 프로젝트의 `SecurityContext` object를 직접 사용 (신규 인터페이스 정의 불필요)

**기존 SecurityContext 위치:** `module-server-api/src/.../util/SecurityContext.kt`

```kotlin
// 기존 프로젝트 SecurityContext object
@Component
object SecurityContext {
    fun getCurrentUserId(): Long? { ... }
    fun getCurrentUserIdOrThrow(): Long = ...  // 인증되지 않으면 예외 발생
    fun getCurrentUsername(): String { ... }   // 사용자 email 반환
}
```

**AuditAspect에서 사용 패턴:**

```kotlin
// v1.1.0: 기존 object 직접 사용
val userId = try {
    SecurityContext.getCurrentUserIdOrThrow().toString()
} catch (e: Exception) {
    "anonymous"  // 비인증 요청 처리
}
val userEmail = SecurityContext.getCurrentUsername()
```

**타입 차이 처리:**
- 기존 SecurityContext의 `getCurrentUserIdOrThrow()`는 `Long` 반환
- AuditLogEntity의 `userId`는 `String` 타입
- `.toString()`으로 변환하여 저장

### 15.3 Spring Boot 4 Best Practices 리서치 결과 (v1.1.0)

**연구 수행 에이전트:** expert-spring-kotlin, feature-basecamp-server

**적용된 개선 사항:**

| Category | 연구 결과 | 적용 내용 |
|----------|----------|----------|
| **Trace ID** | MDC + Filter 패턴이 분산 시스템 추적에 표준 | ✅ TraceIdFilter 추가, AuditLogEntity에 trace_id 컬럼 |
| **예외 매핑** | GlobalExceptionHandler와 동기화 필요 | ✅ extractStatusFromException 확장 (25+ 예외 타입) |
| **SecurityContext** | 프로젝트 기존 object 재활용 | ✅ 신규 인터페이스 대신 기존 object 사용 |
| **비동기 처리** | @Async 또는 ApplicationEventPublisher | ⏳ Phase 2 예정 (Phase 1은 동기 방식 유지) |

**검토 후 미채택 항목:**

| Item | 이유 |
|------|------|
| Hibernate Envers | Entity 변경 이력 추적용, API 호출 감사에 부적합 |
| Spring Actuator Audit | 보안 이벤트 특화, API 감사에는 커스텀 AOP가 더 적합 |
| Request/Response Body 캐싱 | AOP 타이밍 이슈, 메모리 부담, Phase 1에서 불필요 |

**Phase 2 고려 개선 사항:**

| Item | 설명 |
|------|------|
| 비동기 로깅 | `@Async` + `TransactionalEventListener` 조합 |
| Logback 통합 | `%X{traceId}` 패턴으로 로그에 Trace ID 포함 |
| 민감 정보 마스킹 | 비밀번호, API 토큰 등 PII 필터링 |
| 데이터 보존 정책 | 오래된 Audit 로그 아카이빙/삭제 자동화 |
