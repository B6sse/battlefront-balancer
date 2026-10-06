package no.battlefront.balancer.service

import no.battlefront.balancer.dto.HostTokenCreateRequest
import no.battlefront.balancer.model.HostToken
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.HostTokenRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.CurrentUserService
import no.battlefront.balancer.security.HostPrincipal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDateTime
import java.util.Optional

/**
 * JUnit test class for [HostTokenService].
 */
@Tag("HostTokenService")
class HostTokenServiceTest {
    private val hostTokenRepository: HostTokenRepository = mock(HostTokenRepository::class.java)
    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val currentUserService: CurrentUserService = mock(CurrentUserService::class.java)
    private val service = HostTokenService(hostTokenRepository, userRepository, currentUserService)

    private val supervisor = User(id = 3L, username = "purpoz", password = "x", role = "supervisor")

    @Test
    fun `createToken returns a random base64url token and stores only its hash`() {
        `when`(userRepository.findById(3L)).thenReturn(Optional.of(supervisor))
        `when`(currentUserService.currentUserId()).thenReturn(1L)
        `when`(hostTokenRepository.save(any(HostToken::class.java))).thenAnswer { it.getArgument(0) }

        val created = service.createToken(HostTokenCreateRequest(name = " Purpoz PC ", userId = 3L))
        val second = service.createToken(HostTokenCreateRequest(name = "Other", userId = 3L))

        assertTrue(Regex("^[A-Za-z0-9_-]{43}$").matches(created.token), created.token)
        assertTrue(created.token != second.token)
        assertEquals("Purpoz PC", created.name)
        assertEquals("purpoz", created.username)

        val captor = ArgumentCaptor.forClass(HostToken::class.java)
        verify(hostTokenRepository, org.mockito.Mockito.times(2)).save(captor.capture())
        val stored = captor.allValues.first()
        assertEquals(service.hash(created.token), stored.tokenHash)
        assertEquals(64, stored.tokenHash.length)
        assertEquals(3L, stored.userId)
        assertEquals(1L, stored.createdBy)
    }

    @Test
    fun `createToken validates name and owner role`() {
        val editor = User(id = 4L, username = "ed", password = "x", role = "editor")
        `when`(userRepository.findById(4L)).thenReturn(Optional.of(editor))
        `when`(userRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<IllegalArgumentException> { service.createToken(HostTokenCreateRequest(name = "  ", userId = 3L)) }
        assertThrows<IllegalArgumentException> { service.createToken(HostTokenCreateRequest(name = "x".repeat(101), userId = 3L)) }
        assertThrows<IllegalArgumentException> { service.createToken(HostTokenCreateRequest(name = "A", userId = 99L)) }
        val ex = assertThrows<IllegalArgumentException> { service.createToken(HostTokenCreateRequest(name = "A", userId = 4L)) }
        assertEquals("Token owner must be an admin or supervisor", ex.message)
    }

    private fun stored(revokedAt: LocalDateTime? = null) =
        HostToken(id = 7L, name = "Purpoz PC", tokenHash = service.hash("secret"), userId = 3L, revokedAt = revokedAt)

    @Test
    fun `authenticate accepts a valid token and records its use`() {
        val token = stored()
        `when`(hostTokenRepository.findByTokenHash(service.hash("secret"))).thenReturn(token)
        `when`(userRepository.findById(3L)).thenReturn(Optional.of(supervisor))

        assertEquals(HostPrincipal(tokenId = 7L, userId = 3L, name = "Purpoz PC"), service.authenticate("secret"))
        assertNotNull(token.lastUsedAt)
    }

    @Test
    fun `authenticate rejects unknown, blank and revoked tokens`() {
        `when`(hostTokenRepository.findByTokenHash(service.hash("secret"))).thenReturn(stored(revokedAt = LocalDateTime.now()))

        assertNull(service.authenticate("secret"))
        assertNull(service.authenticate("wrong"))
        assertNull(service.authenticate(" "))
        verify(hostTokenRepository, never()).save(any(HostToken::class.java))
    }

    @Test
    fun `authenticate rejects a token whose owner is no longer a supervisor`() {
        `when`(hostTokenRepository.findByTokenHash(service.hash("secret"))).thenReturn(stored())
        `when`(userRepository.findById(3L)).thenReturn(Optional.of(User(id = 3L, username = "purpoz", password = "x", role = "editor")))

        assertNull(service.authenticate("secret"))
    }

    @Test
    fun `revokeToken sets revokedAt once and rejects unknown ids`() {
        val token = stored()
        `when`(hostTokenRepository.findById(7L)).thenReturn(Optional.of(token))
        `when`(hostTokenRepository.findById(8L)).thenReturn(Optional.empty())

        service.revokeToken(7L)
        val revokedAt = token.revokedAt
        assertNotNull(revokedAt)
        service.revokeToken(7L)
        assertEquals(revokedAt, token.revokedAt)
        assertThrows<IllegalArgumentException> { service.revokeToken(8L) }
    }

    @Test
    fun `listTokens never exposes the token and shows the owner`() {
        `when`(hostTokenRepository.findAllByOrderByIdDesc()).thenReturn(listOf(stored()))
        `when`(userRepository.findAllById(listOf(3L))).thenReturn(listOf(supervisor))

        val dto = service.listTokens().single()

        assertEquals("purpoz", dto.username)
        assertNull(dto.revokedAt)
    }
}
