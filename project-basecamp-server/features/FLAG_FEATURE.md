# Feature Flag System Specification

## Overview

Feature Flag 시스템은 DataOps Platform의 기능을 사용자별로 활성화/비활성화하고, 세부 권한을 관리하기 위한 시스템입니다.

### 목적

1. **기능 토글**: 신규 기능의 점진적 롤아웃 (베타 테스트, A/B 테스트)
2. **사용자 타겟팅**: 특정 사용자에게만 기능 활성화/비활성화
3. **세부 권한 관리**: 활성화된 기능 내에서 세부 동작 제어 (Phase 2)
4. **Kill Switch**: 장애 시 즉시 기능 비활성화

### 클라이언트

- **project-basecamp-ui** (React SPA): 로그인 시 Flag 상태 조회 → Context에 저장
- **project-interface-cli** (Python CLI): `dli config flags` 명령으로 상태 확인

---

## Architecture

### Module Placement

```
module-core-common/
├── enums/
│   └── FlagEnums.kt                # FlagStatus, TargetingType, SubjectType
├── exception/
│   └── CommonExceptions.kt         # FlagDisabledException, FlagNotFoundException (통합)

module-core-domain/
├── entity/flag/
│   ├── FlagEntity.kt               # Flag 정의
│   └── FlagTargetEntity.kt         # Override + Permission 통합 (User/API 타겟팅)
├── external/flag/
│   └── FlagCachePort.kt            # 캐시 포트 인터페이스
├── projection/flag/
│   └── FlagProjections.kt          # 평가용 Projection
├── repository/flag/
│   ├── FlagRepositoryJpa.kt        # CRUD 인터페이스
│   ├── FlagRepositoryDsl.kt        # 복합 쿼리 인터페이스
│   ├── FlagTargetRepositoryJpa.kt  # Target CRUD 인터페이스
│   └── FlagTargetRepositoryDsl.kt  # Target 복합 쿼리 인터페이스
└── service/
    └── FlagService.kt              # 평가 로직 및 CRUD

module-core-infra/
├── external/
│   └── MockFlagCacheAdapter.kt     # Mock 캐시 구현
├── repository/flag/
│   ├── FlagRepositoryJpaImpl.kt
│   ├── FlagRepositoryDslImpl.kt
│   ├── FlagTargetRepositoryJpaImpl.kt
│   └── FlagTargetRepositoryDslImpl.kt

module-server-api/
├── controller/
│   └── FlagController.kt           # REST API (11 endpoints)
├── dto/flag/
│   └── FlagDtos.kt                 # 모든 DTO (FlagDto, FlagTargetDto, Command 등)
├── annotation/
│   └── RequireFlag.kt              # AOP 어노테이션
└── aspect/
    └── FlagAspect.kt               # Controller 레벨 검증
```

> **Note:** DTOs는 `module-server-api/dto/flag/` 패키지에 위치합니다 (기존 프로젝트 패턴 준수)
> **Update (2026-01-09):** FlagOverrideEntity와 FlagPermissionEntity가 FlagTargetEntity로 통합되었습니다.

---

## Data Model

### Entity Structure (Implemented)

> **Update (2026-01-09):** FlagOverrideEntity와 FlagPermissionEntity가 **FlagTargetEntity**로 통합되었습니다.
> Override(enabled)와 Permission(permissions JSON)을 단일 엔티티에서 관리합니다.

#### FlagEntity

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | PK |
| flag_key | VARCHAR(100) | 고유 키 (e.g., "query_editor_v2") |
| name | VARCHAR(200) | 표시 이름 |
| description | VARCHAR(1000) | 설명 |
| status | ENUM | ENABLED / DISABLED |
| targeting_type | ENUM | GLOBAL / USER |
| created_at, updated_at, ... | | BaseEntity 상속 |

```kotlin
@Entity
@Table(
    name = "flag",
    indexes = [
        Index(name = "idx_flag_key", columnList = "flag_key", unique = true),
        Index(name = "idx_flag_status", columnList = "status"),
        Index(name = "idx_flag_targeting_type", columnList = "targeting_type"),
    ],
)
class FlagEntity(
    @NotBlank(message = "Flag key is required")
    @Pattern(
        regexp = "^[a-z0-9_.-]+$",
        message = "Flag key must contain only lowercase alphanumeric characters, hyphens, underscores, and dots",
    )
    @Size(max = 100, message = "Flag key must not exceed 100 characters")
    @Column(name = "flag_key", nullable = false, unique = true, length = 100)
    var flagKey: String = "",

    @NotBlank(message = "Flag name is required")
    @Size(max = 200, message = "Flag name must not exceed 200 characters")
    @Column(name = "name", nullable = false, length = 200)
    var name: String = "",

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    @Column(name = "description", length = 1000)
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: FlagStatus = FlagStatus.DISABLED,

    @Enumerated(EnumType.STRING)
    @Column(name = "targeting_type", nullable = false)
    var targetingType: TargetingType = TargetingType.GLOBAL,
) : BaseEntity()
```

