package no.battlefront.balancer.security

import no.battlefront.balancer.dto.HostTokenCreateRequest
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.HostTokenRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.service.HostTokenService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Integration test of the security rules for host tokens, through the real filter chain (H2 database).
 */
@SpringBootTest
@ActiveProfiles("test")
class HostTokenSecurityTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var hostTokenRepository: HostTokenRepository

    @Autowired
    private lateinit var hostTokenService: HostTokenService

    private lateinit var mvc: MockMvc
    private lateinit var admin: User
    private lateinit var supervisor: User

    @BeforeEach
    fun setUp() {
        mvc =
            MockMvcBuilders
                .webAppContextSetup(
                    context,
                ).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
                .build()
        admin = userRepository.save(User(username = "admin", password = "x", role = "admin"))
        supervisor = userRepository.save(User(username = "purpoz", password = "x", role = "supervisor"))
    }

    @AfterEach
    fun tearDown() {
        hostTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun newToken(): String = hostTokenService.createToken(HostTokenCreateRequest(name = "Host", userId = supervisor.id)).token

    private fun bearer(token: String) = "Bearer $token"

    @Test
    fun `invalid bearer token is rejected with 401 even on public endpoints`() {
        mvc
            .perform(get("/api/players").header(HttpHeaders.AUTHORIZATION, bearer("not-a-token")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Invalid or revoked host token"))
    }

    @Test
    fun `host token can use host endpoints`() {
        val token = newToken()

        mvc.perform(get("/api/players").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk)
        mvc
            .perform(get("/api/players/by-persona").param("ids", "1,2").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk)
        // 422 = got past security; the persona IDs are simply not registered
        mvc
            .perform(
                post("/api/balance")
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"personaIds":[1,2]}"""),
            ).andExpect(status().isUnprocessableContent)
    }

    @Test
    fun `host token gets no admin or supervisor rights and only the host endpoints`() {
        val token = newToken()

        mvc.perform(get("/api/matches").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isForbidden)

        mvc.perform(get("/api/admin/host-tokens").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isForbidden)
        mvc.perform(get("/api/admin/users").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isForbidden)
        mvc
            .perform(
                post("/api/matches").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{}"),
            ).andExpect(status().isForbidden)
        mvc
            .perform(
                post("/api/players").header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{}"),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `host token request does not create a session`() {
        val result = mvc.perform(get("/api/players").header(HttpHeaders.AUTHORIZATION, bearer(newToken()))).andReturn()

        assertNull(result.request.getSession(false))
    }

    @Test
    fun `admin can create, list and revoke tokens, and a revoked token stops working`() {
        val adminUser = user(AppUserDetails(admin))
        val created =
            mvc
                .perform(
                    post("/api/admin/host-tokens")
                        .with(adminUser)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"name":"Purpoz PC","userId":${supervisor.id}}"""),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.username").value("purpoz"))
                .andReturn()
        val body = created.response.contentAsString
        val token = Regex("\"token\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        val id = Regex("\"id\":(\\d+)").find(body)!!.groupValues[1]

        val list =
            mvc
                .perform(get("/api/admin/host-tokens").with(adminUser))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].name").value("Purpoz PC"))
                .andReturn()
        assertFalse(list.response.contentAsString.contains(token))

        mvc.perform(get("/api/players").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk)
        mvc.perform(delete("/api/admin/host-tokens/$id").with(adminUser)).andExpect(status().isNoContent)
        mvc.perform(get("/api/players").header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isUnauthorized)
    }

    @Test
    fun `supervisor session cannot manage tokens`() {
        mvc.perform(get("/api/admin/host-tokens").with(user(AppUserDetails(supervisor)))).andExpect(status().isForbidden)
    }
}
