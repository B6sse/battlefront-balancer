package no.battlefront.balancer.controller

import jakarta.servlet.http.HttpServletRequest
import no.battlefront.balancer.dto.CurrentUserDto
import no.battlefront.balancer.dto.LoginRequest
import no.battlefront.balancer.security.AppUserDetails
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthController(
    private val authenticationManager: AuthenticationManager,
) {
    /**
     * Authenticates with username and password, creates a session, and returns the current user.
     * Session cookie is HttpOnly and SameSite=Strict; set SESSION_COOKIE_SECURE=true in production.
     *
     * @param request username and password (JSON).
     * @return 200 with [CurrentUserDto] on success; 401 on invalid credentials.
     */
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        val auth =
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.username, request.password),
            )
        SecurityContextHolder.getContext().authentication = auth
        httpRequest.getSession(true)
        val details = auth.principal as AppUserDetails
        val role =
            details.authorities
                .firstOrNull()
                ?.authority
                ?.removePrefix("ROLE_") ?: ""
        return ResponseEntity.ok(CurrentUserDto(id = details.userId, username = details.username, role = role))
    }

    /**
     * Invalidates the current session. Client should discard the session cookie.
     */
    @PostMapping("/logout")
    fun logout(httpRequest: HttpServletRequest): ResponseEntity<Void> {
        httpRequest.session?.invalidate()
        SecurityContextHolder.clearContext()
        return ResponseEntity.noContent().build()
    }

    /**
     * Returns the currently authenticated user, or 401 if not logged in.
     */
    @GetMapping("/me")
    fun me(
        @AuthenticationPrincipal principal: AppUserDetails?,
    ): ResponseEntity<CurrentUserDto> {
        if (principal == null) return ResponseEntity.status(401).build()
        val role =
            principal.authorities
                .firstOrNull()
                ?.authority
                ?.removePrefix("ROLE_") ?: ""
        return ResponseEntity.ok(CurrentUserDto(id = principal.userId, username = principal.username, role = role))
    }
}
