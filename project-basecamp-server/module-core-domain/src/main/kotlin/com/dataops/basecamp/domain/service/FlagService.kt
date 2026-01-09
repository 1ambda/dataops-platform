package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.FlagStatus
import com.dataops.basecamp.common.enums.SubjectType
import com.dataops.basecamp.common.enums.TargetingType
import com.dataops.basecamp.common.exception.FlagAlreadyExistsException
import com.dataops.basecamp.common.exception.FlagNotFoundException
import com.dataops.basecamp.common.exception.FlagTargetNotFoundException
import com.dataops.basecamp.domain.entity.flag.FlagEntity
import com.dataops.basecamp.domain.entity.flag.FlagTargetEntity
import com.dataops.basecamp.domain.external.flag.FlagCachePort
import com.dataops.basecamp.domain.projection.flag.FlagTargetWithKeyProjection
import com.dataops.basecamp.domain.repository.flag.FlagRepositoryJpa
import com.dataops.basecamp.domain.repository.flag.FlagTargetRepositoryDsl
import com.dataops.basecamp.domain.repository.flag.FlagTargetRepositoryJpa
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Feature Flag 서비스 응답 DTO
 */
data class FlagEvaluationResult(
    val flags: Map<String, Boolean>,
    val permissions: Map<String, Map<String, Boolean>>,
    val evaluatedAt: LocalDateTime,
)

/**
 * Feature Flag 생성 Command
 */
data class CreateFlagCommand(
    val flagKey: String,
    val name: String,
    val description: String? = null,
    val status: FlagStatus = FlagStatus.DISABLED,
    val targetingType: TargetingType = TargetingType.GLOBAL,
)

/**
 * Feature Flag 수정 Command
 */
data class UpdateFlagCommand(
    val name: String? = null,
    val description: String? = null,
    val status: FlagStatus? = null,
    val targetingType: TargetingType? = null,
)

/**
 * FlagTarget 설정 Command (Override + Permission 통합)
 */
data class SetTargetCommand(
    val subjectType: SubjectType = SubjectType.USER,
    val subjectId: Long,
    val enabled: Boolean,
    val permissions: Map<String, Boolean>? = null,
)

/**
 * FlagTarget Permission 수정 Command
 */
data class UpdatePermissionCommand(
    val subjectType: SubjectType = SubjectType.USER,
    val subjectId: Long,
    val permissionKey: String,
    val granted: Boolean,
)

/**
 * Feature Flag 서비스
 *
 * Feature Flag의 평가, CRUD, Target 관리를 담당합니다.
 * FlagTargetEntity로 Override와 Permission을 통합 관리합니다.
 */
