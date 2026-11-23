package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.HostTokenCreateRequest
import no.battlefront.balancer.model.Relay
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.HostTokenRepository
import no.battlefront.balancer.repository.RelayRepository
import no.battlefront.balancer.repository.RelaySessionRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.AppUserDetails
import no.battlefront.balancer.security.RelayTicketSigner
import no.battlefront.balancer.service.HostTokenService
import no.battlefront.balancer.service.RelaySessionService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end test of relays and relay sessions through HTTP and the real security rules (H2 database).
 */
@SpringBootTest
@ActiveProfiles("test")
class RelaySessionFlowTest {
    @Autowired private lateinit var context: WebApplicationContext

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var hostTokenRepository: HostTokenRepository

    @Autowired private lateinit var relayRepository: RelayRepository

    @Autowired private lateinit var relaySessionRepository: RelaySessionRepository

    @Autowired private lateinit var hostTokenService: HostTokenService

    @Autowired private lateinit var relaySessionService: RelaySessionService

    private lateinit var mvc: MockMvc
    private lateinit var admin: User
    private lateinit var editor: User
    private lateinit var token: String
    private lateinit var otherToken: String
    private lateinit var ip: String

    private val signer = RelayTicketSigner("test-ticket-secret-0123456789abcdefghij")
    private val start: Instant = Instant.parse("2026-11-23T12:00:00Z")

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        admin = userRepository.save(User(username = "admin", password = "x", role = "admin"))
        editor = userRepository.save(User(username = "editor", password = "x", role = "editor"))
        token = hostTokenService.createToken(HostTokenCreateRequest(name = "Host A", userId = editor.id)).token
        otherToken = hostTokenService.createToken(HostTokenCreateRequest(name = "Host B", userId = editor.id)).token
        relayRepository.save(Relay(id = "eu1", region = "EU", name = "Europe", host = "relay-eu.example.com", port = 25200))
        relayRepository.save(Relay(id = "na1", region = "NA", name = "North America", host = "203.0.113.7", port = 25201))
        relayRepository.save(Relay(id = "old", region = "EU", name = "Retired", host = "old.example.com", active = false))
        setTime(start)
        // The join rate limit is per IP address and lives as long as the test context: one address per test
        ip = "10.1.0.${NEXT_IP.incrementAndGet()}"
    }

    @AfterEach
    fun tearDown() {
        relaySessionService.clock = Clock.systemUTC()
        relaySessionRepository.deleteAll()
        relayRepository.deleteAll()
        hostTokenRepository.deleteAll()
        userRepository.deleteAll()
        relaySessionService.invalidateList()
    }

    private fun setTime(instant: Instant) {
        relaySessionService.clock = Clock.fixed(instant, ZoneOffset.UTC)
    }

    private fun session(extra: String = "") =
        """{"relayId":"eu1","name":"Friday 4v4","mode":"DropZone","level":"Levels/Desert/Desert_04/Desert_04","maxPlayers":16,"auricVersion":"1.5.0"$extra}"""

    private fun create(
        body: String = session(),
        bearer: String = token,
    ): ResultActions =
        mvc.perform(
            post(
                "/api/relay/sessions",
            ).header(HttpHeaders.AUTHORIZATION, "Bearer $bearer").contentType(MediaType.APPLICATION_JSON).content(body),
        )

    private fun heartbeat(
        sid: String,
        body: String = """{"players":7}""",
        bearer: String = token,
    ): ResultActions =
        mvc.perform(
            put(
                "/api/relay/sessions/$sid",
            ).header(HttpHeaders.AUTHORIZATION, "Bearer $bearer").contentType(MediaType.APPLICATION_JSON).content(body),
        )

    private fun join(
        sid: String,
        body: String? = null,
        from: String = ip,
    ): ResultActions {
        val request =
            post("/api/relay/sessions/$sid/join").with {
                it.remoteAddr = from
                it
            }
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body)
        return mvc.perform(request)
    }

    private fun list(): ResultActions = mvc.perform(get("/api/relay/sessions"))

    private fun field(
        json: String,
        name: String,
    ): String = Regex("\"$name\":\"([^\"]+)\"").find(json)!!.groupValues[1]

    private fun payloadOf(ticket: String): String = String(Base64.getUrlDecoder().decode(ticket.substringBefore('.')))

    private fun sidOf(result: ResultActions): String =
        field(
            result
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString,
            "sid",
        )

    @Test
    fun `create, heartbeat, list, join and delete`() {
        val created =
            create()
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.heartbeatSeconds").value(30))
                .andReturn()
                .response.contentAsString
        val sid = field(created, "sid")
        assertTrue(Regex("^[0-9a-f]{32}$").matches(sid), sid)
        val exp = start.epochSecond + 12 * 3600
        val hostTicket = field(created, "hostTicket")
        assertEquals("""{"sid":"$sid","role":"host","relay":"eu1","max":16,"exp":$exp}""", payloadOf(hostTicket))
        assertEquals(signer.sign(payloadOf(hostTicket)), hostTicket)

        list()
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(
                content().json(
                    """{"sessions":[{"sid":"$sid","name":"Friday 4v4","relayId":"eu1","region":"EU","mode":"DropZone",
                       "level":"Levels/Desert/Desert_04/Desert_04","players":0,"maxPlayers":16,"hasPassword":false,"auricVersion":"1.5.0"}]}""",
                    org.springframework.test.json.JsonCompareMode.STRICT,
                ),
            )

        heartbeat(
            sid,
            """{"players":7,"mode":"Blast","level":"Levels/Hoth/Hoth_01"}""",
        ).andExpect(status().isOk).andExpect(content().json("{}"))
        list()
            .andExpect(jsonPath("$.sessions[0].players").value(7))
            .andExpect(jsonPath("$.sessions[0].mode").value("Blast"))
            .andExpect(jsonPath("$.sessions[0].level").value("Levels/Hoth/Hoth_01"))
        // A heartbeat with only the player count keeps mode and level
        heartbeat(sid, """{"players":8}""").andExpect(status().isOk)
        list().andExpect(jsonPath("$.sessions[0].players").value(8)).andExpect(jsonPath("$.sessions[0].mode").value("Blast"))

        val joined =
            join(sid)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.relay.id").value("eu1"))
                .andExpect(jsonPath("$.relay.host").value("relay-eu.example.com"))
                .andExpect(jsonPath("$.relay.port").value(25200))
                .andReturn()
                .response.contentAsString
        val clientTicket = field(joined, "clientTicket")
        val cid = field(payloadOf(clientTicket), "cid")
        assertTrue(Regex("^[0-9a-f]{16}$").matches(cid), cid)
        assertEquals("""{"sid":"$sid","role":"client","relay":"eu1","cid":"$cid","exp":$exp}""", payloadOf(clientTicket))
        assertEquals(signer.sign(payloadOf(clientTicket)), clientTicket)
        // Every join gets its own cid
        val second = field(join(sid, "{}").andReturn().response.contentAsString, "clientTicket")
        assertNotEquals(cid, field(payloadOf(second), "cid"))

        mvc.perform(delete("/api/relay/sessions/$sid").header(HttpHeaders.AUTHORIZATION, "Bearer $token")).andExpect(status().isNoContent)
        list().andExpect(jsonPath("$.sessions.length()").value(0))
        join(sid).andExpect(status().isNotFound).andExpect(jsonPath("$.message").value("Session not found"))
        heartbeat(sid).andExpect(status().isNotFound)
        mvc.perform(delete("/api/relay/sessions/$sid").header(HttpHeaders.AUTHORIZATION, "Bearer $token")).andExpect(status().isNotFound)
    }

    @Test
    fun `a session without a heartbeat for 90 seconds is gone`() {
        val sid = sidOf(create())
        setTime(start.plusSeconds(60))
        heartbeat(sid).andExpect(status().isOk)
        // 90 seconds after the last heartbeat it is still live; one second later it is not
        setTime(start.plusSeconds(150))
        list().andExpect(jsonPath("$.sessions.length()").value(1))
        setTime(start.plusSeconds(151))
        list().andExpect(jsonPath("$.sessions.length()").value(0))
        join(sid).andExpect(status().isNotFound)
        heartbeat(sid).andExpect(status().isNotFound)

        // The host creates a new one, which also clears the expired row
        val newSid = sidOf(create())
        assertNotEquals(sid, newSid)
        assertEquals(listOf(newSid), relaySessionRepository.findAll().map { it.sid })
    }

    @Test
    fun `a second session from the same token replaces the first, and other tokens keep theirs`() {
        val first = sidOf(create())
        val others = sidOf(create(session().replace("Friday 4v4", "Other host").replace("eu1", "na1"), otherToken))
        val second = sidOf(create(session().replace("Friday 4v4", "Saturday 8v8")))

        list()
            .andExpect(jsonPath("$.sessions.length()").value(2))
            .andExpect(jsonPath("$.sessions[0].name").value("Other host"))
            .andExpect(jsonPath("$.sessions[0].region").value("NA"))
            .andExpect(jsonPath("$.sessions[1].sid").value(second))
            .andExpect(jsonPath("$.sessions[1].name").value("Saturday 8v8"))
        heartbeat(first).andExpect(status().isNotFound)
        join(first).andExpect(status().isNotFound)
        heartbeat(second).andExpect(status().isOk)
        heartbeat(others, bearer = otherToken).andExpect(status().isOk)
    }

    @Test
    fun `a wrong or missing password is refused and the right one gets a ticket`() {
        val created =
            create(session(""","password":"hunter2 æøå""""))
                .andExpect(status().isCreated)
                .andReturn()
                .response.contentAsString
        assertFalse(created.contains("hunter2"))
        val sid = field(created, "sid")
        val stored = relaySessionRepository.findById(sid).get().passwordHash!!
        assertTrue(stored.startsWith("\$2"), "bcrypt hash expected")
        assertFalse(stored.contains("hunter2"))

        val listed =
            list()
                .andExpect(jsonPath("$.sessions[0].hasPassword").value(true))
                .andReturn()
                .response.contentAsString
        assertFalse(listed.contains("hunter2") || listed.contains("password\":\"") || listed.contains(stored))

        join(sid, """{"password":"hunter3"}""").andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("Wrong password"))
        join(sid, """{"password":""}""").andExpect(status().isForbidden)
        join(sid, "{}").andExpect(status().isForbidden).andExpect(jsonPath("$.message").value("Wrong password"))
        join(sid).andExpect(status().isForbidden)
        join(sid, """{"password":"${"x".repeat(500)}"}""").andExpect(status().isForbidden)
        join(sid, """{"password":"hunter2 æøå"}""").andExpect(status().isOk).andExpect(jsonPath("$.clientTicket").isNotEmpty)

        // A session without a password ignores whatever password is sent
        val open = sidOf(create(bearer = otherToken))
        join(open, """{"password":"anything"}""").andExpect(status().isOk)
    }

    @Test
    fun `joining is rate limited per IP address`() {
        val sid = sidOf(create(session(""","password":"secret"""")))
        repeat(10) { join(sid, """{"password":"guess$it"}""").andExpect(status().isForbidden) }
        join(sid, """{"password":"guess10"}""")
            .andExpect(status().isTooManyRequests)
            .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
            .andExpect(jsonPath("$.message").isNotEmpty)
        // Even the right password has to wait
        join(sid, """{"password":"secret"}""").andExpect(status().isTooManyRequests)
        // Another address is not affected
        join(sid, """{"password":"secret"}""", from = "10.2.0.${NEXT_IP.incrementAndGet()}").andExpect(status().isOk)
    }

    @Test
    fun `another token cannot heartbeat or delete someone else's session`() {
        val sid = sidOf(create())
        heartbeat(sid, """{"players":16,"mode":"Hijacked"}""", otherToken).andExpect(status().isNotFound)
        mvc
            .perform(
                delete("/api/relay/sessions/$sid").header(HttpHeaders.AUTHORIZATION, "Bearer $otherToken"),
            ).andExpect(status().isNotFound)
        list()
            .andExpect(jsonPath("$.sessions.length()").value(1))
            .andExpect(jsonPath("$.sessions[0].players").value(0))
            .andExpect(jsonPath("$.sessions[0].mode").value("DropZone"))
        heartbeat(sid).andExpect(status().isOk)
    }

    @Test
    fun `sessions need a host token to create, update and end, and the lists are public`() {
        val sid = sidOf(create())
        val json = MediaType.APPLICATION_JSON
        mvc.perform(post("/api/relay/sessions").contentType(json).content(session())).andExpect(status().isUnauthorized)
        mvc
            .perform(
                post("/api/relay/sessions").with(user(AppUserDetails(admin))).contentType(json).content(session()),
            ).andExpect(status().isForbidden)
        mvc
            .perform(
                put("/api/relay/sessions/$sid").with(user(AppUserDetails(admin))).contentType(json).content("{}"),
            ).andExpect(status().isForbidden)
        mvc.perform(delete("/api/relay/sessions/$sid").with(user(AppUserDetails(admin)))).andExpect(status().isForbidden)
        create(bearer = "not-a-token").andExpect(status().isUnauthorized)

        for (path in listOf("/api/relays", "/api/relay/sessions")) {
            mvc.perform(get(path)).andExpect(status().isOk)
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer $token")).andExpect(status().isOk)
        }
        mvc
            .perform(
                post("/api/relay/sessions/$sid/join")
                    .with {
                        it.remoteAddr = ip
                        it
                    }.header(HttpHeaders.AUTHORIZATION, "Bearer $otherToken"),
            ).andExpect(status().isOk)

        // Nothing about the host leaks into public answers
        val tokenIds = hostTokenRepository.findAll().map { it.id }
        val listed = list().andReturn().response.contentAsString
        assertFalse(listed.contains("hostToken") || listed.contains("Host A") || listed.contains(token), listed)
        assertEquals(2, tokenIds.size)
    }

    @Test
    fun `invalid sessions are rejected with a clear message`() {
        fun expect400(
            body: String,
            message: String,
        ) = create(body).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value(message))

        expect400(session().replace("eu1", "nope"), "Unknown relay \"nope\"")
        expect400(session().replace("eu1", "old"), "Unknown relay \"old\"")
        expect400(session().replace("\"relayId\":\"eu1\",", ""), "relayId is required")
        expect400(session().replace("Friday 4v4", "  \\u0000\\n "), "name is required")
        expect400(session().replace("Friday 4v4", "x".repeat(41)), "name max 40 characters")
        expect400(session().replace("16", "1"), "maxPlayers must be 2 to 40")
        expect400(session().replace("16", "41"), "maxPlayers must be 2 to 40")
        expect400(session().replace("\"maxPlayers\":16,", ""), "maxPlayers is required")
        expect400(session(""","password":"${"p".repeat(41)}""""), "password max 40 characters")
        expect400(session().replace("DropZone", ""), "mode is required")
        list().andExpect(jsonPath("$.sessions.length()").value(0))

        // Control characters are removed and the name is trimmed; limits are inclusive
        val sid =
            sidOf(
                create(
                    session(
                        ""","password":"${"p".repeat(40)}"""",
                    ).replace("Friday 4v4", "  Fri\\u0007day\\n 4v4\\u200b ").replace("16", "40"),
                ),
            )
        list().andExpect(jsonPath("$.sessions[0].name").value("Friday 4v4")).andExpect(jsonPath("$.sessions[0].maxPlayers").value(40))
        heartbeat(
            sid,
            """{"players":41}""",
        ).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("players must be 0 to 40"))
        heartbeat(sid, """{"players":-1}""").andExpect(status().isBadRequest)
    }

    @Test
    fun `admins manage relays, the public list has active ones only, and a hidden relay hides its sessions`() {
        val json = MediaType.APPLICATION_JSON
        val asAdmin = user(AppUserDetails(admin))
        mvc
            .perform(get("/api/relays"))
            .andExpect(status().isOk)
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(
                content().json(
                    """{"relays":[{"id":"eu1","region":"EU","name":"Europe","host":"relay-eu.example.com","port":25200},
                       {"id":"na1","region":"NA","name":"North America","host":"203.0.113.7","port":25201}]}""",
                    org.springframework.test.json.JsonCompareMode.STRICT,
                ),
            )

        mvc
            .perform(
                post(
                    "/api/admin/relays",
                ).with(
                    asAdmin,
                ).contentType(json)
                    .content("""{"id":"eu2","region":"eu","name":"Europe 2","host":"relay-eu2.example.com"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.region").value("EU"))
            .andExpect(jsonPath("$.port").value(25200))
            .andExpect(jsonPath("$.active").value(true))
        mvc
            .perform(
                post(
                    "/api/admin/relays",
                ).with(asAdmin).contentType(json).content("""{"id":"eu2","region":"EU","name":"Again","host":"x.example.com"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("A relay with id \"eu2\" already exists"))

        fun expect400(
            body: String,
            message: String,
        ) = mvc
            .perform(post("/api/admin/relays").with(asAdmin).contentType(json).content(body))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(message))
        val hostMessage = "host must be a DNS name or IP address, without a scheme or port (e.g. relay-eu.example.com)"
        expect400(
            """{"id":"EU 3","region":"EU","name":"n","host":"h.example.com"}""",
            "id must be 1 to 20 characters: a-z, 0-9 and -, starting with a letter or digit (e.g. eu1)",
        )
        expect400("""{"id":"eu3","region":"EU","name":"n","host":"https://h.example.com"}""", hostMessage)
        expect400("""{"id":"eu3","region":"EU","name":"n","host":"h.example.com:25200"}""", hostMessage)
        expect400("""{"id":"eu3","region":"EU","name":"n","host":"h.example.com","port":70000}""", "port must be 1 to 65535")
        expect400("""{"id":"eu3","region":"EU","host":"h.example.com"}""", "name is required")
        mvc
            .perform(
                post(
                    "/api/admin/relays",
                ).with(asAdmin).contentType(json).content("""{"id":"v6","region":"EU","name":"IPv6","host":"2001:db8::7","port":1}"""),
            ).andExpect(status().isOk)

        // Deactivating a relay hides it and its sessions, and nobody can join through it
        val sid = sidOf(create())
        mvc
            .perform(get("/api/admin/relays").with(asAdmin))
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[0].id").value("eu1"))
            .andExpect(jsonPath("$[0].liveSessions").value(1))
        mvc
            .perform(
                put(
                    "/api/admin/relays/eu1",
                ).with(
                    asAdmin,
                ).contentType(json)
                    .content("""{"region":"EU","name":"Europe","host":"relay-eu.example.com","active":false}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.active").value(false))
        mvc.perform(get("/api/relays")).andExpect(jsonPath("$.relays[?(@.id=='eu1')]").isEmpty)
        list().andExpect(jsonPath("$.sessions.length()").value(0))
        join(sid).andExpect(status().isNotFound)
        create().andExpect(status().isBadRequest)
        mvc
            .perform(
                put(
                    "/api/admin/relays/eu1",
                ).with(
                    asAdmin,
                ).contentType(json)
                    .content("""{"region":"EU","name":"Europe","host":"relay-eu.example.com","active":true}"""),
            ).andExpect(status().isOk)
        list().andExpect(jsonPath("$.sessions.length()").value(1))

        // Deleting a relay ends its sessions
        mvc.perform(delete("/api/admin/relays/eu1").with(asAdmin)).andExpect(status().isNoContent)
        mvc.perform(delete("/api/admin/relays/eu1").with(asAdmin)).andExpect(status().isBadRequest)
        assertEquals(0, relaySessionRepository.count())
        heartbeat(sid).andExpect(status().isNotFound)

        // Admins only
        mvc.perform(get("/api/admin/relays")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/admin/relays").with(user(AppUserDetails(editor)))).andExpect(status().isForbidden)
        mvc.perform(delete("/api/admin/relays/na1").with(user(AppUserDetails(editor)))).andExpect(status().isForbidden)
        mvc.perform(get("/api/admin/relays").header(HttpHeaders.AUTHORIZATION, "Bearer $token")).andExpect(status().isForbidden)
    }

    @Test
    fun `tickets expire 12 hours after they are issued`() {
        val sid = sidOf(create())
        setTime(start.plus(Duration.ofSeconds(45)))
        val ticket = field(join(sid).andReturn().response.contentAsString, "clientTicket")
        assertTrue(payloadOf(ticket).endsWith("\"exp\":${start.epochSecond + 45 + 43_200}}"), payloadOf(ticket))
    }

    private companion object {
        val NEXT_IP = AtomicInteger(0)
    }
}
