package no.battlefront.balancer.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import no.battlefront.balancer.service.HostTokenService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Authenticates requests carrying `Authorization: Bearer <host token>` as a [HostPrincipal] with `ROLE_host`.
 *
 * Requests without a bearer header pass through untouched. An invalid or revoked token is rejected with 401
 * right away, even on public endpoints, so a misconfigured host notices immediately. The authentication is
 * not stored in the session.
 *
 * Not a Spring bean on purpose: Spring Boot would otherwise also register it as a servlet filter.
 */
class HostTokenAuthenticationFilter(
    private val hostTokenService: HostTokenService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header == null || !header.startsWith(BEARER_PREFIX, ignoreCase = true)) {
            filterChain.doFilter(request, response)
            return
        }

        val principal = hostTokenService.authenticate(header.substring(BEARER_PREFIX.length).trim())
        if (principal == null) {
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = "application/json"
            response.characterEncoding = "UTF-8"
            response.writer.write("""{"message":"Invalid or revoked host token"}""")
            return
        }

        val context = SecurityContextHolder.createEmptyContext()
        context.authentication =
            UsernamePasswordAuthenticationToken.authenticated(principal, null, listOf(SimpleGrantedAuthority(ROLE_HOST)))
        SecurityContextHolder.setContext(context)
        filterChain.doFilter(request, response)
    }

    companion object {
        const val ROLE_HOST = "ROLE_host"
        private const val BEARER_PREFIX = "Bearer "
    }
}
