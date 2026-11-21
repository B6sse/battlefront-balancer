package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.HostTokenCreateRequest
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.AuricVersionRepository
import no.battlefront.balancer.repository.HostTokenRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * End-to-end test of the Auric version settings through HTTP and the real security rules (H2 database).
 */
@SpringBootTest
@ActiveProfiles("test")
class AuricVersionFlowTest {
    @Autowired private lateinit var context: WebApplicationContext

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var hostTokenRepository: HostTokenRepository

    @Autowired private lateinit var auricVersionRepository: AuricVersionRepository

    @Autowired private lateinit var hostTokenService: HostTokenService

    private lateinit var mvc: MockMvc
    private lateinit var admin: User
    private lateinit var editor: User

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        admin = userRepository.save(User(username = "admin", password = "x", role = "admin"))
        editor = userRepository.save(User(username = "editor", password = "x", role = "editor"))
    }

    @AfterEach
    fun tearDown() {
        auricVersionRepository.deleteAll()
        hostTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun save(body: String) =
        mvc.perform(put("/api/admin/auric-version").with(user(AppUserDetails(admin))).contentType(MediaType.APPLICATION_JSON).content(body))

    private val sha = "76742fb4c30092edd69371f9bf0742c9be2ca1df03fe3ad3452f849596e2b45f"

    @Test
    fun `nothing is public until an admin saves it, then everyone can read it with an ETag`() {
        mvc
            .perform(get("/api/auric/version"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Auric version info has not been set"))
        mvc
            .perform(get("/api/admin/auric-version").with(user(AppUserDetails(admin))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.latestVersion").isEmpty)
            .andExpect(jsonPath("$.updatedAt").isEmpty)

        save(
            """{"latestVersion":" 1.4.0 ","minimumVersion":"1.4.0","downloadUrl":"https://files.example.com/auric-1.4.0.dll",
               "sha256":"${sha.uppercase()}","releaseNotes":"Fixes the scoreboard.\nFaster balancing."}""",
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.latestVersion").value("1.4.0"))
            .andExpect(jsonPath("$.updatedAt").isNotEmpty)

        val etag =
            mvc
                .perform(get("/api/auric/version"))
                .andExpect(status().isOk)
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-cache"))
                .andExpect(jsonPath("$.latestVersion").value("1.4.0"))
                .andExpect(jsonPath("$.minimumVersion").value("1.4.0"))
                .andExpect(jsonPath("$.downloadUrl").value("https://files.example.com/auric-1.4.0.dll"))
                .andExpect(jsonPath("$.sha256").value(sha))
                .andExpect(jsonPath("$.releaseNotes").value("Fixes the scoreboard.\nFaster balancing."))
                .andExpect(jsonPath("$.updatedAt").doesNotExist())
                .andReturn()
                .response
                .getHeader(HttpHeaders.ETAG)!!
        mvc.perform(get("/api/auric/version").header(HttpHeaders.IF_NONE_MATCH, etag)).andExpect(status().isNotModified)

        // Optional fields can be cleared; a second save replaces the row
        save("""{"latestVersion":"1.10.0","minimumVersion":"1.9.0","downloadUrl":"","sha256":null}""").andExpect(status().isOk)
        mvc
            .perform(get("/api/auric/version").header(HttpHeaders.IF_NONE_MATCH, etag))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.latestVersion").value("1.10.0"))
            .andExpect(jsonPath("$.downloadUrl").isEmpty)
            .andExpect(jsonPath("$.sha256").isEmpty)
            .andExpect(jsonPath("$.releaseNotes").isEmpty)
        assert(auricVersionRepository.count() == 1L)
    }

    @Test
    fun `invalid versions, URLs and hashes are rejected with a clear message`() {
        fun expect400(
            body: String,
            message: String,
        ) = save(body).andExpect(status().isBadRequest).andExpect(jsonPath("$.message").value(message))

        val format = "must be three dot-separated numbers, e.g. 1.4.0"
        expect400("""{"latestVersion":"1.4","minimumVersion":"1.4.0"}""", "latestVersion $format")
        expect400("""{"latestVersion":"1.4.0","minimumVersion":"1.4.0.1"}""", "minimumVersion $format")
        expect400("""{"latestVersion":"v1.4.0","minimumVersion":"1.4.0"}""", "latestVersion $format")
        expect400("""{"latestVersion":"1.4.0","minimumVersion":"1.-4.0"}""", "minimumVersion $format")
        expect400("""{"minimumVersion":"1.4.0"}""", "latestVersion is required")
        // Compared as numbers, not text: 1.10.0 > 1.9.0
        expect400(
            """{"latestVersion":"1.9.0","minimumVersion":"1.10.0"}""",
            "minimumVersion (1.10.0) must not be higher than latestVersion (1.9.0)",
        )
        expect400(
            """{"latestVersion":"1.4.0","minimumVersion":"1.4.0","downloadUrl":"http://x.example.com/a.dll"}""",
            "downloadUrl must be an https:// URL",
        )
        expect400("""{"latestVersion":"1.4.0","minimumVersion":"1.4.0","sha256":"abc"}""", "sha256 must be 64 hex characters (SHA-256)")
        expect400(
            """{"latestVersion":"1.4.0","minimumVersion":"1.4.0","releaseNotes":"${"x".repeat(2001)}"}""",
            "releaseNotes max 2000 characters",
        )
        assert(auricVersionRepository.count() == 0L)
    }

    @Test
    fun `only admins edit, and the public endpoint works with a host token too`() {
        save("""{"latestVersion":"1.4.0","minimumVersion":"1.3.0"}""").andExpect(status().isOk)
        val token = hostTokenService.createToken(HostTokenCreateRequest(name = "Host", userId = editor.id)).token

        mvc.perform(get("/api/admin/auric-version")).andExpect(status().isUnauthorized)
        mvc.perform(get("/api/admin/auric-version").with(user(AppUserDetails(editor)))).andExpect(status().isForbidden)
        mvc
            .perform(
                put("/api/admin/auric-version")
                    .with(user(AppUserDetails(editor)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"latestVersion":"9.9.9","minimumVersion":"9.9.9"}"""),
            ).andExpect(status().isForbidden)
        mvc.perform(get("/api/admin/auric-version").header(HttpHeaders.AUTHORIZATION, "Bearer $token")).andExpect(status().isForbidden)

        mvc
            .perform(get("/api/auric/version").header(HttpHeaders.AUTHORIZATION, "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.minimumVersion").value("1.3.0"))
    }
}