#### FlagTargetEntity (통합: Override + Permission)

| Column | Type | Description |
|--------|------|-------------|
| id | BIGINT | PK |
| flag_id | BIGINT | FK → flag (FK 필드 사용, JPA 관계 어노테이션 금지) |
| subject_type | ENUM | USER / API_TOKEN |
| subject_id | BIGINT | user_id 또는 token_id |
| enabled | BOOLEAN | true=강제 활성화, false=강제 비활성화 (Override 기능) |
| permissions | TEXT | JSON 형태 권한 맵 (e.g., `{"execute": true, "write": false}`) |

```kotlin
@Entity
@Table(
    name = "flag_target",
    indexes = [
        Index(name = "idx_flag_target_subject", columnList = "subject_type, subject_id"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_flag_target",
            columnNames = ["flag_id", "subject_type", "subject_id"]
        )
    ]
)
class FlagTargetEntity(
    // FK 필드 사용 (JPA 관계 어노테이션 금지 - ENTITY_RELATION.md 참조)
    @Column(name = "flag_id", nullable = false)
    var flagId: Long = 0L,

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    var subjectType: SubjectType = SubjectType.USER,

    @Column(name = "subject_id", nullable = false)
    var subjectId: Long = 0L,

    @Column(name = "enabled", nullable = false)
    var enabled: Boolean = true,

    // JSON 형태로 세부 권한 저장 (e.g., {"execute": true, "write": false})
    @Column(name = "permissions", columnDefinition = "TEXT")
    var permissions: String? = null,
) : BaseEntity()
```

> **IMPORTANT:** 이 프로젝트는 JPA 관계 어노테이션(`@ManyToOne`, `@OneToMany` 등)을 **금지**합니다.
> FK 필드(`var flagId: Long`)를 사용하고, 필요한 JOIN은 QueryDSL에서 수행합니다.
> 자세한 내용은 `docs/ENTITY_RELATION.md` 참조.

### Design Decision: Unified FlagTargetEntity

**Why unified entity instead of separate Override/Permission?**

| Aspect | Separate Entities | Unified Entity (Chosen) |
|--------|------------------|-------------------------|
| Query Complexity | 2 JOINs for full evaluation | 1 JOIN |
| Schema Simplicity | 3 tables | 2 tables |
| Permission Flexibility | Fixed columns | JSON (extensible) |
| API Surface | 6+ endpoints | 4 target endpoints |
| N+1 Prevention | Complex projection | Single projection |

### Enums

```kotlin
// module-core-common/enums/FlagEnums.kt
package com.dataops.basecamp.common.enums

enum class FlagStatus {
    ENABLED,   // 활성화 (targeting_type 에 따라 적용)
    DISABLED,  // 전체 비활성화
}

enum class TargetingType {
    GLOBAL,     // 전체 사용자 적용
    USER,       // Override가 있는 사용자만
    // Phase 2
    // ROLE,      // UserRole 기반
    // PERCENTAGE // 비율 기반 롤아웃
}

enum class SubjectType {
    USER,       // 사용자
    API_TOKEN,  // API 토큰 (Phase 2)
}
```

### Repository Projection (N+1 방지)

```kotlin
// FlagOverride 조회 시 flagKey 포함 - N+1 쿼리 방지
data class FlagOverrideWithKeyProjection(
    val flagKey: String,
    val flagId: Long,
    val subjectType: SubjectType,
    val subjectId: Long,
    val enabled: Boolean,
)
```

---

## Service Layer

### FlagService

