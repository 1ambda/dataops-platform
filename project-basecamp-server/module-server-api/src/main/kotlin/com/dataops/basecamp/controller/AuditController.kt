package com.dataops.basecamp.controller

import com.dataops.basecamp.annotation.NoAudit
import com.dataops.basecamp.common.enums.AuditAction
import com.dataops.basecamp.common.enums.AuditResource
import com.dataops.basecamp.common.exception.ResourceNotFoundException
import com.dataops.basecamp.domain.service.AuditService
import com.dataops.basecamp.dto.audit.AuditLogDto
import com.dataops.basecamp.dto.audit.AuditStatsDto
import com.dataops.basecamp.dto.audit.toDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

/**
 * Audit Management Controller
 *
 * Provides API endpoints for audit log management.
 * All endpoints are excluded from audit logging to prevent recursive logging.
 */
@RestController
@RequestMapping("/api/v1/management/audit")
@Validated
@Tag(name = "Audit Management", description = "Audit log management API")
@NoAudit(reason = "Audit management endpoints should not be audited")
class AuditController(
    private val auditService: AuditService,
) {
    /**
     * List audit logs with filtering
     *
     * GET /api/v1/management/audit/logs
     */
    @GetMapping("/logs")
    @Operation(summary = "List audit logs", description = "Search and list audit logs with filtering and pagination")
    fun listLogs(
        @RequestParam(required = false) userId: String?,
        @RequestParam(required = false) action: AuditAction?,
        @RequestParam(required = false) resource: AuditResource?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        startDate: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        endDate: LocalDateTime?,
        @PageableDefault(size = 20, sort = ["createdAt"], direction = Sort.Direction.DESC)
        pageable: Pageable,
    ): ResponseEntity<Page<AuditLogDto>> {
        val logs = auditService.searchLogs(userId, action, resource, startDate, endDate, pageable)
        return ResponseEntity.ok(logs.map { it.toDto() })
    }

    /**
     * Get audit log detail
     *
     * GET /api/v1/management/audit/logs/{id}
     */
    @GetMapping("/logs/{id}")
    @Operation(summary = "Get audit log detail", description = "Retrieve a specific audit log by ID")
    fun getLog(
        @PathVariable id: Long,
    ): ResponseEntity<AuditLogDto> {
        val log =
            auditService.findById(id)
                ?: throw ResourceNotFoundException("AuditLog", id)
        return ResponseEntity.ok(log.toDto())
    }

    /**
     * Get audit statistics
     *
     * GET /api/v1/management/audit/stats
     */
    @GetMapping("/stats")
    @Operation(summary = "Get audit statistics", description = "Retrieve audit statistics for a given time period")
    fun getStats(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        startDate: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        endDate: LocalDateTime?,
    ): ResponseEntity<AuditStatsDto> {
        val stats = auditService.getStats(startDate, endDate)
        return ResponseEntity.ok(stats.toDto())
    }
}