@Service
@Transactional(readOnly = true)
class FlagService(
    private val flagRepositoryJpa: FlagRepositoryJpa,
    private val flagTargetRepositoryJpa: FlagTargetRepositoryJpa,
    private val flagTargetRepositoryDsl: FlagTargetRepositoryDsl,
    private val flagCachePort: FlagCachePort,
) {
    private val objectMapper = jacksonObjectMapper()

    // ============= 평가 메서드 =============

    /**
     * Flag 활성화 여부 확인
     *
     * 평가 순서:
     * 1. Flag 조회 -> 없으면 false
     * 2. Status=DISABLED -> false
     * 3. Target 존재 -> target.enabled 반환
     * 4. targeting_type=GLOBAL -> true
     * 5. targeting_type=USER -> false (target 없으면)
     */
    fun isEnabled(
        flagKey: String,
        userId: Long,
    ): Boolean {
        val flag = flagRepositoryJpa.findByFlagKey(flagKey) ?: return false

        if (flag.status == FlagStatus.DISABLED) {
            return false
        }

        val target =
            flagTargetRepositoryDsl.findByFlagIdAndSubject(
                flagId = flag.id!!,
                subjectType = SubjectType.USER,
                subjectId = userId,
            )

        if (target != null) {
            return target.enabled
        }

        return when (flag.targetingType) {
            TargetingType.GLOBAL -> true
            TargetingType.USER -> false
        }
    }

    /**
     * Permission 여부 확인
     *
     * 평가 순서:
     * 1. Flag 활성화 여부 확인 -> 비활성화면 false
     * 2. Target 조회 -> 없으면 true (기본값은 허용)
     * 3. permissions JSON에서 permissionKey 조회 -> 없으면 true (기본값은 허용)
     */
    fun hasPermission(
        flagKey: String,
        userId: Long,
        permissionKey: String,
    ): Boolean {
        if (!isEnabled(flagKey, userId)) {
            return false
        }

        val flag = flagRepositoryJpa.findByFlagKey(flagKey) ?: return false

        val target =
            flagTargetRepositoryDsl.findByFlagIdAndSubject(
                flagId = flag.id!!,
                subjectType = SubjectType.USER,
                subjectId = userId,
            )

        // Target이 없으면 기본값은 허용
        if (target == null) {
            return true
        }

        // permissions JSON 파싱
        val permissions = parsePermissions(target.permissions)
        return permissions[permissionKey] ?: true
    }

    /**
     * 사용자의 모든 Flag 상태 조회 (클라이언트 API용)
     *
     * NOTE: Projection 패턴 사용으로 N+1 쿼리 방지
     * - 1 query: 모든 Flag 조회
     * - 1 query: 사용자의 모든 Target 조회 (JOIN으로 flagKey 포함)
     */
    fun evaluateAllFlags(userId: Long): FlagEvaluationResult {
        val allFlags = flagRepositoryJpa.findAll()

        // Projection으로 flagKey 포함하여 조회 (N+1 방지)
        val targets =
            flagTargetRepositoryDsl
                .findBySubjectWithFlagKey(
                    subjectType = SubjectType.USER,
                    subjectId = userId,
                ).associateBy { it.flagKey }

        val evaluated =
            allFlags.associate { flag ->
                flag.flagKey to evaluateFlag(flag, targets[flag.flagKey]?.enabled)
            }

        val permissionMap = buildPermissionMap(evaluated, targets)

        return FlagEvaluationResult(
            flags = evaluated,
            permissions = permissionMap,
            evaluatedAt = LocalDateTime.now(),
        )
    }

    private fun evaluateFlag(
        flag: FlagEntity,
        targetEnabled: Boolean?,
    ): Boolean {
        if (flag.status == FlagStatus.DISABLED) return false
        if (targetEnabled != null) return targetEnabled
        return flag.targetingType == TargetingType.GLOBAL
    }

    private fun buildPermissionMap(
        evaluatedFlags: Map<String, Boolean>,
        targets: Map<String, FlagTargetWithKeyProjection>,
    ): Map<String, Map<String, Boolean>> {
        val result = mutableMapOf<String, MutableMap<String, Boolean>>()

        targets.forEach { (flagKey, target) ->
            // Flag가 활성화된 경우에만 권한 포함
            if (evaluatedFlags[flagKey] == true && target.permissions != null) {
                val permissions = parsePermissions(target.permissions)
                if (permissions.isNotEmpty()) {
                    result[flagKey] = permissions.toMutableMap()
                }
            }
        }

        return result
    }

    private fun parsePermissions(permissionsJson: String?): Map<String, Boolean> {
        if (permissionsJson.isNullOrBlank()) {
            return emptyMap()
        }
        return try {
            objectMapper.readValue(permissionsJson)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun serializePermissions(permissions: Map<String, Boolean>?): String? {
        if (permissions.isNullOrEmpty()) {
            return null
        }
        return objectMapper.writeValueAsString(permissions)
    }

    // ============= Flag CRUD =============

    @Transactional
    fun createFlag(command: CreateFlagCommand): FlagEntity {
        if (flagRepositoryJpa.existsByFlagKey(command.flagKey)) {
            throw FlagAlreadyExistsException(command.flagKey)
        }

        val entity =
            FlagEntity(
                flagKey = command.flagKey,
                name = command.name,
                description = command.description,
                status = command.status,
                targetingType = command.targetingType,
            )

        val saved = flagRepositoryJpa.save(entity)

        // 캐시 무효화
        flagCachePort.evictFlag(command.flagKey)

        return saved
    }

    @Transactional
    fun updateFlag(
        flagKey: String,
        command: UpdateFlagCommand,
    ): FlagEntity {
        val entity =
            flagRepositoryJpa.findByFlagKey(flagKey)
                ?: throw FlagNotFoundException(flagKey)

        command.name?.let { entity.name = it }
        command.description?.let { entity.description = it }
        command.status?.let { entity.status = it }
        command.targetingType?.let { entity.targetingType = it }

        val saved = flagRepositoryJpa.save(entity)

        // 캐시 무효화
        flagCachePort.evictFlag(flagKey)

        return saved
    }

    @Transactional
    fun deleteFlag(flagKey: String) {
        val entity =
            flagRepositoryJpa.findByFlagKey(flagKey)
                ?: throw FlagNotFoundException(flagKey)

        flagRepositoryJpa.delete(entity)

        // 캐시 무효화
        flagCachePort.evictFlag(flagKey)
    }

    fun getFlag(flagKey: String): FlagEntity? = flagRepositoryJpa.findByFlagKey(flagKey)

    fun getFlagOrThrow(flagKey: String): FlagEntity =
        flagRepositoryJpa.findByFlagKey(flagKey) ?: throw FlagNotFoundException(flagKey)

    fun getAllFlags(): List<FlagEntity> = flagRepositoryJpa.findAll()

    // ============= Target CRUD =============

    @Transactional
    fun setTarget(
        flagKey: String,
        command: SetTargetCommand,
    ): FlagTargetEntity {
        val flag =
            flagRepositoryJpa.findByFlagKey(flagKey)
                ?: throw FlagNotFoundException(flagKey)

        val existing =
            flagTargetRepositoryDsl.findByFlagIdAndSubject(
                flagId = flag.id!!,
                subjectType = command.subjectType,
                subjectId = command.subjectId,
            )

        val entity =
            existing ?: FlagTargetEntity(
                flagId = flag.id!!,
                subjectType = command.subjectType,
                subjectId = command.subjectId,
            )
        entity.enabled = command.enabled
        entity.permissions = serializePermissions(command.permissions)

        val saved = flagTargetRepositoryJpa.save(entity)

        // 캐시 무효화
        flagCachePort.evictTargets(command.subjectType, command.subjectId)

        return saved
    }

    @Transactional
    fun updateTargetPermission(
        flagKey: String,
        command: UpdatePermissionCommand,
    ): FlagTargetEntity {
        val flag =
            flagRepositoryJpa.findByFlagKey(flagKey)
                ?: throw FlagNotFoundException(flagKey)

        val target =
            flagTargetRepositoryDsl.findByFlagIdAndSubject(
                flagId = flag.id!!,
                subjectType = command.subjectType,
                subjectId = command.subjectId,
            ) ?: throw FlagTargetNotFoundException(flagKey, command.subjectType.name, command.subjectId)

        // 기존 permissions 파싱 후 업데이트
        val permissions = parsePermissions(target.permissions).toMutableMap()
        permissions[command.permissionKey] = command.granted
        target.permissions = serializePermissions(permissions)

        val saved = flagTargetRepositoryJpa.save(target)

        // 캐시 무효화
        flagCachePort.evictTargets(command.subjectType, command.subjectId)

        return saved
    }

    @Transactional
    fun removeTarget(
        flagKey: String,
        subjectType: SubjectType,
        subjectId: Long,
    ) {
        val flag =
            flagRepositoryJpa.findByFlagKey(flagKey)
                ?: throw FlagNotFoundException(flagKey)

        val target =
            flagTargetRepositoryDsl.findByFlagIdAndSubject(
                flagId = flag.id!!,
                subjectType = subjectType,
                subjectId = subjectId,
            ) ?: throw FlagTargetNotFoundException(flagKey, subjectType.name, subjectId)

        flagTargetRepositoryJpa.delete(target)

        // 캐시 무효화
        flagCachePort.evictTargets(subjectType, subjectId)
    }

    fun getTargets(flagKey: String): List<FlagTargetEntity> {
        val flag =
            flagRepositoryJpa.findByFlagKey(flagKey)
                ?: throw FlagNotFoundException(flagKey)

        return flagTargetRepositoryJpa.findByFlagId(flag.id!!)
    }
}