```kotlin
@Service
@Transactional(readOnly = true)
class FlagService(
    private val flagRepositoryJpa: FlagRepositoryJpa,
    private val flagRepositoryDsl: FlagRepositoryDsl,
    private val flagOverrideRepositoryJpa: FlagOverrideRepositoryJpa,
    private val flagOverrideRepositoryDsl: FlagOverrideRepositoryDsl,
    // Phase 2: FlagPermissionRepository
    // Phase 2: CacheManager
) {
    /**
     * Flag 활성화 여부 확인
     *
     * 평가 순서:
     * 1. Flag 조회 → 없으면 false
     * 2. Status=DISABLED → false
     * 3. Override 존재 → override.enabled 반환
     * 4. targeting_type=GLOBAL → true
     * 5. targeting_type=USER → false (override 없으면)
     */
    fun isEnabled(flagKey: String, userId: Long): Boolean {
        val flag = flagRepositoryJpa.findByFlagKey(flagKey) ?: return false

        if (flag.status == FlagStatus.DISABLED) {
            return false
        }

        val override = flagOverrideRepositoryDsl.findByFlagIdAndSubject(
            flagId = flag.id,
            subjectType = SubjectType.USER,
            subjectId = userId
        )
        if (override != null) {
            return override.enabled
        }

        return when (flag.targetingType) {
            TargetingType.GLOBAL -> true
            TargetingType.USER -> false
        }
    }

    /**
     * 사용자의 모든 Flag 상태 조회 (클라이언트 API용)
     *
     * NOTE: Projection 패턴 사용으로 N+1 쿼리 방지
     * - 1 query: 모든 Flag 조회
     * - 1 query: 사용자의 모든 Override 조회 (JOIN으로 flagKey 포함)
     */
    fun evaluateAllFlags(userId: Long): FlagEvaluationDto {
        val allFlags = flagRepositoryJpa.findAll()

        // Projection으로 flagKey 포함하여 조회 (N+1 방지)
        val overrides = flagOverrideRepositoryDsl.findBySubjectWithFlagKey(
            subjectType = SubjectType.USER,
            subjectId = userId
        ).associateBy { it.flagKey }

        val evaluated = allFlags.associate { flag ->
            flag.flagKey to evaluateFlag(flag, overrides[flag.flagKey]?.enabled)
        }

        return FlagEvaluationDto(
            flags = evaluated,
            evaluatedAt = LocalDateTime.now()
        )
    }

    private fun evaluateFlag(flag: FlagEntity, overrideEnabled: Boolean?): Boolean {
        if (flag.status == FlagStatus.DISABLED) return false
        if (overrideEnabled != null) return overrideEnabled
        return flag.targetingType == TargetingType.GLOBAL
    }

    // Phase 2: hasPermission(flagKey, userId, permission)

    // CRUD operations
    @Transactional
    fun createFlag(command: CreateFlagCommand): FlagDto {
        if (flagRepositoryJpa.existsByFlagKey(command.flagKey)) {
            throw FlagAlreadyExistsException(command.flagKey)
        }

        val entity = FlagEntity(
            flagKey = command.flagKey,
            name = command.name,
            description = command.description,
            status = command.status,
            targetingType = command.targetingType,
        )
        val saved = flagRepositoryJpa.save(entity)
        return saved.toDto()
    }

    @Transactional
    fun updateFlag(flagKey: String, command: UpdateFlagCommand): FlagDto {
        val entity = flagRepositoryJpa.findByFlagKey(flagKey)
            ?: throw FlagNotFoundException(flagKey)

        command.name?.let { entity.name = it }
        command.description?.let { entity.description = it }
        command.status?.let { entity.status = it }
        command.targetingType?.let { entity.targetingType = it }

        return entity.toDto()
    }

    @Transactional
    fun deleteFlag(flagKey: String) {
        val entity = flagRepositoryJpa.findByFlagKey(flagKey)
            ?: throw FlagNotFoundException(flagKey)
        flagRepositoryJpa.delete(entity)
    }

    @Transactional
    fun setOverride(flagKey: String, command: SetOverrideCommand): FlagOverrideDto {
        val flag = flagRepositoryJpa.findByFlagKey(flagKey)
            ?: throw FlagNotFoundException(flagKey)

        val existing = flagOverrideRepositoryDsl.findByFlagIdAndSubject(
            flagId = flag.id,
            subjectType = command.subjectType,
            subjectId = command.subjectId
        )

        val entity = existing ?: FlagOverrideEntity(
            flagId = flag.id,
            subjectType = command.subjectType,
            subjectId = command.subjectId,
        )
        entity.enabled = command.enabled

        val saved = flagOverrideRepositoryJpa.save(entity)
        return saved.toDto(flagKey)
    }

    @Transactional
    fun removeOverride(flagKey: String, subjectType: SubjectType, subjectId: Long) {
        val flag = flagRepositoryJpa.findByFlagKey(flagKey)
            ?: throw FlagNotFoundException(flagKey)

        val override = flagOverrideRepositoryDsl.findByFlagIdAndSubject(
            flagId = flag.id,
            subjectType = subjectType,
            subjectId = subjectId
        ) ?: throw FlagOverrideNotFoundException(flagKey, subjectType, subjectId)

        flagOverrideRepositoryJpa.delete(override)
    }
}
```

### Repository Interfaces (Domain Layer)

