package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.SubjectType
import com.dataops.basecamp.domain.service.CreateFlagCommand
import com.dataops.basecamp.domain.service.FlagService
import com.dataops.basecamp.domain.service.SetTargetCommand
import com.dataops.basecamp.domain.service.UpdateFlagCommand
import com.dataops.basecamp.domain.service.UpdatePermissionCommand
import com.dataops.basecamp.dto.flag.CreateFlagRequest
import com.dataops.basecamp.dto.flag.FlagDto
import com.dataops.basecamp.dto.flag.FlagEvaluationDto
import com.dataops.basecamp.dto.flag.FlagSingleEvaluationDto
import com.dataops.basecamp.dto.flag.FlagTargetDto
import com.dataops.basecamp.dto.flag.SetTargetRequest
import com.dataops.basecamp.dto.flag.UpdateFlagRequest
import com.dataops.basecamp.dto.flag.UpdateTargetPermissionRequest
import com.dataops.basecamp.util.SecurityContext
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

/**
 * Feature Flag REST API Controller
 *
 * Feature Flag 관리 및 평가 API를 제공합니다.
 *
 * 관리 API (ADMIN):
 * - GET /api/v1/flags                                      전체 Flag 목록
 * - GET /api/v1/flags/{key}                                Flag 상세
 * - POST /api/v1/flags                                     Flag 생성
 * - PUT /api/v1/flags/{key}                                Flag 수정
 * - DELETE /api/v1/flags/{key}                             Flag 삭제
 * - GET /api/v1/flags/{key}/targets                        Target 목록
 * - POST /api/v1/flags/{key}/targets                       Target 설정
 * - PUT /api/v1/flags/{key}/targets/permissions            Target Permission 수정
 * - DELETE /api/v1/flags/{key}/targets/{subjectType}/{subjectId}  Target 제거
 *
 * 클라이언트 API (ALL):
 * - GET /api/v1/flags/evaluate                             현재 사용자의 모든 Flag 상태
 * - GET /api/v1/flags/evaluate/{key}                       특정 Flag 상태
 */
@RestController
@RequestMapping("/api/v1/flags")
@Validated
class FlagController(
    private val flagService: FlagService,
) {
    // ============= 클라이언트 API (ALL) =============

    /**
     * 현재 사용자의 모든 Flag 상태 조회
     */
    @GetMapping("/evaluate")
    fun evaluateAllFlags(): ResponseEntity<FlagEvaluationDto> {
        val userId = SecurityContext.getCurrentUserIdOrThrow()
        val result = flagService.evaluateAllFlags(userId)
        return ResponseEntity.ok(FlagEvaluationDto.from(result))
    }

    /**
     * 특정 Flag 상태 조회
     */
    @GetMapping("/evaluate/{key}")
    fun evaluateSingleFlag(
        @PathVariable key: String,
    ): ResponseEntity<FlagSingleEvaluationDto> {
        val userId = SecurityContext.getCurrentUserIdOrThrow()
        val enabled = flagService.isEnabled(key, userId)
        return ResponseEntity.ok(
            FlagSingleEvaluationDto(
                flagKey = key,
                enabled = enabled,
                evaluatedAt = LocalDateTime.now(),
            ),
        )
    }

    // ============= 관리 API (ADMIN) - Flag CRUD =============

    /**
     * 전체 Flag 목록 조회
     */
    @GetMapping
    fun getAllFlags(): ResponseEntity<List<FlagDto>> {
        val flags = flagService.getAllFlags()
        return ResponseEntity.ok(flags.map { FlagDto.from(it) })
    }

    /**
     * Flag 상세 조회
     */
    @GetMapping("/{key}")
    fun getFlag(
        @PathVariable key: String,
    ): ResponseEntity<FlagDto> {
        val flag = flagService.getFlagOrThrow(key)
        return ResponseEntity.ok(FlagDto.from(flag))
    }

    /**
     * Flag 생성
     */
    @PostMapping
    fun createFlag(
        @Valid @RequestBody request: CreateFlagRequest,
    ): ResponseEntity<FlagDto> {
        val command =
            CreateFlagCommand(
                flagKey = request.flagKey,
                name = request.name,
                description = request.description,
                status = request.status,
                targetingType = request.targetingType,
            )
        val flag = flagService.createFlag(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(FlagDto.from(flag))
    }

    /**
     * Flag 수정
     */
    @PutMapping("/{key}")
    fun updateFlag(
        @PathVariable key: String,
        @Valid @RequestBody request: UpdateFlagRequest,
    ): ResponseEntity<FlagDto> {
        val command =
            UpdateFlagCommand(
                name = request.name,
                description = request.description,
                status = request.status,
                targetingType = request.targetingType,
            )
        val flag = flagService.updateFlag(key, command)
        return ResponseEntity.ok(FlagDto.from(flag))
    }

    /**
     * Flag 삭제
     */
    @DeleteMapping("/{key}")
    fun deleteFlag(
        @PathVariable key: String,
    ): ResponseEntity<Void> {
        flagService.deleteFlag(key)
        return ResponseEntity.noContent().build()
    }

    // ============= 관리 API (ADMIN) - Target CRUD =============

    /**
     * Target 목록 조회
     */
    @GetMapping("/{key}/targets")
    fun getTargets(
        @PathVariable key: String,
    ): ResponseEntity<List<FlagTargetDto>> {
        val targets = flagService.getTargets(key)
        return ResponseEntity.ok(targets.map { FlagTargetDto.from(it, key) })
    }

    /**
     * Target 설정 (Override + Permission 통합)
     */
    @PostMapping("/{key}/targets")
    fun setTarget(
        @PathVariable key: String,
        @Valid @RequestBody request: SetTargetRequest,
    ): ResponseEntity<FlagTargetDto> {
        val command =
            SetTargetCommand(
                subjectType = request.subjectType,
                subjectId = request.subjectId,
                enabled = request.enabled,
                permissions = request.permissions,
            )
        val target = flagService.setTarget(key, command)
        return ResponseEntity.status(HttpStatus.CREATED).body(FlagTargetDto.from(target, key))
    }

    /**
     * Target Permission 수정
     */
    @PutMapping("/{key}/targets/permissions")
    fun updateTargetPermission(
        @PathVariable key: String,
        @Valid @RequestBody request: UpdateTargetPermissionRequest,
    ): ResponseEntity<FlagTargetDto> {
        val command =
            UpdatePermissionCommand(
                subjectType = request.subjectType,
                subjectId = request.subjectId,
                permissionKey = request.permissionKey,
                granted = request.granted,
            )
        val target = flagService.updateTargetPermission(key, command)
        return ResponseEntity.ok(FlagTargetDto.from(target, key))
    }

    /**
     * Target 제거
     */
    @DeleteMapping("/{key}/targets/{subjectType}/{subjectId}")
    fun removeTarget(
        @PathVariable key: String,
        @PathVariable subjectType: SubjectType,
        @PathVariable subjectId: Long,
    ): ResponseEntity<Void> {
        flagService.removeTarget(key, subjectType, subjectId)
        return ResponseEntity.noContent().build()
    }
}
