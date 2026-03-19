package no.battlefront.balancer.security

import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException

class SecurityTest {
    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `AppUserDetails exposes id, username and ROLE authority`() {
        val user = User(id = 99L, username = "alice", password = "pw", role = "admin")
        val details = AppUserDetails(user)

        assertEquals(99L, details.userId)
        assertEquals("alice", details.username)
        assertEquals("pw", details.password) // exercises AppUserDetails.getPassword()
        assertTrue(details.authorities.any { it.authority == "ROLE_admin" })

        // sanity checks for UserDetails contract
        assertTrue(details.isEnabled)
        assertTrue(details.isAccountNonExpired)
        assertTrue(details.isAccountNonLocked)
        assertTrue(details.isCredentialsNonExpired)
    }

    @Test
    fun `AppUserDetailsService loads user by username`() {
        val repo = mock(UserRepository::class.java)
        val service = AppUserDetailsService(repo)

        val user = User(id = 1L, username = "bob", password = "pw", role = "supervisor")
        `when`(repo.findByUsername("bob")).thenReturn(user)

        val loaded: UserDetails = service.loadUserByUsername("bob")
        assertInstanceOf(AppUserDetails::class.java, loaded)
        val app = loaded as AppUserDetails
        assertEquals(1L, app.userId)
        assertEquals("bob", app.username)
        assertTrue(app.authorities.any { it.authority == "ROLE_supervisor" })
    }

    @Test
    fun `AppUserDetailsService throws UsernameNotFoundException when user missing`() {
        val repo = mock(UserRepository::class.java)
        val service = AppUserDetailsService(repo)

        `when`(repo.findByUsername(anyString())).thenReturn(null)

        assertThrows<UsernameNotFoundException> { service.loadUserByUsername("missing") }
    }

    @Test
    fun `CurrentUserService returns null when not authenticated`() {
        SecurityContextHolder.clearContext()
        val service = CurrentUserService()
        assertNull(service.currentUserId())
    }

    @Test
    fun `CurrentUserService returns null when principal is not AppUserDetails`() {
        val service = CurrentUserService()
        val token = UsernamePasswordAuthenticationToken("not-app-user", null, emptyList<GrantedAuthority>())
        SecurityContextHolder.getContext().authentication = token

        assertNull(service.currentUserId())
    }

    @Test
    fun `CurrentUserService returns userId when principal is AppUserDetails`() {
        val service = CurrentUserService()
        val user = User(id = 123L, username = "u", password = "pw", role = "admin")
        val principal = AppUserDetails(user)
        val token = UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        SecurityContextHolder.getContext().authentication = token

        assertEquals(123L, service.currentUserId())
    }
}
