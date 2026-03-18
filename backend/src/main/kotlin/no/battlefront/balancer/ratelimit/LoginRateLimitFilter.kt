package no.battlefront.balancer.ratelimit

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Rate-limits POST /api/login per client IP to reduce brute-force and abuse.
 *
 * When the limit is exceeded, responds with 429 Too Many Requests and a [Retry-After] header.
 * Client IP is taken from [X-Forwarded-For] (first hop) when present (e.g. behind reverse proxy),
 * otherwise from [HttpServletRequest.getRemoteAddr].
 */
@Component
@Order(0)
class LoginRateLimitFilter(
    private val loginRateLimitStore: LoginRateLimitStore,
    @param:Value($$"${app.rate-limit.login.enabled:true}") private val enabled: Boolean,
) : OncePerRequestFilter() {
    /**
     * Runs the filter: for POST /api/login, checks rate limit per client; otherwise delegates to the chain.
     * When rate limit is exceeded, writes 429 with [Retry-After] and a JSON error body and does not call the chain.
     *
     * @param request the HTTP request
     * @param response the HTTP response (written to when rate limited)
     * @param filterChain the chain to call when the request is allowed
     */
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (!enabled || !isLoginRequest(request)) {
            filterChain.doFilter(request, response)
            return
        }
        val clientKey = clientKey(request)
        when (val result = loginRateLimitStore.tryAcquire(clientKey)) {
            is LoginRateLimitStore.RateLimitResult.Allowed -> filterChain.doFilter(request, response)
            is LoginRateLimitStore.RateLimitResult.Limited -> {
                response.status = HttpStatus.TOO_MANY_REQUESTS.value()
                response.addHeader("Retry-After", result.retryAfterSeconds.toString())
                response.contentType = "application/json"
                response.characterEncoding = "UTF-8"
                response.writer.write("""{"error":"Too many login attempts. Try again later."}""")
            }
        }
    }

    /**
     * Returns whether the request is a POST to the login endpoint (and thus subject to rate limiting).
     *
     * @param request the HTTP request to check
     * @return true if the request is POST /api/login, false otherwise
     */
    private fun isLoginRequest(request: HttpServletRequest): Boolean =
        "POST" == request.method && request.requestURI?.endsWith("/api/login") == true

    /**
     * Derives a client identifier for rate limiting: first hop from X-Forwarded-For when present, else remote address.
     *
     * @param request the HTTP request
     * @return the client key (IP or forwarded IP) used as the rate-limit bucket key
     */
    private fun clientKey(request: HttpServletRequest): String {
        val forwarded = request.getHeader("X-Forwarded-For")
        return if (!forwarded.isNullOrBlank()) {
            forwarded.split(",").firstOrNull()?.trim() ?: request.remoteAddr
        } else {
            request.remoteAddr
        }
    }
}
