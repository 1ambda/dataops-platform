package com.github.lambda.controller

import com.github.lambda.dto.SessionResponse
import com.github.lambda.util.SecurityContext
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

/**
 * 세션 컨트롤러
 */
@RestController
class SessionController {
    @Value("\${app.login-uri:/oauth2/authorization/keycloak}")
    private lateinit var loginUri: String

    /**
     * 현재 사용자 정보를 조회합니다.
     */
    @GetMapping("/api/session/whoami")
    fun whoami(principal: Principal?): SessionResponse = SecurityContext.of(principal)

    /**
     * 로그인 페이지로 리다이렉트합니다.
     */
    @GetMapping("/api/session/login")
    fun login(response: HttpServletResponse) {
        response.sendRedirect(loginUri)
    }
}
