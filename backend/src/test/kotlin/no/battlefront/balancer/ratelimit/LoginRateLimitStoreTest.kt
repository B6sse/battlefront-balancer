package no.battlefront.balancer.ratelimit

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * JUnit test class for [LoginRateLimitStore].
 */
@Tag("LoginRateLimitStore")
class LoginRateLimitStoreTest {
    /**
     * Test that the constructor throws when maxPerMinute is zero.
     *
     * 1. Act: Instantiate LoginRateLimitStore with maxPerMinute = 0.
     * 2. Assert: Verify IllegalArgumentException is thrown.
     */
    @Test
    fun `constructor throws when maxPerMinute is zero`() {
        assertThrows<IllegalArgumentException> { LoginRateLimitStore(0) }
    }

    /**
     * Test that the constructor throws when maxPerMinute is negative.
     *
     * 1. Act: Instantiate LoginRateLimitStore with maxPerMinute = -1.
     * 2. Assert: Verify IllegalArgumentException is thrown.
     */
    @Test
    fun `constructor throws when maxPerMinute is negative`() {
        assertThrows<IllegalArgumentException> { LoginRateLimitStore(-1) }
    }

    /**
     * Test that tryAcquire returns Allowed for each attempt when under the per-minute limit.
     *
     * 1. Arrange: Create store with maxPerMinute = 5.
     * 2. Act: Call tryAcquire 5 times for the same client key.
     * 3. Assert: Verify each result is Allowed.
     */
    @Test
    fun `tryAcquire returns Allowed when under limit`() {
        val store = LoginRateLimitStore(maxPerMinute = 5)
        repeat(5) {
            val result = store.tryAcquire("192.168.1.1")
            assertInstanceOf(LoginRateLimitStore.RateLimitResult.Allowed::class.java, result)
        }
    }

    /**
     * Test that tryAcquire returns Limited with positive retryAfterSeconds when over the limit.
     *
     * 1. Arrange: Create store with maxPerMinute = 2.
     * 2. Act: Call tryAcquire 3 times for the same client key.
     * 3. Assert: Verify third result is Limited and retryAfterSeconds >= 1.
     */
    @Test
    fun `tryAcquire returns Limited when over limit with positive retryAfterSeconds`() {
        val store = LoginRateLimitStore(maxPerMinute = 2)
        store.tryAcquire("10.0.0.1")
        store.tryAcquire("10.0.0.1")
        val result = store.tryAcquire("10.0.0.1")
        assertInstanceOf(LoginRateLimitStore.RateLimitResult.Limited::class.java, result)
        val limited = result as LoginRateLimitStore.RateLimitResult.Limited
        assertTrue(limited.retryAfterSeconds >= 1)
    }

    /**
     * Test that different client keys are rate limited independently.
     *
     * 1. Arrange: Create store with maxPerMinute = 1.
     * 2. Act: Call tryAcquire once for client-A and once for client-B.
     * 3. Assert: Verify both results are Allowed.
     */
    @Test
    fun `different client keys are rate limited independently`() {
        val store = LoginRateLimitStore(maxPerMinute = 1)
        val r1 = store.tryAcquire("client-A")
        val r2 = store.tryAcquire("client-B")
        assertInstanceOf(LoginRateLimitStore.RateLimitResult.Allowed::class.java, r1)
        assertInstanceOf(LoginRateLimitStore.RateLimitResult.Allowed::class.java, r2)
    }

    @Test
    fun `tryAcquire resets the window when elapsed reaches WINDOW_MILLIS`() {
        val store = LoginRateLimitStore(maxPerMinute = 1)
        val key = "client-reset"

        // Create an initial window entry.
        store.tryAcquire(key)

        val windowsField = store.javaClass.getDeclaredField("windows")
        windowsField.isAccessible = true

        @Suppress("UNCHECKED_CAST")
        val windows =
            windowsField.get(store) as ConcurrentHashMap<String, AtomicReference<Any>>

        val atomicRef = windows[key] ?: error("Window entry not found for key=$key")

        // Access the private nested data class `Window(startMillis: Long, count: Int)`.
        val windowClass = store.javaClass.declaredClasses.first { it.simpleName == "Window" }
        val ctor =
            windowClass.getDeclaredConstructor(
                java.lang.Long.TYPE,
                Integer.TYPE,
            )
        ctor.isAccessible = true

        val now = System.currentTimeMillis()
        // Force elapsed >= 60s window to exercise the `Window(now, 1)` branch.
        val oldStartMillis = now - 61_000L
        val overLimitCount = 10 // would be Limited if the reset branch wasn't taken

        val forcedOldWindow = ctor.newInstance(oldStartMillis, overLimitCount)
        atomicRef.set(forcedOldWindow)

        val result = store.tryAcquire(key)
        assertInstanceOf(LoginRateLimitStore.RateLimitResult.Allowed::class.java, result)
    }
}
