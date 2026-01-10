package com.dataops.basecamp.controller

import com.dataops.basecamp.common.constant.CommonConstants
import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.domain.command.resource.CreateResourceShareCommand
import com.dataops.basecamp.domain.command.resource.RevokeResourceShareCommand
import com.dataops.basecamp.domain.command.resource.UpdateResourceShareCommand
import com.dataops.basecamp.domain.entity.resource.TeamResourceShareEntity
import com.dataops.basecamp.domain.repository.team.TeamRepositoryJpa
import com.dataops.basecamp.domain.service.ResourceShareService
import com.dataops.basecamp.domain.service.UserGrantService
import com.dataops.basecamp.dto.resource.CreateResourceShareRequest
import com.dataops.basecamp.dto.resource.ResourceShareDto
import com.dataops.basecamp.dto.resource.ResourceShareSummaryDto
import com.dataops.basecamp.dto.resource.UpdateResourceShareRequest
import com.dataops.basecamp.util.SecurityContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Resource Share REST API Controller
 *
 * Provides endpoints for managing resource shares between teams.
 * Base path: /api/v1/resources/{resourceType}/shares
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/resources/{resourceType}/shares")
@CrossOrigin
@Validated
@Tag(name = "Resource Sharing", description = "Resource sharing between teams API")
@PreAuthorize("hasRole('ROLE_USER')")
class ResourceShareController(
    private val resourceShareService: ResourceShareService,
    private val userGrantService: UserGrantService,
    private val teamRepositoryJpa: TeamRepositoryJpa,
) {
    private val logger = KotlinLogging.logger {}

    // ==================== Share CRUD ====================

    /**
     * List shares for a resource type
     *
     * GET /api/v1/resources/{resourceType}/shares
     */
    @Operation(
        summary = "List resource shares",
        description =
            "List all shares for a specific resource type. " +
                "Filter by ownerTeamId for outgoing shares, sharedWithTeamId for incoming shares.",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping
    fun listShares(
        @Parameter(description = "Resource type")
        @PathVariable resourceType: ShareableResourceType,
        @Parameter(description = "Filter by owner team ID")
        @RequestParam(required = false) ownerTeamId: Long?,
        @Parameter(description = "Filter by shared-with team ID")
        @RequestParam(required = false) sharedWithTeamId: Long?,
    ): ResponseEntity<List<ResourceShareSummaryDto>> {
        logger.info { "Listing shares for $resourceType: ownerTeamId=$ownerTeamId, sharedWithTeamId=$sharedWithTeamId" }

        val shares =
            when {
                ownerTeamId != null ->
                    resourceShareService.listSharesByOwnerTeamAndResourceType(ownerTeamId, resourceType)

                sharedWithTeamId != null ->
                    resourceShareService.listSharesBySharedWithTeamAndResourceType(sharedWithTeamId, resourceType)

                else ->
                    resourceShareService
                        .listSharesByOwnerTeam(
                            SecurityContext.getCurrentUserIdOrThrow(),
                        ).filter { it.resourceType == resourceType }
            }

        val dtos = shares.map { it.toSummaryDto() }

        return ResponseEntity.ok(dtos)
    }

    /**
     * Create a new resource share
     *
     * POST /api/v1/resources/{resourceType}/shares
     */
    @Operation(
        summary = "Create resource share",
        description = "Share a resource with another team",
    )
    @SwaggerApiResponse(responseCode = "201", description = "Share created")
    @PostMapping
    fun createShare(
        @Parameter(description = "Resource type")
        @PathVariable resourceType: ShareableResourceType,
        @Parameter(description = "Owner team ID")
        @RequestParam ownerTeamId: Long,
        @Valid @RequestBody request: CreateResourceShareRequest,
    ): ResponseEntity<ResourceShareDto> {
        val currentUserId = SecurityContext.getCurrentUserIdOrThrow()
        logger.info {
            "Creating share for $resourceType:${request.resourceId} from team $ownerTeamId to team ${request.sharedWithTeamId}"
        }

        val command =
            CreateResourceShareCommand(
                ownerTeamId = ownerTeamId,
                sharedWithTeamId = request.sharedWithTeamId,
                resourceType = resourceType,
                resourceId = request.resourceId,
                permission = request.permission,
                visibleToTeam = request.visibleToTeam,
                grantedBy = currentUserId,
            )

        val share = resourceShareService.createShare(command)

        return ResponseEntity.status(HttpStatus.CREATED).body(share.toDto())
    }

    /**
     * Get share details
     *
     * GET /api/v1/resources/{resourceType}/shares/{shareId}
     */
    @Operation(
        summary = "Get share details",
        description = "Get details of a specific share",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/{shareId}")
    fun getShare(
        @Parameter(description = "Resource type")
        @PathVariable resourceType: ShareableResourceType,
        @PathVariable shareId: Long,
    ): ResponseEntity<ResourceShareDto> {
        logger.info { "Getting share: $shareId" }

        val share = resourceShareService.getShareOrThrow(shareId)

        return ResponseEntity.ok(share.toDto())
    }

    /**
     * Update a share
     *
     * PUT /api/v1/resources/{resourceType}/shares/{shareId}
     */
    @Operation(
        summary = "Update share",
        description = "Update share settings",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Share updated")
    @PutMapping("/{shareId}")
    fun updateShare(
        @Parameter(description = "Resource type")
        @PathVariable resourceType: ShareableResourceType,
        @PathVariable shareId: Long,
        @Valid @RequestBody request: UpdateResourceShareRequest,
    ): ResponseEntity<ResourceShareDto> {
        val currentUserId = SecurityContext.getCurrentUserIdOrThrow()
        logger.info { "Updating share: $shareId" }

        val command =
            UpdateResourceShareCommand(
                shareId = shareId,
                permission = request.permission,
                visibleToTeam = request.visibleToTeam,
                updatedBy = currentUserId,
            )

        val share = resourceShareService.updateShare(command)

        return ResponseEntity.ok(share.toDto())
    }

    /**
     * Revoke a share
     *
     * DELETE /api/v1/resources/{resourceType}/shares/{shareId}
     */
    @Operation(
        summary = "Revoke share",
        description = "Revoke a share. This also deletes all associated user grants.",
    )
    @SwaggerApiResponse(responseCode = "204", description = "Share revoked")
    @DeleteMapping("/{shareId}")
    fun revokeShare(
        @Parameter(description = "Resource type")
        @PathVariable resourceType: ShareableResourceType,
        @PathVariable shareId: Long,
    ): ResponseEntity<Void> {
        val currentUserId = SecurityContext.getCurrentUserIdOrThrow()
        logger.info { "Revoking share: $shareId" }

        val command =
            RevokeResourceShareCommand(
                shareId = shareId,
                revokedBy = currentUserId,
            )

        resourceShareService.revokeShare(command)

        return ResponseEntity.noContent().build()
    }

    // ==================== Mappers ====================

    private fun TeamResourceShareEntity.toDto(): ResourceShareDto {
        val ownerTeam = teamRepositoryJpa.findByIdAndDeletedAtIsNull(this.ownerTeamId)
        val sharedWithTeam = teamRepositoryJpa.findByIdAndDeletedAtIsNull(this.sharedWithTeamId)
        val grantCount = userGrantService.countGrantsByShare(this.id!!).toInt()

        return ResourceShareDto(
            id = this.id!!,
            ownerTeamId = this.ownerTeamId,
            ownerTeamName = ownerTeam?.name,
            sharedWithTeamId = this.sharedWithTeamId,
            sharedWithTeamName = sharedWithTeam?.name,
            resourceType = this.resourceType,
            resourceId = this.resourceId,
            permission = this.permission,
            visibleToTeam = this.visibleToTeam,
            grantedBy = this.grantedBy,
            grantedAt = this.grantedAt,
            grantCount = grantCount,
        )
    }

    private fun TeamResourceShareEntity.toSummaryDto(): ResourceShareSummaryDto {
        val sharedWithTeam = teamRepositoryJpa.findByIdAndDeletedAtIsNull(this.sharedWithTeamId)
        val grantCount = userGrantService.countGrantsByShare(this.id!!).toInt()

        return ResourceShareSummaryDto(
            id = this.id!!,
            sharedWithTeamId = this.sharedWithTeamId,
            sharedWithTeamName = sharedWithTeam?.name,
            resourceId = this.resourceId,
            permission = this.permission,
            visibleToTeam = this.visibleToTeam,
            grantCount = grantCount,
            grantedAt = this.grantedAt,
        )
    }
}
