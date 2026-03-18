package no.battlefront.balancer.ratelimit

import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
}
