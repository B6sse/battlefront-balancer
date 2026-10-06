package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.HostTokenCreateRequest
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.HostTokenRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.PlayerRequestRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.AppUserDetails
import no.battlefront.balancer.service.HostTokenService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * End-to-end test of player requests through HTTP and the real security rules (H2 database).
 */
@SpringBootTest
@ActiveProfiles("test")
class PlayerRequestFlowTest {
    @Autowired private lateinit var context: WebApplicationContext

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var hostTokenRepository: HostTokenRepository

    @Autowired private lateinit var playerRequestRepository: PlayerRequestRepository

    @Autowired private lateinit var playerRepository: PlayerRepository

    @Autowired private lateinit var rankedPlayerStatRepository: RankedPlayerStatRepository

    @Autowired private lateinit var hostTokenService: HostTokenService

    private lateinit var mvc: MockMvc
    private lateinit var editor: User
    private lateinit var supervisor: User
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        editor = userRepository.save(User(username = "editor", password = "x", role = "editor"))
        supervisor = userRepository.save(User(username = "sup", password = "x", role = "supervisor"))
        token = hostTokenService.createToken(HostTokenCreateRequest(name = "Sup PC", userId = supervisor.id)).token
    }

    @AfterEach
    fun tearDown() {
        playerRequestRepository.deleteAll()
        rankedPlayerStatRepository.deleteAll()
        playerRepository.deleteAll()
        hostTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun submit(body: String) =
        mvc.perform(
            post("/api/player-requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )

    private val fihzy = """{"personaId":1004081841178,"nickname":"fihzy","nation":"no","rating":75,"inGameName":"fihzy_ig"}"""

    @Test
    fun `host requests a player, editor approves, and the player can be looked up by persona ID`() {
        submit(fihzy).andExpect(status().isOk).andExpect(jsonPath("$.replaced").value(false))
        // A second request for the same persona ID replaces the first
        submit(fihzy.replace("\"rating\":75", "\"rating\":82"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.replaced").value(true))

        val list =
            mvc
                .perform(get("/api/player-requests").with(user(AppUserDetails(editor))))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rating").value(82))
                .andExpect(jsonPath("$[0].requestedBy").value("Sup PC (sup)"))
                .andReturn()
        val id = Regex("\"id\":(\\d+)").find(list.response.contentAsString)!!.groupValues[1]

        mvc
            .perform(
                post("/api/player-requests/$id/approve")
                    .with(user(AppUserDetails(editor)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nickname":"Fihzy"}"""),
            ).andExpect(status().isOk)

        mvc
            .perform(get("/api/players/by-persona").param("ids", "1004081841178").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.players[0].nickname").value("Fihzy"))
            .andExpect(jsonPath("$.players[0].lastSeenName").value("fihzy_ig"))
            .andExpect(jsonPath("$.players[0].br").value(950))
            .andExpect(jsonPath("$.unknown.length()").value(0))

        // Now registered: a new request is refused
        submit(fihzy).andExpect(status().isConflict)
        mvc
            .perform(get("/api/player-requests").with(user(AppUserDetails(editor))))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `only hosts can submit and only admins and editors can resolve`() {
        mvc
            .perform(post("/api/player-requests").with(user(AppUserDetails(editor))).contentType(MediaType.APPLICATION_JSON).content(fihzy))
            .andExpect(status().isForbidden)
        mvc.perform(get("/api/player-requests").with(user(AppUserDetails(supervisor)))).andExpect(status().isForbidden)
        mvc.perform(post("/api/player-requests/1/reject").with(user(AppUserDetails(supervisor)))).andExpect(status().isForbidden)
        mvc.perform(get("/api/player-requests").header(HttpHeaders.AUTHORIZATION, "Bearer $token")).andExpect(status().isForbidden)
    }
}
