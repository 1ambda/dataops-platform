package com.dataops.basecamp.controller

import com.dataops.basecamp.common.constant.CommonConstants
import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.domain.command.resource.CreateUserGrantCommand
import com.dataops.basecamp.domain.command.resource.RevokeUserGrantCommand
import com.dataops.basecamp.domain.command.resource.UpdateUserGrantCommand
import com.dataops.basecamp.domain.entity.resource.UserResourceGrantEntity
import com.dataops.basecamp.domain.repository.user.UserRepositoryJpa
import com.dataops.basecamp.domain.service.UserGrantService
import com.dataops.basecamp.dto.resource.CreateUserGrantRequest
import com.dataops.basecamp.dto.resource.UpdateUserGrantRequest
import com.dataops.basecamp.dto.resource.UserGrantDto
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
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * User Grant REST API Controller
 *
 * Provides endpoints for managing user-level grants for shared resources.
 * Base path: /api/v1/resources/{resourceType}/shares/{shareId}/grants
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/resources/{resourceType}/shares/{shareId}/grants")
@CrossOrigin
@Validated
@Tag(name = "User Grants", description = "User-level grant management for shared resources API")
@PreAuthorize("hasRole('ROLE_USER')")
class UserGrantController(
    private val userGrantService: UserGrantService,
    private val userRepositoryJpa: UserRepositoryJpa,
) {
    private val logger = KotlinLogging.logger {}

    // ==================== Grant CRUD ====================

    /**
     * List grants for a share
     *
     * GET /api/v1/resources/{resourceType}/shares/{shareId}/grants
     */
    @Operation(
        summary = "List grants",
        description = "List all user grants for a specific share",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping
    fun listGrants(
        @Parameter(description = "Resource type")
        @PathVariable resourceType: ShareableResourceType,
        @PathVariable shareId: Long,
    ): ResponseEntity<List<UserGrantDto>> {
        logger.info { "Listing grants for share: $shareId" }

        val grants = userGrantService.listGrantsByShare(shareId)
        val dtos = grants.map { it.toDto() }

        return ResponseEntity.ok(dtos)
    }

    /**
     * Create a new user grant
     *
     * POST /api/v1/resources/{resourceType}/shares/{shareId}/grants
     */
    @Operation(
        summary = "Create grant",
        description = "Grant access to a specific user. Permission cannot exceed share permission.",
    )
    @SwaggerApiResponse(responseCode = "201", description = "Grant created")
    @PostMapping
    fun createGrant(
        @Parameter(description = "Resource type")
        @PathVariable resourceType: ShareableResourceType,
        @PathVariable shareId: Long,
        @Valid @RequestBody request: CreateUserGrantRequest,
    ): ResponseEntity<UserGrantDto> {
        val currentUserId = SecurityContext.getCurrentUserIdOrThrow()
        logger.info { "Creating grant for user ${request.userId} on share: $shareId" }

        val command =
            CreateUserGrantCommand(
                shareId = shareId,
                userId = request.userId,
                permission = request.permission,
                grantedBy = currentUserId,
            )

        val grant = userGrantService.createGrant(command)

        return ResponseEntity.status(HttpStatus.CREATED).body(grant.toDto())
    }

    /**
     * Get grant details
     *
     * GET /api/v1/resources/{resourceType}/shares/{shareId}/grants/{grantId}
     */
    @Operation(
        summary = "Get grant details",
        description = "Get details of a specific grant",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/{grantId}")
    fun getGrant(
        @Parameter(description = "Resource type")
        @PathVariable resourceType: ShareableResourceType,
        @PathVariable shareId: Long,
        @PathVariable grantId: Long,
    ): ResponseEntity<UserGrantDto> {
        logger.info { "Getting grant: $grantId" }

        val grant = userGrantService.getGrantOrThrow(grantId)

        return ResponseEntity.ok(grant.toDto())
    }

    /**
     * Update a grant
     *
     * PUT /api/v1/resources/{resourceType}/shares/{shareId}/grants/{grantId}
     */
    @Operation(
        summary = "Update grant",
        description = "Update grant permission. Cannot exceed share permission.",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Grant updated")
    @PutMapping("/{grantId}")
    fun updateGrant(
        @Parameter(description = "Resource type")
        @PathVariable resourceType: ShareableResourceType,
        @PathVariable shareId: Long,
        @PathVariable grantId: Long,
        @Valid @RequestBody request: UpdateUserGrantRequest,
    ): ResponseEntity<UserGrantDto> {
        val currentUserId = SecurityContext.getCurrentUserIdOrThrow()
        logger.info { "Updating grant: $grantId to permission ${request.permission}" }

        val command =
            UpdateUserGrantCommand(
                grantId = grantId,
                permission = request.permission,
                updatedBy = currentUserId,
            )

        val grant = userGrantService.updateGrant(command)

        return ResponseEntity.ok(grant.toDto())
    }

    /**
     * Revoke a grant
     *
     * DELETE /api/v1/resources/{resourceType}/shares/{shareId}/grants/{grantId}
     */
    @Operation(
        summary = "Revoke grant",
        description = "Revoke a user's access grant",
    )
    @SwaggerApiResponse(responseCode = "204", description = "Grant revoked")
    @DeleteMapping("/{grantId}")
    fun revokeGrant(
        @Parameter(description = "Resource type")
        @PathVariable resourceType: ShareableResourceType,
        @PathVariable shareId: Long,
        @PathVariable grantId: Long,
    ): ResponseEntity<Void> {
        val currentUserId = SecurityContext.getCurrentUserIdOrThrow()
        logger.info { "Revoking grant: $grantId" }

        val command =
            RevokeUserGrantCommand(
                grantId = grantId,
                revokedBy = currentUserId,
            )

        userGrantService.revokeGrant(command)

        return ResponseEntity.noContent().build()
    }

    // ==================== Mappers ====================

    private fun UserResourceGrantEntity.toDto(): UserGrantDto {
        val user = userRepositoryJpa.findByIdAndDeletedAtIsNull(this.userId)

        return UserGrantDto(
            id = this.id!!,
            shareId = this.shareId,
            userId = this.userId,
            userEmail = user?.email,
            userName = user?.username,
            permission = this.permission,
            grantedBy = this.grantedBy,
            grantedAt = this.grantedAt,
        )
    }
}
