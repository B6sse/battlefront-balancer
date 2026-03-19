package no.battlefront.balancer.controller

import jakarta.servlet.http.HttpServletRequest
import no.battlefront.balancer.dto.LastMatchPlayerDto
import no.battlefront.balancer.dto.LoginRequest
import no.battlefront.balancer.dto.MatchSubmitRequest
import no.battlefront.balancer.dto.PlayerCreateRequest
import no.battlefront.balancer.dto.PlayerUpdateRequest
import no.battlefront.balancer.dto.PlayerWithStatsDto
import no.battlefront.balancer.dto.RandomizerDto
import no.battlefront.balancer.dto.RandomizerSubmitRequest
import no.battlefront.balancer.model.Player
import no.battlefront.balancer.model.Randomizer
import no.battlefront.balancer.model.User
import no.battlefront.balancer.security.AppUserDetails
import no.battlefront.balancer.service.MatchService
import no.battlefront.balancer.service.PlayerService
import no.battlefront.balancer.service.RandomizerService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

class ControllerTest {
    @BeforeEach
    fun clearSecurityContext() {
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `HealthController returns UP`() {
        val controller = HealthController()
        val result = controller.health()
        assertEquals("UP", result["status"])
    }

    @Test
    fun `AuthController login sets security context and returns role`() {
        val authenticationManager = mock(AuthenticationManager::class.java)
        val controller = AuthController(authenticationManager)

        val user = User(id = 42L, username = "admin", password = "pw", role = "admin")
        val details = AppUserDetails(user)
        val auth = UsernamePasswordAuthenticationToken(details, null, details.authorities)

        `when`(authenticationManager.authenticate(any(Authentication::class.java))).thenReturn(auth)

        val httpRequest = mock(HttpServletRequest::class.java)
        val loginSession = mock<jakarta.servlet.http.HttpSession>()
        `when`(httpRequest.getSession(true)).thenReturn(loginSession)

        val response = controller.login(LoginRequest(username = "admin", password = "pw"), httpRequest)
        assertEquals(HttpStatus.OK, response.statusCode)

        val body = requireNotNull(response.body) as no.battlefront.balancer.dto.CurrentUserDto
        assertEquals(42L, body.id)
        assertEquals("admin", body.username)
        assertEquals("admin", body.role)

        assertTrue(SecurityContextHolder.getContext().authentication == auth)
    }

    @Test
    fun `AuthController login throws when authentication fails`() {
        val authenticationManager = mock(AuthenticationManager::class.java)
        val controller = AuthController(authenticationManager)

        `when`(authenticationManager.authenticate(any(Authentication::class.java))).thenThrow(
            BadCredentialsException("bad credentials"),
        )

        val httpRequest = mock(HttpServletRequest::class.java)

        assertThrows<BadCredentialsException> {
            controller.login(LoginRequest(username = "x", password = "y"), httpRequest)
        }

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `AuthController me returns 401 when principal is null`() {
        val controller = AuthController(mock(AuthenticationManager::class.java))
        val response = controller.me(null)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `AuthController me returns role without ROLE_ prefix`() {
        val controller = AuthController(mock(AuthenticationManager::class.java))

        val user = User(id = 7L, username = "s", password = "pw", role = "supervisor")
        val principal = AppUserDetails(user)
        val response = controller.me(principal)

        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals(7L, body.id)
        assertEquals("s", body.username)
        assertEquals("supervisor", body.role)
    }

    @Test
    fun `AuthController logout invalidates session and clears security context`() {
        val authenticationManager = mock(AuthenticationManager::class.java)
        val controller = AuthController(authenticationManager)

        val user = User(id = 1L, username = "u", password = "pw", role = "admin")
        val principal = AppUserDetails(user)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)

        val httpRequest = mock(HttpServletRequest::class.java)
        val session = mock<jakarta.servlet.http.HttpSession>()
        `when`(httpRequest.getSession()).thenReturn(session)
        `when`(httpRequest.getSession(false)).thenReturn(session)

        val response = controller.logout(httpRequest)
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        verify(session).invalidate()
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `PlayerController getPlayers returns list`() {
        val playerService = mock(PlayerService::class.java)
        val controller = PlayerController(playerService)

        val players =
            listOf(
                PlayerWithStatsDto(
                    id = 1L,
                    nickname = "A",
                    nation = "no",
                    rating = 80,
                    dzrating = 75,
                    elo = 100,
                    br = 90,
                    played = 1,
                    best = 95,
                    won = 1,
                    lost = 0,
                    draw = 0,
                    score = 10,
                    mvp = 0,
                ),
            )

        `when`(playerService.getPlayersWithCurrentSeasonStats()).thenReturn(players)

        val response = controller.getPlayers()
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals(1, body.size)
        assertEquals("A", body.first().nickname)
    }

    @Test
    fun `PlayerController createPlayer succeeds`() {
        val playerService = mock(PlayerService::class.java)
        val controller = PlayerController(playerService)

        val request = PlayerCreateRequest(nickname = "New", nation = "no", rating = 50)
        val saved = Player(id = 10L, nickname = "New", nation = "no", rating = 50, dzrating = 50, elo = 900)

        `when`(playerService.createPlayer(request)).thenReturn(saved)

        val response = controller.createPlayer(request)
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body) as Player
        assertEquals(10L, body.id)
        assertEquals("New", body.nickname)
    }

    @Test
    fun `PlayerController createPlayer badRequest maps IllegalArgumentException message fallback`() {
        val playerService = mock(PlayerService::class.java)
        val controller = PlayerController(playerService)

        val request = PlayerCreateRequest(nickname = " ", nation = "no", rating = 10)
        `when`(playerService.createPlayer(request)).thenThrow(IllegalArgumentException())

        val response = controller.createPlayer(request)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = requireNotNull(response.body) as Map<*, *>
        assertEquals("Invalid request", body["message"])
    }

    @Test
    fun `PlayerController updatePlayer succeeds`() {
        val playerService = mock(PlayerService::class.java)
        val controller = PlayerController(playerService)

        val request = PlayerUpdateRequest(nickname = "N", nation = "no", rating = 70, dzrating = 70, br = 800)
        val saved = Player(id = 2L, nickname = "N", nation = "no", rating = 70, dzrating = 70, elo = 900)

        `when`(playerService.updatePlayer(2L, request)).thenReturn(saved)

        val response = controller.updatePlayer(2L, request)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("N", (response.body as Player).nickname)
    }

    @Test
    fun `PlayerController updatePlayer badRequest maps IllegalArgumentException message`() {
        val playerService = mock(PlayerService::class.java)
        val controller = PlayerController(playerService)

        val request = PlayerUpdateRequest(nickname = "X", nation = "no", rating = 70, dzrating = 70, br = 800)
        `when`(playerService.updatePlayer(2L, request)).thenThrow(IllegalArgumentException("bad"))

        val response = controller.updatePlayer(2L, request)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = requireNotNull(response.body) as Map<*, *>
        assertEquals("bad", body["message"])
    }

    @Test
    fun `PlayerController updatePlayer badRequest maps IllegalStateException fallback`() {
        val playerService = mock(PlayerService::class.java)
        val controller = PlayerController(playerService)

        val request = PlayerUpdateRequest(nickname = "X", nation = "no", rating = 70, dzrating = 70, br = 800)
        `when`(playerService.updatePlayer(2L, request)).thenThrow(IllegalStateException())

        val response = controller.updatePlayer(2L, request)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val body = requireNotNull(response.body) as Map<*, *>
        assertEquals("Season stats not found", body["message"])
    }

    @Test
    fun `PlayerController deletePlayer returns 204 and calls service`() {
        val playerService = mock(PlayerService::class.java)
        val controller = PlayerController(playerService)

        val response = controller.deletePlayer(5L)
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        verify(playerService).deletePlayer(5L)
    }

    @Test
    fun `MatchController getPlayersInLastMatch returns OK`() {
        val matchService = mock(MatchService::class.java)
        val controller = MatchController(matchService)

        val last =
            listOf(
                LastMatchPlayerDto(
                    id = 1L,
                    nickname = "A",
                    nation = "no",
                    rating = 80,
                    br = 90,
                    best = 95,
                    played = 1,
                    won = 1,
                    lost = 0,
                    draw = 0,
                    score = 10,
                    mvp = 0,
                ),
            )
        `when`(matchService.getPlayersInLastMatch()).thenReturn(last)

        val response = controller.getPlayersInLastMatch()
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals(1, body.size)
        assertEquals("A", body.first().nickname)
    }

    @Test
    fun `MatchController submitMatch returns 200 on success`() {
        val matchService = mock(MatchService::class.java)
        val controller = MatchController(matchService)

        val req =
            MatchSubmitRequest(
                matchData = listOf("map", 2, 3, 4, 5, "DSE"),
                rebels = emptyList(),
                imperials = emptyList(),
            )

        org.mockito.Mockito
            .doNothing()
            .`when`(matchService)
            .submitMatch(req)

        val response = controller.submitMatch(req)
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals(true, body["success"])
    }

    @Test
    fun `MatchController submitMatch returns 400 on IllegalArgumentException fallback`() {
        val matchService = mock(MatchService::class.java)
        val controller = MatchController(matchService)

        val req =
            MatchSubmitRequest(
                matchData = listOf("x"),
                rebels = emptyList(),
                imperials = emptyList(),
            )

        `when`(matchService.submitMatch(req)).thenThrow(IllegalArgumentException())

        val response = controller.submitMatch(req)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        val body = requireNotNull(response.body)
        assertEquals(false, body["success"])
        assertEquals("Invalid request", body["message"])
    }

    @Test
    fun `MatchController submitMatch returns 500 on unexpected exception`() {
        val matchService = mock(MatchService::class.java)
        val controller = MatchController(matchService)

        val req =
            MatchSubmitRequest(
                matchData = listOf("x"),
                rebels = emptyList(),
                imperials = emptyList(),
            )

        `when`(matchService.submitMatch(req)).thenThrow(RuntimeException())

        val response = controller.submitMatch(req)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)

        val body = requireNotNull(response.body)
        assertEquals(false, body["success"])
        assertEquals("Error saving match", body["message"])
    }

    @Test
    fun `RandomizerController getLatest returns OK`() {
        val randomizerService = mock(RandomizerService::class.java)
        val controller = RandomizerController(randomizerService)

        val dto = RandomizerDto(map = "Dune Sea", rule = "DSE")
        `when`(randomizerService.getLatest()).thenReturn(dto)

        val response = controller.getLatest()
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals("Dune Sea", body.map)
        assertEquals("DSE", body.rule)
    }

    @Test
    fun `RandomizerController submit returns 200 on success`() {
        val randomizerService = mock(RandomizerService::class.java)
        val controller = RandomizerController(randomizerService)

        val req = RandomizerSubmitRequest(map = "Dune Sea", rule = "DSE")
        `when`(randomizerService.save(req.map, req.rule)).thenReturn(Randomizer(map = req.map, rule = req.rule))

        val response = controller.submit(req)
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = requireNotNull(response.body)
        assertEquals(true, body["success"])
    }

    @Test
    fun `RandomizerController submit returns 400 on exception fallback`() {
        val randomizerService = mock(RandomizerService::class.java)
        val controller = RandomizerController(randomizerService)

        val req = RandomizerSubmitRequest(map = "x", rule = "y")
        `when`(randomizerService.save(req.map, req.rule)).thenThrow(RuntimeException())

        val response = controller.submit(req)
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)

        val body = requireNotNull(response.body)
        assertEquals(false, body["success"])
        assertEquals("Error saving randomizer", body["message"])
    }
}
