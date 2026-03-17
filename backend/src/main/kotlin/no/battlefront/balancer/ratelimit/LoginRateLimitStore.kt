package no.battlefront.balancer.ratelimit

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe, in-memory fixed-window rate limit for login attempts per client IP.
 *
 * Each IP is allowed at most [maxPerMinute] attempts per 60-second window. Windows are aligned
 * to the first request from that IP. Old entries are evicted when a new window is started.
 *
 * @param maxPerMinute maximum attempts per IP per minute; must be positive
 */
class LoginRateLimitStore(private val maxPerMinute: Int) {

    init {
        require(maxPerMinute > 0) { "maxPerMinute must be positive" }
    }

    private data class Window(val startMillis: Long, val count: Int)

    private val windows = ConcurrentHashMap<String, AtomicReference<Window>>()

    private companion object {
        const val WINDOW_SECONDS = 60L
        const val WINDOW_MILLIS = WINDOW_SECONDS * 1000
    }

    /**
     * Records an attempt from [clientKey] (e.g. IP). Call before performing login.
     *
     * @param clientKey identifier for the client (e.g. IP address)
     * @return [RateLimitResult.Allowed] if under the limit, or [RateLimitResult.Limited] with
     *         seconds until the current window ends
     */
    fun tryAcquire(clientKey: String): RateLimitResult {
        val now = System.currentTimeMillis()
        val ref = windows.getOrPut(clientKey) { AtomicReference(Window(now, 0)) }
        val updated = ref.updateAndGet { current ->
            val elapsed = now - current.startMillis
            if (elapsed >= WINDOW_MILLIS) {
                Window(now, 1)
            } else {
                Window(current.startMillis, (current.count + 1).coerceAtMost(maxPerMinute + 1))
            }
        }
        val overLimit = updated.count > maxPerMinute
        return if (overLimit) {
            val windowEndSeconds = ((updated.startMillis + WINDOW_MILLIS - now) / 1000).coerceAtLeast(1)
            RateLimitResult.Limited(retryAfterSeconds = windowEndSeconds.toInt())
        } else {
            RateLimitResult.Allowed
        }
    }

    sealed interface RateLimitResult {
        data object Allowed : RateLimitResult
        data class Limited(val retryAfterSeconds: Int) : RateLimitResult
    }
}