```kotlin
// module-core-domain/repository/flag/FlagRepositoryJpa.kt
interface FlagRepositoryJpa {
    fun save(entity: FlagEntity): FlagEntity
    fun findById(id: Long): FlagEntity?
    fun findByFlagKey(flagKey: String): FlagEntity?
    fun existsByFlagKey(flagKey: String): Boolean
    fun findAll(): List<FlagEntity>
    fun delete(entity: FlagEntity)
}

// module-core-domain/repository/flag/FlagOverrideRepositoryDsl.kt
interface FlagOverrideRepositoryDsl {
    fun findByFlagIdAndSubject(
        flagId: Long,
        subjectType: SubjectType,
        subjectId: Long,
    ): FlagOverrideEntity?

    // Projection으로 flagKey 포함 - N+1 방지
    fun findBySubjectWithFlagKey(
        subjectType: SubjectType,
        subjectId: Long,
    ): List<FlagOverrideWithKeyProjection>
}
```

### Repository Implementation (Infra Layer)

```kotlin
// module-core-infra/repository/flag/FlagRepositoryJpaImpl.kt
@Repository("flagRepositoryJpa")
class FlagRepositoryJpaImpl(
    private val springDataRepository: FlagRepositoryJpaSpringData,
) : FlagRepositoryJpa {
    override fun save(entity: FlagEntity) = springDataRepository.save(entity)
    override fun findById(id: Long) = springDataRepository.findById(id).orElse(null)
    override fun findByFlagKey(flagKey: String) = springDataRepository.findByFlagKey(flagKey)
    override fun existsByFlagKey(flagKey: String) = springDataRepository.existsByFlagKey(flagKey)
    override fun findAll() = springDataRepository.findAll()
    override fun delete(entity: FlagEntity) = springDataRepository.delete(entity)
}

// module-core-infra/repository/flag/FlagOverrideRepositoryDslImpl.kt
@Repository("flagOverrideRepositoryDsl")
class FlagOverrideRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : FlagOverrideRepositoryDsl {

    private val flag = QFlagEntity.flagEntity
    private val override = QFlagOverrideEntity.flagOverrideEntity

    override fun findByFlagIdAndSubject(
        flagId: Long,
        subjectType: SubjectType,
        subjectId: Long,
    ): FlagOverrideEntity? {
        return queryFactory
            .selectFrom(override)
            .where(
                override.flagId.eq(flagId),
                override.subjectType.eq(subjectType),
                override.subjectId.eq(subjectId),
                override.deletedAt.isNull,
            )
            .fetchOne()
    }

    // Projection으로 JOIN하여 N+1 방지
    override fun findBySubjectWithFlagKey(
        subjectType: SubjectType,
        subjectId: Long,
    ): List<FlagOverrideWithKeyProjection> {
        return queryFactory
            .select(
                Projections.constructor(
                    FlagOverrideWithKeyProjection::class.java,
                    flag.flagKey,
                    override.flagId,
                    override.subjectType,
                    override.subjectId,
                    override.enabled,
                )
            )
            .from(override)
            .join(flag).on(override.flagId.eq(flag.id))
            .where(
                override.subjectType.eq(subjectType),
                override.subjectId.eq(subjectId),
                override.deletedAt.isNull,
                flag.deletedAt.isNull,
            )
            .fetch()
    }
}
```

### AOP 어노테이션

```kotlin
// module-server-api/annotation/RequireFlag.kt

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireFlag(
    val key: String,
    val fallbackMessage: String = "This feature is not available",
    // Phase 2
    // val permission: String = "",
)
```

```kotlin
// module-server-api/aspect/FlagAspect.kt

@Aspect
@Component
class FlagAspect(
    private val flagService: FlagService,
) {
    @Around("@annotation(requireFlag)")
    fun checkFlag(
        joinPoint: ProceedingJoinPoint,
        requireFlag: RequireFlag,
    ): Any? {
        val authentication = SecurityContextHolder.getContext().authentication
        val userId = extractUserId(authentication)

        if (!flagService.isEnabled(requireFlag.key, userId)) {
            throw FlagDisabledException(
                flagKey = requireFlag.key,
                message = requireFlag.fallbackMessage
            )
        }

        return joinPoint.proceed()
    }

    private fun extractUserId(authentication: Authentication): Long {
        // JWT에서 userId 추출
        val principal = authentication.principal as? UserPrincipal
            ?: throw UnauthorizedException("User principal not found")
        return principal.userId
    }
}
```

### 직접 서비스 호출 (AOP 대안)

```kotlin
@RestController
@RequestMapping("/api/v1/lineage")
class LineageController(
    private val lineageService: LineageService,
    private val flagService: FlagService,
) {
    @GetMapping("/graph/advanced")
    fun getAdvancedLineageGraph(): ResponseEntity<LineageGraphDto> {
        val userId = getCurrentUserId()
        if (!flagService.isEnabled("advanced_lineage", userId)) {
            throw FlagDisabledException("advanced_lineage", "Advanced lineage is not enabled")
        }
        return ResponseEntity.ok(lineageService.getAdvancedGraph())
    }
}
```

---

## REST API

