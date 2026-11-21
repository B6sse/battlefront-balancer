package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.HostTokenCreateRequest
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.ApprovedModRepository
import no.battlefront.balancer.repository.ApprovedModsStateRepository
import no.battlefront.balancer.repository.HostTokenRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.AppUserDetails
import no.battlefront.balancer.service.HostTokenService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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

/**
 * End-to-end test of the approved mods list through HTTP and the real security rules (H2 database).
 */
@SpringBootTest
@ActiveProfiles("test")
class ApprovedModFlowTest {
    @Autowired private lateinit var context: WebApplicationContext

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var hostTokenRepository: HostTokenRepository

    @Autowired private lateinit var approvedModRepository: ApprovedModRepository

    @Autowired private lateinit var stateRepository: ApprovedModsStateRepository

    @Autowired private lateinit var hostTokenService: HostTokenService

    private lateinit var mvc: MockMvc
    private lateinit var admin: User
    private lateinit var editor: User
    private lateinit var token: String

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        admin = userRepository.save(User(username = "admin", password = "x", role = "admin"))
        editor = userRepository.save(User(username = "editor", password = "x", role = "editor"))
        token = hostTokenService.createToken(HostTokenCreateRequest(name = "Host", userId = editor.id)).token
    }

    @AfterEach
    fun tearDown() {
        approvedModRepository.deleteAll()
        stateRepository.deleteAll()
        hostTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    /** What the signing tool prints. */
    private val signed =
        """
        { "title": "Improved Scoreboard", "version": "1.3", "author": "ZEROW", "category": "OPTIONAL",
          "contentSha256": "4507eb7297053ffb38a65228158189de3260e411e603401b0c1ecb2542b2af76", "contentSize": 74992,
          "keyId": "2026-11", "axSha256": "76742fb4c30092edd69371f9bf0742c9be2ca1df03fe3ad3452f849596e2b45f", "axSize": 75321 }
        """.trimIndent()

    private val otherHash = "a".repeat(64)

    private fun withExtra(extra: String) = signed.trimEnd().removeSuffix("}") + ", $extra }"

    private fun create(body: String) =
        mvc.perform(post("/api/admin/mods").with(user(AppUserDetails(admin))).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun update(
        id: Long,
        body: String,
    ) = mvc.perform(put("/api/admin/mods/$id").with(user(AppUserDetails(admin))).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun idOf(body: String): Long = Regex("\"id\":(\\d+)").find(body)!!.groupValues[1].toLong()

    @Test
    fun `admin pastes the signing tool JSON with a link and notes, and the public list shows it without notes`() {
        val created =
            create(withExtra(""""downloadUrl": "https://files.example.com/scoreboard.ax", "notes": "Checked by B6sse""""))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.notes").value("Checked by B6sse"))
                .andReturn()
                .response.contentAsString
        assertNotNull(idOf(created))

        mvc
            .perform(get("/api/mods/approved"))
            .andExpect(status().isOk)
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
            .andExpect(header().exists(HttpHeaders.ETAG))
            .andExpect(jsonPath("$.updatedAt").value(org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ")))
            .andExpect(jsonPath("$.mods.length()").value(1))
            .andExpect(jsonPath("$.mods[0].title").value("Improved Scoreboard"))
            .andExpect(jsonPath("$.mods[0].version").value("1.3"))
            .andExpect(jsonPath("$.mods[0].author").value("ZEROW"))
            .andExpect(jsonPath("$.mods[0].category").value("OPTIONAL"))
            .andExpect(jsonPath("$.mods[0].contentSha256").value("4507eb7297053ffb38a65228158189de3260e411e603401b0c1ecb2542b2af76"))
            .andExpect(jsonPath("$.mods[0].contentSize").value(74992))
            .andExpect(jsonPath("$.mods[0].keyId").value("2026-11"))
            .andExpect(jsonPath("$.mods[0].axSha256").value("76742fb4c30092edd69371f9bf0742c9be2ca1df03fe3ad3452f849596e2b45f"))
            .andExpect(jsonPath("$.mods[0].axSize").value(75321))
            .andExpect(jsonPath("$.mods[0].downloadUrl").value("https://files.example.com/scoreboard.ax"))
            .andExpect(jsonPath("$.mods[0].notes").doesNotExist())
            .andExpect(jsonPath("$.mods[0].id").doesNotExist())
            .andExpect(jsonPath("$.mods[0].active").doesNotExist())
    }

    @Test
    fun `downloadUrl is null when there is none, and upper-case hashes are stored in lower case`() {
        create(signed.replace("4507eb72", "4507EB72")).andExpect(status().isOk)
        val body =
            mvc
                .perform(get("/api/mods/approved"))
                .andReturn()
                .response.contentAsString
        assert(body.contains("\"downloadUrl\":null")) { body }
        assert(body.contains("\"contentSha256\":\"4507eb7297053ffb")) { body }
    }

    @Test
    fun `ETag is honoured with 304 and changes when the list changes`() {
        val id = idOf(create(signed).andReturn().response.contentAsString)
        val etag =
            mvc
                .perform(get("/api/mods/approved"))
                .andReturn()
                .response
                .getHeader(HttpHeaders.ETAG)!!

        mvc
            .perform(get("/api/mods/approved").header(HttpHeaders.IF_NONE_MATCH, etag))
            .andExpect(status().isNotModified)
            .andExpect(header().string(HttpHeaders.ETAG, etag))
            .andExpect(content().string(""))
        // A proxy may weaken the ETag; If-None-Match uses weak comparison
        mvc.perform(get("/api/mods/approved").header(HttpHeaders.IF_NONE_MATCH, "W/$etag")).andExpect(status().isNotModified)
        mvc.perform(get("/api/mods/approved").header(HttpHeaders.IF_NONE_MATCH, "\"something-else\"")).andExpect(status().isOk)

        update(id, withExtra(""""downloadUrl": "https://files.example.com/v2.ax"""")).andExpect(status().isOk)
        val etagAfterEdit =
            mvc
                .perform(get("/api/mods/approved").header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.mods[0].downloadUrl").value("https://files.example.com/v2.ax"))
                .andReturn()
                .response
                .getHeader(HttpHeaders.ETAG)!!
        assertNotEquals(etag, etagAfterEdit)

        mvc.perform(delete("/api/admin/mods/$id").with(user(AppUserDetails(admin)))).andExpect(status().isNoContent)
        mvc
            .perform(get("/api/mods/approved").header(HttpHeaders.IF_NONE_MATCH, etagAfterEdit))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.mods.length()").value(0))
        assertEquals(0, approvedModRepository.count())
    }

    @Test
    fun `inactive mods are not public, and reactivating brings them back`() {
        val id = idOf(create(signed).andReturn().response.contentAsString)
        update(id, withExtra(""""active": false""")).andExpect(status().isOk).andExpect(jsonPath("$.active").value(false))
        mvc.perform(get("/api/mods/approved")).andExpect(jsonPath("$.mods.length()").value(0))
        mvc
            .perform(get("/api/admin/mods").with(user(AppUserDetails(admin))))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].active").value(false))

        // Editing without "active" keeps it inactive
        update(id, signed.replace("\"1.3\"", "\"1.3.1\"")).andExpect(jsonPath("$.active").value(false))
        update(id, withExtra(""""active": true""")).andExpect(jsonPath("$.active").value(true))
        mvc.perform(get("/api/mods/approved")).andExpect(jsonPath("$.mods.length()").value(1))
    }

    @Test
    fun `invalid fields are rejected with a clear message`() {
        fun expect400(
            body: String,
            message: String,
        ) = create(body).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value(message))

        expect400(signed.replace("4507eb72", "4507eb7"), "contentSha256 must be 64 hex characters (SHA-256)")
        expect400(signed.replace("4507eb72", "4507eg72"), "contentSha256 must be 64 hex characters (SHA-256)")
        expect400(signed.replace("76742fb4", "76742fb"), "axSha256 must be 64 hex characters (SHA-256)")
        expect400(signed.replace("\"OPTIONAL\"", "\"REQUIRED\""), "Unknown category \"REQUIRED\": must be OPTIONAL or MUST_MATCH_HOST")
        expect400(signed.replace("\"OPTIONAL\"", "\"optional\""), "Unknown category \"optional\": must be OPTIONAL or MUST_MATCH_HOST")
        expect400(withExtra(""""downloadUrl": "http://files.example.com/a.ax""""), "downloadUrl must be an https:// URL")
        expect400(withExtra(""""downloadUrl": "files.example.com/a.ax""""), "downloadUrl must be an https:// URL")
        expect400(withExtra(""""downloadUrl": "https://""""), "downloadUrl must be an https:// URL")
        expect400(signed.replace("\"title\": \"Improved Scoreboard\",", ""), "title is required")
        expect400(signed.replace("74992", "0"), "contentSize must be a positive number of bytes")
        expect400(signed.replace(", \"axSize\": 75321", ""), "axSize is required")
        assertEquals(0, approvedModRepository.count())
    }

    @Test
    fun `a duplicate contentSha256 is rejected with 409, also when editing another mod`() {
        val first = idOf(create(signed).andReturn().response.contentAsString)
        create(signed.replace("4507EB72".lowercase(), "4507EB72"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("A mod with this contentSha256 is already in the list: Improved Scoreboard 1.3"))
            .andExpect(jsonPath("$.existingId").value(first))

        val second =
            idOf(
                create(signed.replace("4507eb7297053ffb38a65228158189de3260e411e603401b0c1ecb2542b2af76", otherHash))
                    .andReturn()
                    .response.contentAsString,
            )
        update(second, signed).andExpect(status().isConflict)
        // Saving a mod with its own hash is fine
        update(first, signed).andExpect(status().isOk)
        update(999_999, signed).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value("Mod not found"))
    }

    @Test
    fun `only admins manage mods, and everyone may read the public list`() {
        mvc.perform(get("/api/admin/mods")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/admin/mods").with(user(AppUserDetails(editor)))).andExpect(status().isForbidden)
        mvc
            .perform(post("/api/admin/mods").with(user(AppUserDetails(editor))).contentType(MediaType.APPLICATION_JSON).content(signed))
            .andExpect(status().isForbidden)
        mvc.perform(delete("/api/admin/mods/1").with(user(AppUserDetails(editor)))).andExpect(status().isForbidden)
        mvc
            .perform(get("/api/admin/mods").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isForbidden)

        mvc.perform(get("/api/mods/approved")).andExpect(status().isOk)
        mvc.perform(get("/api/mods/approved").header(HttpHeaders.AUTHORIZATION, "Bearer $token")).andExpect(status().isOk)
        mvc.perform(get("/api/mods/approved").with(user(AppUserDetails(editor)))).andExpect(status().isOk)
    }
}
