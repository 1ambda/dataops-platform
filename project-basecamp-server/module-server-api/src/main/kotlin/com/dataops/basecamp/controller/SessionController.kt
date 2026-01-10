package com.dataops.basecamp.controller

import com.dataops.basecamp.annotation.NoAudit
import com.dataops.basecamp.domain.repository.user.UserRepositoryJpa
import com.dataops.basecamp.domain.service.TeamService
import com.dataops.basecamp.dto.SessionResponse
import com.dataops.basecamp.dto.UserTeamInfo
import com.dataops.basecamp.dto.team.SetDefaultTeamRequest
import com.dataops.basecamp.util.SecurityContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

/**
 * Session Controller
 *
 * Provides session management endpoints including user info and team context.
 */
@RestController
@Tag(name = "Session", description = "Session management API")
class SessionController(
    private val teamService: TeamService,
    private val userRepositoryJpa: UserRepositoryJpa,
) {
    private val logger = KotlinLogging.logger {}

    @Value("\${app.login-uri:/oauth2/authorization/keycloak}")
    private lateinit var loginUri: String

    /**
     * Get current user information including team memberships.
     *
     * GET /api/session/whoami
     */
    @Operation(
        summary = "Get current user info",
        description = "Returns authenticated user information including team memberships",
    )
    @GetMapping("/api/session/whoami")
    @NoAudit(reason = "Frequent calls, no sensitive information")
    fun whoami(principal: Principal?): SessionResponse {
        val baseResponse = SecurityContext.of(principal)

        // If not authenticated, return base response without team info
        if (!baseResponse.authenticated) {
            return baseResponse
        }

        // Get current user's team memberships
        val currentUserId = SecurityContext.getCurrentUserId()
        if (currentUserId == null) {
            return baseResponse
        }

        val teamsWithRoles = teamService.getTeamsByUserId(currentUserId)
        val teamInfos =
            teamsWithRoles.map { (team, role) ->
                UserTeamInfo(
                    id = team.id!!,
                    name = team.name,
                    displayName = team.displayName,
                    role = role,
                )
            }

        // Get user's default team
        val user = userRepositoryJpa.findByEmail(baseResponse.email)
        val defaultTeamId = user?.defaultTeamId
        val defaultTeamName = defaultTeamId?.let { teamService.getTeam(it)?.name }

        return baseResponse.copy(
            teams = teamInfos,
            defaultTeamId = defaultTeamId,
            defaultTeamName = defaultTeamName,
        )
    }

    /**
     * Set the current user's default team.
     *
     * PUT /api/session/team
     */
    @Operation(
        summary = "Set default team",
        description = "Set the current user's default team context",
    )
    @PutMapping("/api/session/team")
    @PreAuthorize("hasRole('ROLE_USER')")
    fun setDefaultTeam(
        @Valid @RequestBody request: SetDefaultTeamRequest,
    ): ResponseEntity<SessionResponse> {
        logger.info { "Setting default team to: ${request.teamId}" }

        val currentUserId = SecurityContext.getCurrentUserIdOrThrow()
        val currentEmail = SecurityContext.getCurrentUsername()

        // Verify user is member of the team (if teamId is provided)
        if (request.teamId != null) {
            if (!teamService.isMember(request.teamId, currentUserId)) {
                return ResponseEntity.badRequest().build()
            }
        }

        // Update user's default team
        val user = userRepositoryJpa.findByEmail(currentEmail)
        if (user != null) {
            user.defaultTeamId = request.teamId
            userRepositoryJpa.save(user)
        }

        // Return updated session response
        val teamsWithRoles = teamService.getTeamsByUserId(currentUserId)
        val teamInfos =
            teamsWithRoles.map { (team, role) ->
                UserTeamInfo(
                    id = team.id!!,
                    name = team.name,
                    displayName = team.displayName,
                    role = role,
                )
            }

        val defaultTeamName = request.teamId?.let { teamService.getTeam(it)?.name }

        val response =
            SessionResponse(
                authenticated = true,
                userId = user?.id?.toString() ?: "",
                email = currentEmail,
                roles = emptyList(), // Roles would be populated from security context
                teams = teamInfos,
                defaultTeamId = request.teamId,
                defaultTeamName = defaultTeamName,
            )

        return ResponseEntity.ok(response)
    }

    /**
     * Redirect to login page.
     *
     * GET /api/session/login
     */
    @Operation(
        summary = "Login redirect",
        description = "Redirects to the OAuth2 login page",
    )
    @GetMapping("/api/session/login")
    fun login(response: HttpServletResponse) {
        response.sendRedirect(loginUri)
    }
}