> **Update (2026-01-09):** API가 13개에서 **11개**로 단순화되었습니다. `/overrides`와 `/permissions` 엔드포인트가 `/targets`로 통합되었습니다.

### 클라이언트용 API (2 endpoints)

| Method | Endpoint | Description | Response |
|--------|----------|-------------|----------|
| GET | `/api/v1/flags/evaluate` | 현재 사용자의 모든 Flag 상태 | `FlagEvaluationDto` |
| GET | `/api/v1/flags/evaluate/{key}` | 특정 Flag 상태 | `FlagSingleEvaluationDto` |

### Flag CRUD API (5 endpoints)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | `/api/v1/flags` | 전체 Flag 목록 | - | `List<FlagDto>` |
| POST | `/api/v1/flags` | Flag 생성 | `CreateFlagRequest` | `FlagDto` |
| GET | `/api/v1/flags/{key}` | Flag 상세 | - | `FlagDto` |
| PUT | `/api/v1/flags/{key}` | Flag 수정 | `UpdateFlagRequest` | `FlagDto` |
| DELETE | `/api/v1/flags/{key}` | Flag 삭제 | - | 204 No Content |

### Target 관리 API (4 endpoints)

| Method | Endpoint | Description | Request | Response |
|--------|----------|-------------|---------|----------|
| GET | `/api/v1/flags/{key}/targets` | Target 목록 | - | `List<FlagTargetDto>` |
| POST | `/api/v1/flags/{key}/targets` | Target 설정 (Override + Permission) | `SetTargetRequest` | `FlagTargetDto` |
| PUT | `/api/v1/flags/{key}/targets/permissions` | Target Permission 수정 | `UpdatePermissionRequest` | `FlagTargetDto` |
| DELETE | `/api/v1/flags/{key}/targets/{subjectType}/{subjectId}` | Target 제거 | - | 204 No Content |

> **Note:** DELETE target 엔드포인트는 `/{subjectType}/{subjectId}` 경로 파라미터 사용 (쿼리 파라미터 대신)

### DTOs

```kotlin
// module-server-api/dto/flag/FlagDtos.kt
package com.dataops.basecamp.dto.flag

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

// ============ Response DTOs ============

data class FlagDto(
    val id: Long,
    val flagKey: String,
    val name: String,
    val description: String?,
    val status: FlagStatus,
    val targetingType: TargetingType,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: LocalDateTime,
)

data class FlagTargetDto(
    val id: Long,
    val flagKey: String,
    val subjectType: SubjectType,
    val subjectId: Long,
    val enabled: Boolean,
    val permissions: Map<String, Boolean>?,  // JSON → Map 변환
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: LocalDateTime,
)

data class FlagEvaluationDto(
    val flags: Map<String, Boolean>,
    val permissions: Map<String, Map<String, Boolean>>,  // flagKey → permission map
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val evaluatedAt: LocalDateTime,
)

data class FlagSingleEvaluationDto(
    val flagKey: String,
    val enabled: Boolean,
    val permissions: Map<String, Boolean>?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val evaluatedAt: LocalDateTime,
)

// ============ Request DTOs ============

data class CreateFlagRequest(
    @field:NotBlank(message = "Flag key is required")
    @field:Pattern(
        regexp = "^[a-z0-9_.-]+$",
        message = "Flag key must contain only lowercase alphanumeric characters, hyphens, underscores, and dots"
    )
    val flagKey: String,

    @field:NotBlank(message = "Flag name is required")
    val name: String,

    val description: String? = null,
    val status: FlagStatus = FlagStatus.DISABLED,
    val targetingType: TargetingType = TargetingType.GLOBAL,
)

data class UpdateFlagRequest(
    val name: String? = null,
    val description: String? = null,
    val status: FlagStatus? = null,
    val targetingType: TargetingType? = null,
)

data class SetTargetRequest(
    val subjectType: SubjectType = SubjectType.USER,
    val subjectId: Long,
    val enabled: Boolean,
    val permissions: Map<String, Boolean>? = null,  // Optional permission map
)

data class UpdatePermissionRequest(
    val subjectType: SubjectType = SubjectType.USER,
    val subjectId: Long,
    val permissions: Map<String, Boolean>,  // Required permission map for update
)
```

### 응답 예시

