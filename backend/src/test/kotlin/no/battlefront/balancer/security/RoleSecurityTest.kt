package no.battlefront.balancer.security

import no.battlefront.balancer.model.User
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Integration test of the role hierarchy: supervisor < editor < admin.
 * Only checks that security lets the request through (not 401/403); the request bodies are deliberately empty.
 */
@SpringBootTest
@ActiveProfiles("test")
class RoleSecurityTest {
    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
    }

    private fun session(role: String) = user(AppUserDetails(User(id = 1L, username = role, password = "x", role = role)))

    private fun postJson(path: String) = post(path).contentType(MediaType.APPLICATION_JSON).content("{}")

    private fun assertAllowed(
        role: String,
        path: String,
    ) {
        val code =
            mvc
                .perform(postJson(path).with(session(role)))
                .andReturn()
                .response.status
        assertTrue(code != 401 && code != 403, "$role should be allowed to POST $path, got $code")
    }

    @Test
    fun `supervisor, editor and admin can submit matches`() {
        listOf("supervisor", "editor", "admin").forEach { assertAllowed(it, "/api/matches") }
    }

    @Test
    fun `editor and admin can create players, supervisor cannot`() {
        assertAllowed("editor", "/api/players")
        assertAllowed("admin", "/api/players")
        mvc.perform(postJson("/api/players").with(session("supervisor"))).andExpect(status().isForbidden)
    }

    @Test
    fun `only admin can manage users and host tokens`() {
        listOf("supervisor", "editor").forEach { role ->
            mvc.perform(get("/api/admin/users").with(session(role))).andExpect(status().isForbidden)
            mvc.perform(get("/api/admin/host-tokens").with(session(role))).andExpect(status().isForbidden)
        }
        mvc.perform(get("/api/admin/host-tokens").with(session("admin"))).andExpect(status().isOk)
    }
}
