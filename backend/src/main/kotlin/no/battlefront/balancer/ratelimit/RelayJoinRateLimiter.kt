package no.battlefront.balancer.ratelimit

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Limits POST /api/relay/sessions/{sid}/join per client IP, so session passwords cannot be guessed quickly.
 * Same fixed one-minute window and client IP rule (first hop of X-Forwarded-For, else the remote address) as the
 * login limit.
 */
@Component
class RelayJoinRateLimiter(
    @Value("\${app.rate-limit.relay-join.max-per-minute:10}") maxPerMinute: Int,
) {
    private val store = LoginRateLimitStore(maxPerMinute)

    /**
     * Records a join attempt.
     *
     * @return null if allowed, otherwise the seconds until the client may try again
     */
    fun retryAfterSeconds(request: HttpServletRequest): Int? {
        val forwarded = request.getHeader("X-Forwarded-For")
        val clientKey =
            forwarded
                ?.split(",")
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() } ?: request.remoteAddr
        return when (val result = store.tryAcquire(clientKey)) {
            is LoginRateLimitStore.RateLimitResult.Allowed -> null
            is LoginRateLimitStore.RateLimitResult.Limited -> result.retryAfterSeconds
        }
    }
}