```json
// GET /api/v1/flags/evaluate
{
  "flags": {
    "query_editor_v2": true,
    "advanced_lineage": false,
    "new_catalog_ui": true
  },
  "permissions": {
    "query_editor_v2": {
      "execute": true,
      "write": false
    }
  },
  "evaluatedAt": "2026-01-09T10:00:00Z"
}

// GET /api/v1/flags/evaluate/query_editor_v2
{
  "flagKey": "query_editor_v2",
  "enabled": true,
  "permissions": {
    "execute": true,
    "write": false
  },
  "evaluatedAt": "2026-01-09T10:00:00Z"
}

// GET /api/v1/flags/query_editor_v2
{
  "id": 1,
  "flagKey": "query_editor_v2",
  "name": "Query Editor V2",
  "description": "새로운 쿼리 에디터 베타",
  "status": "ENABLED",
  "targetingType": "USER",
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-01-05T12:00:00Z"
}

// GET /api/v1/flags/query_editor_v2/targets
[
  {
    "id": 1,
    "flagKey": "query_editor_v2",
    "subjectType": "USER",
    "subjectId": 123,
    "enabled": true,
    "permissions": {
      "execute": true,
      "write": false
    },
    "createdAt": "2026-01-06T10:00:00Z",
    "updatedAt": "2026-01-09T12:00:00Z"
  }
]
```

---

## Exception Handling

```kotlin
// module-core-common/exception/FlagExceptions.kt
package com.dataops.basecamp.common.exception

/**
 * Feature Flag 관련 예외 (프로젝트 Exception 계층 구조 준수)
 */
class FlagDisabledException(
    val flagKey: String,
    override val message: String = "Feature flag '$flagKey' is not enabled",
) : BusinessException(message)

class FlagNotFoundException(
    val flagKey: String,
) : ResourceNotFoundException(resource = "Flag", identifier = flagKey)

class FlagAlreadyExistsException(
    val flagKey: String,
) : BusinessException("Feature flag '$flagKey' already exists")

class FlagOverrideNotFoundException(
    val flagKey: String,
    val subjectType: SubjectType,
    val subjectId: Long,
) : ResourceNotFoundException(
    resource = "FlagOverride",
    identifier = "$flagKey:$subjectType:$subjectId"
)
```

```kotlin
// module-server-api/advice/FlagExceptionHandler.kt
package com.dataops.basecamp.api.advice

@RestControllerAdvice
class FlagExceptionHandler {

    @ExceptionHandler(FlagDisabledException::class)
    fun handleFlagDisabled(ex: FlagDisabledException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(
                code = "FLAG_DISABLED",
                message = ex.message ?: "Feature is not enabled",
            ))
    }

    @ExceptionHandler(FlagNotFoundException::class)
    fun handleFlagNotFound(ex: FlagNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                code = "FLAG_NOT_FOUND",
                message = ex.message ?: "Feature flag not found",
            ))
    }

    @ExceptionHandler(FlagAlreadyExistsException::class)
    fun handleFlagAlreadyExists(ex: FlagAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(
                code = "FLAG_ALREADY_EXISTS",
                message = ex.message ?: "Feature flag already exists",
            ))
    }
}
```

---

## Client Integration

### Web UI (React)

```typescript
// context/FlagContext.tsx
interface FlagContextValue {
  flags: Record<string, boolean>;
  isLoading: boolean;
  isEnabled: (key: string) => boolean;
  refetch: () => Promise<void>;
}

const FlagContext = createContext<FlagContextValue | null>(null);

export function FlagProvider({ children }: { children: React.ReactNode }) {
  const [flags, setFlags] = useState<Record<string, boolean>>({});
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchFlags();
  }, []);

  const fetchFlags = async () => {
    const response = await api.get('/api/v1/flags/evaluate');
    setFlags(response.data.flags);
    setIsLoading(false);
  };

  const isEnabled = (key: string) => flags[key] ?? false;

  return (
    <FlagContext.Provider value={{ flags, isLoading, isEnabled, refetch: fetchFlags }}>
      {children}
    </FlagContext.Provider>
  );
}

export const useFlags = () => {
  const context = useContext(FlagContext);
  if (!context) throw new Error('useFlags must be used within FlagProvider');
  return context;
};

// 사용 예시
function QueryEditor() {
  const { isEnabled } = useFlags();

  if (isEnabled('query_editor_v2')) {
    return <NewQueryEditor />;
  }
  return <LegacyQueryEditor />;
}
```

### CLI (dli)

```python
# dli/api/config.py (ConfigAPI 확장)
class ConfigAPI:
    def get_flags(self) -> dict[str, bool]:
        """현재 사용자의 모든 Flag 상태 조회"""
        response = self._client.get("/api/v1/flags/evaluate")
        return response.json()["flags"]

    def is_flag_enabled(self, key: str) -> bool:
        """특정 Flag 활성화 여부 확인"""
        response = self._client.get(f"/api/v1/flags/evaluate/{key}")
        return response.json()["enabled"]

# dli/commands/config.py (CLI 명령)
@config_app.command("flags")
def show_flags():
    """현재 사용자의 Feature Flag 상태 확인"""
    api = ConfigAPI(context=get_context())
    flags = api.get_flags()

    table = Table(title="Feature Flags")
    table.add_column("Flag", style="cyan")
    table.add_column("Enabled", style="green")

    for key, enabled in sorted(flags.items()):
        table.add_row(key, "✓" if enabled else "✗")

    console.print(table)
```

