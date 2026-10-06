package no.battlefront.balancer.service

import no.battlefront.balancer.dto.PlayerCreateRequest
import no.battlefront.balancer.dto.PlayerRequestApproveRequest
import no.battlefront.balancer.dto.PlayerRequestCreateRequest
import no.battlefront.balancer.model.HostToken
import no.battlefront.balancer.model.Player
import no.battlefront.balancer.model.PlayerRequest
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.HostTokenRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.PlayerRequestRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.CurrentUserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional

/**
 * JUnit test class for [PlayerRequestService].
 */
@Tag("PlayerRequestService")
class PlayerRequestServiceTest {
    private val playerRequestRepository: PlayerRequestRepository = mock(PlayerRequestRepository::class.java)
    private val playerRepository: PlayerRepository = mock(PlayerRepository::class.java)
    private val hostTokenRepository: HostTokenRepository = mock(HostTokenRepository::class.java)
    private val userRepository: UserRepository = mock(UserRepository::class.java)
    private val playerService: PlayerService = mock(PlayerService::class.java)
    private val currentUserService: CurrentUserService = mock(CurrentUserService::class.java)
    private val service =
        PlayerRequestService(
            playerRequestRepository,
            playerRepository,
            hostTokenRepository,
            userRepository,
            playerService,
            currentUserService,
        )

    private fun request(
        nickname: String = "fihzy",
        nation: String = "NO",
        rating: Int = 75,
    ) = PlayerRequestCreateRequest(
        personaId = 1004081841178L,
        nickname = nickname,
        nation = nation,
        rating = rating,
        inGameName = " fihzy_ig ",
    )

    private fun pending(id: Long = 1L) =
        PlayerRequest(
            id = id,
            personaId = 1004081841178L,
            nickname = "fihzy",
            nation = "no",
            rating = 75,
            inGameName = "fihzy_ig",
            status = "pending",
        )

    @Test
    fun `submit stores a new pending request`() {
        `when`(playerRequestRepository.save(any(PlayerRequest::class.java))).thenAnswer { it.getArgument(0) }

        val result = service.submit(request(), tokenId = 7L)

        assertFalse(result.replaced)
        assertEquals("pending", result.status)
        val saved = org.mockito.ArgumentCaptor.forClass(PlayerRequest::class.java)
        verify(playerRequestRepository).save(saved.capture())
        assertEquals("no", saved.value.nation)
        assertEquals("fihzy_ig", saved.value.inGameName)
        assertEquals(7L, saved.value.tokenId)
    }

    @Test
    fun `submit replaces a pending request for the same persona ID`() {
        val existing = pending(id = 5L)
        `when`(playerRequestRepository.findByPersonaIdAndStatus(1004081841178L, "pending")).thenReturn(existing)
        `when`(playerRequestRepository.save(any(PlayerRequest::class.java))).thenAnswer { it.getArgument(0) }

        val result = service.submit(request(nickname = "Fihzy", rating = 80), tokenId = 8L)

        assertTrue(result.replaced)
        assertEquals(5L, result.id)
        assertEquals("Fihzy", existing.nickname)
        assertEquals(80, existing.rating)
        assertEquals(8L, existing.tokenId)
    }

    @Test
    fun `submit rejects a persona ID that is already registered`() {
        `when`(playerRepository.findByPersonaId(1004081841178L))
            .thenReturn(Player(id = 40L, nickname = "Naeven", nation = "fr", personaId = 1004081841178L))

        val ex = assertThrows<PersonaAlreadyRegisteredException> { service.submit(request(), tokenId = 7L) }
        assertEquals(40L, ex.playerId)
        verify(playerRequestRepository, never()).save(any(PlayerRequest::class.java))
    }

    @Test
    fun `submit validates fields`() {
        assertThrows<IllegalArgumentException> { service.submit(request(nickname = " "), 7L) }
        assertThrows<IllegalArgumentException> { service.submit(request(nickname = "x".repeat(17)), 7L) }
        assertThrows<IllegalArgumentException> { service.submit(request(nickname = "<script>"), 7L) }
        assertThrows<IllegalArgumentException> { service.submit(request(nation = "nor"), 7L) }
        assertThrows<IllegalArgumentException> { service.submit(request(rating = 0), 7L) }
        assertThrows<IllegalArgumentException> { service.submit(request().copy(personaId = 0), 7L) }
    }

    @Test
    fun `approve creates the player with corrections and records who approved`() {
        val req = pending()
        `when`(playerRequestRepository.findById(1L)).thenReturn(Optional.of(req))
        `when`(currentUserService.currentUserId()).thenReturn(2L)
        val created = Player(id = 50L, nickname = "Fihzy", nation = "no", rating = 80, personaId = 1004081841178L)
        `when`(playerService.createPlayer(PlayerCreateRequest("Fihzy", "no", 80, 1004081841178L))).thenReturn(created)

        val player = service.approve(1L, PlayerRequestApproveRequest(nickname = "Fihzy", rating = 80))

        assertEquals(50L, player.id)
        assertEquals("fihzy_ig", created.lastSeenName)
        assertEquals("approved", req.status)
        assertEquals(2L, req.resolvedBy)
        assertEquals(50L, req.playerId)
    }

    @Test
    fun `link gives an existing player the persona ID`() {
        val req = pending()
        val player = Player(id = 12L, nickname = "Basse", nation = "no")
        `when`(playerRequestRepository.findById(1L)).thenReturn(Optional.of(req))
        `when`(playerRepository.findById(12L)).thenReturn(Optional.of(player))

        service.link(1L, 12L)

        assertEquals(1004081841178L, player.personaId)
        assertEquals("fihzy_ig", player.lastSeenName)
        assertEquals("Basse", player.nickname)
        assertEquals("approved", req.status)
        assertEquals(12L, req.playerId)
    }

    @Test
    fun `link refuses a player that already has another persona ID`() {
        `when`(playerRequestRepository.findById(1L)).thenReturn(Optional.of(pending()))
        `when`(playerRepository.findById(12L)).thenReturn(Optional.of(Player(id = 12L, nickname = "Basse", personaId = 99L)))

        val ex = assertThrows<IllegalArgumentException> { service.link(1L, 12L) }
        assertEquals("Basse already has persona ID 99", ex.message)
    }

    @Test
    fun `reject marks the request and only pending requests can be resolved`() {
        val req = pending()
        `when`(playerRequestRepository.findById(1L)).thenReturn(Optional.of(req))

        service.reject(1L)

        assertEquals("rejected", req.status)
        assertNull(req.playerId)
        val ex = assertThrows<IllegalArgumentException> { service.reject(1L) }
        assertEquals("Request is already rejected", ex.message)
    }

    @Test
    fun `listPending shows which token sent the request`() {
        `when`(playerRequestRepository.findByStatusOrderByRequestedAtDesc("pending")).thenReturn(listOf(pending().apply { tokenId = 7L }))
        `when`(hostTokenRepository.findAllById(listOf(7L))).thenReturn(listOf(HostToken(id = 7L, name = "Purpoz PC", userId = 3L)))
        `when`(userRepository.findAllById(listOf(3L))).thenReturn(listOf(User(id = 3L, username = "purpoz", role = "supervisor")))

        assertEquals("Purpoz PC (purpoz)", service.listPending().single().requestedBy)
    }
}
