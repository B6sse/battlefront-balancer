package no.battlefront.balancer.ratelimit

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class LoginRateLimitFilterTest {
    @Test
    fun `filter delegates when rate limiting disabled`() {
        val store = mock(LoginRateLimitStore::class.java)
        val filter = LoginRateLimitFilter(store, enabled = false)

        val request = MockHttpServletRequest()
        request.method = "POST"
        request.requestURI = "/api/login"
        request.remoteAddr = "1.1.1.1"
        request.addHeader("X-Forwarded-For", "9.9.9.9, 8.8.8.8")

        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        verify(store, never()).tryAcquire(anyString())
    }

    @Test
    fun `filter delegates when request is not POST login`() {
        val store = mock(LoginRateLimitStore::class.java)
        val filter = LoginRateLimitFilter(store, enabled = true)

        val request = MockHttpServletRequest()
        request.method = "GET"
        request.requestURI = "/api/health"
        request.remoteAddr = "1.1.1.1"

        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        filter.doFilter(request, response, chain)

        verify(chain).doFilter(request, response)
        verify(store, never()).tryAcquire(anyString())
    }

    @Test
    fun `filter allows when under limit`() {
        val store = mock(LoginRateLimitStore::class.java)
        val filter = LoginRateLimitFilter(store, enabled = true)

        val request = MockHttpServletRequest()
        request.method = "POST"
        request.requestURI = "/api/login"
        request.remoteAddr = "1.1.1.1"
        request.addHeader("X-Forwarded-For", "9.9.9.9, 8.8.8.8")

        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        `when`(store.tryAcquire("9.9.9.9")).thenReturn(LoginRateLimitStore.RateLimitResult.Allowed)

        filter.doFilter(request, response, chain)

        verify(store).tryAcquire("9.9.9.9")
        verify(chain).doFilter(request, response)
        assertEquals(200, response.status)
    }

    @Test
    fun `filter returns 429 with Retry-After and JSON when limited`() {
        val store = mock(LoginRateLimitStore::class.java)
        val filter = LoginRateLimitFilter(store, enabled = true)

        val request = MockHttpServletRequest()
        request.method = "POST"
        request.requestURI = "/api/login"
        request.remoteAddr = "1.1.1.1"
        request.addHeader("X-Forwarded-For", "9.9.9.9, 8.8.8.8")

        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        `when`(store.tryAcquire("9.9.9.9")).thenReturn(
            LoginRateLimitStore.RateLimitResult.Limited(retryAfterSeconds = 17),
        )

        filter.doFilter(request, response, chain)

        verify(store).tryAcquire("9.9.9.9")
        verify(chain, never()).doFilter(any(HttpServletRequest::class.java), any())

        assertEquals(429, response.status)
        assertEquals("17", response.getHeader("Retry-After"))
        assertTrue(response.contentType?.contains("application/json") == true)
        assertTrue(response.contentAsString.contains("""{"error":"Too many login attempts. Try again later."}"""))
    }

    @Test
    fun `filter uses remoteAddr as client key when X-Forwarded-For is absent`() {
        val store = mock(LoginRateLimitStore::class.java)
        val filter = LoginRateLimitFilter(store, enabled = true)

        val request = MockHttpServletRequest()
        request.method = "POST"
        request.requestURI = "/api/login"
        request.remoteAddr = "203.0.113.10"
        // no X-Forwarded-For header -> clientKey() else branch (remoteAddr)

        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        `when`(store.tryAcquire("203.0.113.10")).thenReturn(LoginRateLimitStore.RateLimitResult.Allowed)

        filter.doFilter(request, response, chain)

        verify(store).tryAcquire("203.0.113.10")
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `filter uses remoteAddr when X-Forwarded-For is blank`() {
        val store = mock(LoginRateLimitStore::class.java)
        val filter = LoginRateLimitFilter(store, enabled = true)

        val request = MockHttpServletRequest()
        request.method = "POST"
        request.requestURI = "/api/login"
        request.remoteAddr = "198.51.100.2"
        request.addHeader("X-Forwarded-For", "   ")

        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        `when`(store.tryAcquire("198.51.100.2")).thenReturn(LoginRateLimitStore.RateLimitResult.Allowed)

        filter.doFilter(request, response, chain)

        verify(store).tryAcquire("198.51.100.2")
        verify(chain).doFilter(request, response)
    }

    @Test
    fun `filter returns 429 using remoteAddr when limited and no X-Forwarded-For`() {
        val store = mock(LoginRateLimitStore::class.java)
        val filter = LoginRateLimitFilter(store, enabled = true)

        val request = MockHttpServletRequest()
        request.method = "POST"
        request.requestURI = "/api/login"
        request.remoteAddr = "192.0.2.1"

        val response = MockHttpServletResponse()
        val chain = mock(FilterChain::class.java)

        `when`(store.tryAcquire("192.0.2.1")).thenReturn(
            LoginRateLimitStore.RateLimitResult.Limited(retryAfterSeconds = 5),
        )

        filter.doFilter(request, response, chain)

        verify(store).tryAcquire("192.0.2.1")
        verify(chain, never()).doFilter(any(HttpServletRequest::class.java), any())
        assertEquals(429, response.status)
        assertEquals("5", response.getHeader("Retry-After"))
    }
}