**CLI 출력 예시:**
```
$ dli config flags
┌────────────────────┬─────────┐
│ Flag               │ Enabled │
├────────────────────┼─────────┤
│ advanced_lineage   │ ✗       │
│ new_catalog_ui     │ ✓       │
│ query_editor_v2    │ ✓       │
└────────────────────┴─────────┘
```

---

## Database Schema

> **Update (2026-01-09):** flag_override와 flag_permission 테이블이 **flag_target**으로 통합되었습니다.

```sql
-- Flag 정의 테이블
CREATE TABLE flag (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flag_key VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    status VARCHAR(20) NOT NULL DEFAULT 'DISABLED',
    targeting_type VARCHAR(20) NOT NULL DEFAULT 'GLOBAL',

    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,

    CONSTRAINT uk_flag_key UNIQUE (flag_key),
    INDEX idx_flag_key (flag_key),
    INDEX idx_flag_status (status),
    INDEX idx_flag_targeting_type (targeting_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Target 테이블 (Override + Permission 통합)
CREATE TABLE flag_target (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    flag_id BIGINT NOT NULL,
    subject_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    subject_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    permissions TEXT,  -- JSON 형태: {"execute": true, "write": false}

    created_by BIGINT,
    updated_by BIGINT,
    deleted_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,

    CONSTRAINT uk_flag_target UNIQUE (flag_id, subject_type, subject_id),
    CONSTRAINT fk_flag_target_flag FOREIGN KEY (flag_id) REFERENCES flag(id) ON DELETE CASCADE,
    INDEX idx_flag_target_subject (subject_type, subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## Implementation Phases

### Phase 1 (MVP) - ✅ **Completed (2026-01-09)**

| 구성요소 | 상세 | 상태 |
|----------|------|------|
| **Entity** | FlagEntity, FlagTargetEntity (통합) | ✅ Complete |
| **Repository** | JPA + DSL 구현 (Projection 패턴 포함) | ✅ Complete |
| **Service** | FlagService (isEnabled, hasPermission, evaluateAllFlags, CRUD) | ✅ Complete |
| **Controller** | FlagController (11 endpoints) | ✅ Complete |
| **AOP** | @RequireFlag 어노테이션 + Aspect | ✅ Complete |
| **Exception** | CommonExceptions 통합 | ✅ Complete |
| **DTOs** | module-server-api/dto/flag/ 위치 | ✅ Complete |
| **Cache** | FlagCachePort + MockFlagCacheAdapter | ✅ Complete |
| **Tests** | 60+ tests (Service + Controller) | ✅ Complete |

### Phase 2 (확장)

| 구성요소 | 상세 | 우선순위 |
|----------|------|----------|
| **Real Cache** | Redis 캐시 구현체 | P1 |
| **API Token** | SubjectType.API_TOKEN 확장 | P1 |
| **Role 타겟팅** | TargetingType.ROLE | P2 |
| **비율 롤아웃** | TargetingType.PERCENTAGE | P2 |
| **Admin UI** | Flag 관리 화면 | P2 |
| **Audit Log** | 변경 이력 추적 | P2 |

---

## Usage Examples

### 시나리오 1: 베타 기능 테스트 (with Permissions)

```http
### Flag 생성 (ADMIN)
POST /api/v1/flags
Content-Type: application/json

{
  "flagKey": "query_editor_v2",
  "name": "Query Editor V2",
  "description": "새로운 쿼리 에디터 베타 테스트",
  "status": "ENABLED",
  "targetingType": "USER"
}

### 특정 사용자에게 활성화 + 세부 권한 설정
POST /api/v1/flags/query_editor_v2/targets
Content-Type: application/json

{
  "subjectType": "USER",
  "subjectId": 123,
  "enabled": true,
  "permissions": {
    "execute": true,
    "write": false
  }
}

### 권한만 업데이트 (enabled는 유지)
PUT /api/v1/flags/query_editor_v2/targets/permissions
Content-Type: application/json

{
  "subjectType": "USER",
  "subjectId": 123,
  "permissions": {
    "execute": true,
    "write": true
  }
}

### 사용자 123은 true + permissions, 다른 사용자는 false
GET /api/v1/flags/evaluate/query_editor_v2
# User 123: { "flagKey": "query_editor_v2", "enabled": true, "permissions": {"execute": true, "write": true}, "evaluatedAt": "..." }
# User 456: { "flagKey": "query_editor_v2", "enabled": false, "permissions": null, "evaluatedAt": "..." }
```

### 시나리오 2: 전체 사용자 기능 활성화

```http
### Flag 생성 (ADMIN)
POST /api/v1/flags
Content-Type: application/json

