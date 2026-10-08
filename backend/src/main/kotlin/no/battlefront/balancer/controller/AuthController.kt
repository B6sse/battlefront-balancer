package no.battlefront.balancer.controller

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import no.battlefront.balancer.dto.CurrentUserDto
import no.battlefront.balancer.dto.LoginRequest
import no.battlefront.balancer.dto.LoginResponse
import no.battlefront.balancer.dto.TwoFactorCodeRequest
import no.battlefront.balancer.dto.TwoFactorSetupDto
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.AppUserDetails
import no.battlefront.balancer.service.TwoFactorService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Login, logout and the current user.
 *
 * Supervisors are logged in with username and password. Admins and editors also need a code from an authenticator
 * app: after the password step their session only holds a *pending* login (not authenticated), which becomes a real
 * login after POST /api/login/2fa, or after setting up the app via /api/login/2fa/setup the first time. A pending
 * login expires after [PENDING_TTL_SECONDS] or [MAX_CODE_ATTEMPTS] wrong codes.
 */
@RestController
@RequestMapping("/api")
class AuthController(
    private val authenticationManager: AuthenticationManager,
    private val userRepository: UserRepository,
    private val twoFactorService: TwoFactorService,
) {
    /**
     * Checks username and password. Returns status "OK" with the user (logged in), or "TOTP_REQUIRED" /
     * "TOTP_SETUP_REQUIRED" when the account needs two-factor authentication. 401 on wrong credentials.
     */
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ResponseEntity<LoginResponse> {
        val auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken(request.username, request.password))
        val user = userRepository.findById((auth.principal as AppUserDetails).userId).orElseThrow()
        if (!twoFactorService.isRequired(user)) {
            return ResponseEntity.ok(LoginResponse(LoginResponse.OK, completeLogin(user, httpRequest, httpResponse)))
        }

        // Start from a fresh session so nothing from an earlier login or attempt carries over
        httpRequest.getSession(false)?.invalidate()
        val session = httpRequest.getSession(true)
        session.setAttribute(PENDING_USER_ID, user.id)
        session.setAttribute(PENDING_SINCE, Instant.now().epochSecond)
        session.setAttribute(PENDING_ATTEMPTS, 0)
        val status = if (user.totpEnabled) LoginResponse.TOTP_REQUIRED else LoginResponse.TOTP_SETUP_REQUIRED
        return ResponseEntity.ok(LoginResponse(status))
    }

    /**
     * Second step for admins and editors: a code from the authenticator app or a recovery code.
     */
    @PostMapping("/login/2fa")
    fun verifyCode(
        @RequestBody request: TwoFactorCodeRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ResponseEntity<Any> {
        val (session, user) = pendingLogin(httpRequest) ?: return expired()
        if (!user.totpEnabled) return badRequest("Set up the authenticator app first")
        if (!twoFactorService.verifyLoginCode(user.id, request.code)) return wrongCode(session)
        return ResponseEntity.ok(LoginResponse(LoginResponse.OK, completeLogin(user, httpRequest, httpResponse)))
    }

    /**
     * First login of an admin or editor: returns a new secret for the authenticator app (the same one on repeated
     * calls during this login).
     */
    @PostMapping("/login/2fa/setup")
    fun startSetup(httpRequest: HttpServletRequest): ResponseEntity<Any> {
        val (session, user) = pendingLogin(httpRequest) ?: return expired()
        if (user.totpEnabled) return badRequest("Two-factor authentication is already set up")
        val secret =
            session.getAttribute(SETUP_SECRET) as String? ?: twoFactorService.newSecret().also { session.setAttribute(SETUP_SECRET, it) }
        return ResponseEntity.ok(TwoFactorSetupDto(secret, twoFactorService.otpauthUri(user.username, secret)))
    }

    /**
     * Confirms the authenticator app with a code, turns on two-factor login, logs in, and returns recovery codes
     * (shown once).
     */
    @PostMapping("/login/2fa/setup/confirm")
    fun confirmSetup(
        @RequestBody request: TwoFactorCodeRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): ResponseEntity<Any> {
        val (session, user) = pendingLogin(httpRequest) ?: return expired()
        if (user.totpEnabled) return badRequest("Two-factor authentication is already set up")
        val secret = session.getAttribute(SETUP_SECRET) as String? ?: return badRequest("Start the setup first")
        val recoveryCodes = twoFactorService.enable(user.id, secret, request.code) ?: return wrongCode(session)
        val loggedIn = completeLogin(user, httpRequest, httpResponse)
        return ResponseEntity.ok(LoginResponse(LoginResponse.OK, loggedIn, recoveryCodes))
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
        return ResponseEntity.ok(principal.toDto())
    }

    /** Stores the authenticated user in a session with a new id (protects against session fixation). */
    private fun completeLogin(
        user: User,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): CurrentUserDto {
        val session = httpRequest.getSession(true)
        PENDING_ATTRIBUTES.forEach { session.removeAttribute(it) }
        httpRequest.changeSessionId()

        val details = AppUserDetails(user)
        val context = SecurityContextHolder.createEmptyContext()
        context.authentication = UsernamePasswordAuthenticationToken.authenticated(details, null, details.authorities)
        SecurityContextHolder.setContext(context)
        HttpSessionSecurityContextRepository().saveContext(context, httpRequest, httpResponse)
        return details.toDto()
    }

    /** The session and user of an unexpired pending two-factor login, or null. */
    private fun pendingLogin(httpRequest: HttpServletRequest): Pair<HttpSession, User>? {
        val session = httpRequest.getSession(false) ?: return null
        val userId = session.getAttribute(PENDING_USER_ID) as Long? ?: return null
        val since = session.getAttribute(PENDING_SINCE) as Long? ?: return null
        if (Instant.now().epochSecond - since > PENDING_TTL_SECONDS) {
            PENDING_ATTRIBUTES.forEach { session.removeAttribute(it) }
            return null
        }
        val user = userRepository.findById(userId).orElse(null) ?: return null
        return session to user
    }

    private fun wrongCode(session: HttpSession): ResponseEntity<Any> {
        val attempts = (session.getAttribute(PENDING_ATTEMPTS) as Int? ?: 0) + 1
        if (attempts >= MAX_CODE_ATTEMPTS) {
            PENDING_ATTRIBUTES.forEach { session.removeAttribute(it) }
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("message" to "Too many wrong codes. Log in again."))
        }
        session.setAttribute(PENDING_ATTEMPTS, attempts)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("message" to "Invalid code"))
    }

    private fun expired(): ResponseEntity<Any> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("message" to "Your login has expired. Log in again."))

    private fun badRequest(message: String): ResponseEntity<Any> = ResponseEntity.badRequest().body(mapOf("message" to message))

    private fun AppUserDetails.toDto() =
        CurrentUserDto(
            id = userId,
            username = username,
            role = authorities.firstOrNull()?.authority?.removePrefix("ROLE_") ?: "",
        )

    private companion object {
        const val PENDING_USER_ID = "auth.pending.userId"
        const val PENDING_SINCE = "auth.pending.since"
        const val PENDING_ATTEMPTS = "auth.pending.attempts"
        const val SETUP_SECRET = "auth.pending.setupSecret"
        val PENDING_ATTRIBUTES = listOf(PENDING_USER_ID, PENDING_SINCE, PENDING_ATTEMPTS, SETUP_SECRET)

        const val PENDING_TTL_SECONDS = 300L
        const val MAX_CODE_ATTEMPTS = 5
    }
}
