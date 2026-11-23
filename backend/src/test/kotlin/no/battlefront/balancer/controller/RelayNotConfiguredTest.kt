package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.HostTokenCreateRequest
import no.battlefront.balancer.model.Relay
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.HostTokenRepository
import no.battlefront.balancer.repository.RelayRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.service.HostTokenService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Without a ticket secret the relay session endpoints answer 503 and the rest of the site works.
 */
@SpringBootTest(properties = ["app.relay.ticket-secret="])
@ActiveProfiles("test")
class RelayNotConfiguredTest {
    @Autowired private lateinit var context: WebApplicationContext

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var hostTokenRepository: HostTokenRepository

    @Autowired private lateinit var relayRepository: RelayRepository

    @Autowired private lateinit var hostTokenService: HostTokenService

    private lateinit var mvc: MockMvc
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        val owner = userRepository.save(User(username = "sup", password = "x", role = "supervisor"))
        token = hostTokenService.createToken(HostTokenCreateRequest(name = "Host", userId = owner.id)).token
        relayRepository.save(Relay(id = "eu1", region = "EU", name = "Europe", host = "relay-eu.example.com"))
    }

    @AfterEach
    fun tearDown() {
        relayRepository.deleteAll()
        hostTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun `session endpoints answer 503 with a clear message and everything else works`() {
        val message = "Relay sessions are not configured on this server (RELAY_TICKET_SECRET is not set)"
        val auth = "Bearer $token"
        val body = """{"relayId":"eu1","name":"Friday 4v4","mode":"DropZone","level":"L","maxPlayers":16}"""
        mvc
            .perform(
                post("/api/relay/sessions").header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON).content(body),
            ).andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.message").value(message))
        mvc
            .perform(
                put(
                    "/api/relay/sessions/abc",
                ).header(HttpHeaders.AUTHORIZATION, auth).contentType(MediaType.APPLICATION_JSON).content("{}"),
            ).andExpect(status().isServiceUnavailable)
        mvc.perform(delete("/api/relay/sessions/abc").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isServiceUnavailable)
        mvc.perform(get("/api/relay/sessions")).andExpect(status().isServiceUnavailable).andExpect(jsonPath("$.message").value(message))
        mvc.perform(post("/api/relay/sessions/abc/join")).andExpect(status().isServiceUnavailable)

        mvc.perform(get("/api/relays")).andExpect(status().isOk).andExpect(jsonPath("$.relays[0].id").value("eu1"))
        mvc.perform(get("/api/health")).andExpect(status().isOk)
        mvc.perform(get("/api/players").header(HttpHeaders.AUTHORIZATION, auth)).andExpect(status().isOk)
    }
}