{
  "flagKey": "new_catalog_ui",
  "name": "New Catalog UI",
  "status": "ENABLED",
  "targetingType": "GLOBAL"
}

### 특정 사용자만 비활성화 (문제 발생 시)
POST /api/v1/flags/new_catalog_ui/targets
Content-Type: application/json

{
  "subjectType": "USER",
  "subjectId": 789,
  "enabled": false
}

### Target 삭제
DELETE /api/v1/flags/new_catalog_ui/targets/USER/789
```

### 시나리오 3: Controller에서 Flag 검증

```kotlin
// 방법 1: AOP 어노테이션
@RequireFlag("advanced_lineage")
@GetMapping("/lineage/advanced")
fun getAdvancedLineage(): ResponseEntity<LineageDto> {
    return ResponseEntity.ok(lineageService.getAdvanced())
}

// 방법 2: 직접 서비스 호출
@GetMapping("/lineage/advanced")
fun getAdvancedLineage(): ResponseEntity<LineageDto> {
    val userId = getCurrentUserId()
    if (!flagService.isEnabled("advanced_lineage", userId)) {
        throw FlagDisabledException("advanced_lineage")
    }
    return ResponseEntity.ok(lineageService.getAdvanced())
}
```

---

## Trade-off Decisions

| 결정 사항 | 선택 | 이유 | 대안 |
|-----------|------|------|------|
| **저장소** | Database + Cache (Phase 2) | 기존 인프라 활용, 단순함 | 외부 서비스 (LaunchDarkly) |
| **평가** | Server-side | 보안, 단일 진실 소스 | Client-side SDK |
| **타겟팅** | User + (Role Phase 2) | MVP 단순화 | 복잡한 규칙 엔진 |
| **AOP** | 어노테이션 + 직접호출 둘 다 | 유연성 | 어노테이션만 |
| **네이밍** | Flag (간결) | 코드 가독성 | FeatureFlag (명시적) |
| **JPA 관계** | FK 필드 사용 | 프로젝트 규칙, N+1 방지 | @ManyToOne (금지) |
| **DTO 위치** | module-server-api | API 레이어 책임 분리 | module-core-domain |

---

## Entity Relationship

> **Update (2026-01-09):** 구현 완료 - `docs/ENTITY_RELATION.md`에 반영됨

```markdown
### Flag Domain

| Entity | FK Field | References | Cardinality | Notes |
|--------|----------|------------|-------------|-------|
| `FlagTargetEntity` | `flagId` | `FlagEntity` | N:1 | User/API target with override and permissions |

Flag (root)
  +-- FlagTarget (flagId FK)
```

---

## References

- [FeatBit - Feature Flag System Design 2025](https://www.featbit.co/articles2025/feature-flag-system-design-2025)
- [LaunchDarkly Documentation](https://launchdarkly.com/docs/guides/flags/flag-hierarchy)
- [ConfigCat Evaluation](https://configcat.com/docs/targeting/feature-flag-evaluation/)
- [PostHog Feature Flag System](https://deepwiki.com/PostHog/posthog/4.1-feature-flag-system)

---

## Review Notes (Agent Feedback Applied)

다음 피드백이 문서에 반영되었습니다:

| 항목 | 수정 내용 |
|------|-----------|
| JPA 관계 어노테이션 | `@ManyToOne` 제거 → `var flagId: Long` FK 필드 사용 |
| N+1 쿼리 방지 | `FlagOverrideWithKeyProjection` Projection 패턴 추가 |
| DTO 위치 | `module-core-domain/dto/` → `module-server-api/dto/flag/` |
| 유효성 검증 | Entity에 `@NotBlank`, `@Pattern`, `@Size` 추가 |
| Index 어노테이션 | `@Table(indexes = [...])` 추가 |
| Exception 계층 | `RuntimeException` → `BusinessException` 상속 |
| DELETE 엔드포인트 | 쿼리 파라미터 → 경로 파라미터 `/{subjectType}/{subjectId}` |
| Repository 빈 명명 | `@Repository("flagRepositoryJpa")` 명시 |

---

## Changelog

| 버전 | 날짜 | 변경 내용 |
|------|------|-----------|
| 0.1.0 | 2026-01-06 | 초안 작성 (Phase 1 MVP 설계) |
| 0.1.1 | 2026-01-06 | Agent 리뷰 피드백 반영 (JPA 관계 제거, Projection 패턴, DTO 위치 수정 등) |
| **1.0.0** | **2026-01-09** | **구현 완료** - FlagOverrideEntity + FlagPermissionEntity → FlagTargetEntity 통합, API 13개 → 11개 단순화, 60+ 테스트 |
