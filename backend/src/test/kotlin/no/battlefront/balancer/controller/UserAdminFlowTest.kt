package no.battlefront.balancer.controller

import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.RecoveryCodeRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.AppUserDetails
import no.battlefront.balancer.security.Totp
import no.battlefront.balancer.service.TwoFactorService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Integration test of user management by admins (H2, real security rules).
 */
@SpringBootTest
@ActiveProfiles("test")
class UserAdminFlowTest {
    @Autowired private lateinit var context: WebApplicationContext

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var recoveryCodeRepository: RecoveryCodeRepository

    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var twoFactorService: TwoFactorService

    private lateinit var mvc: MockMvc
    private lateinit var admin: User
    private lateinit var otherAdmin: User
    private lateinit var editor: User
    private val now = 1_800_000_000L
    private val adminSecret = Totp.generateSecret()

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        twoFactorService.clock = Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC)
        admin =
            userRepository.save(
                User(
                    username = "boss",
                    password = passwordEncoder.encode(OLD)!!,
                    role = "admin",
                    totpSecret = adminSecret,
                    totpEnabled = true,
                ),
            )
        otherAdmin = userRepository.save(User(username = "boss2", password = passwordEncoder.encode(OLD)!!, role = "admin"))
        editor =
            userRepository.save(
                User(
                    username = "ed",
                    password = passwordEncoder.encode(OLD)!!,
                    role = "editor",
                    totpSecret = Totp.generateSecret(),
                    totpEnabled = true,
                ),
            )
    }

    @AfterEach
    fun tearDown() {
        recoveryCodeRepository.deleteAll()
        userRepository.deleteAll()
        twoFactorService.clock = Clock.systemUTC()
    }

    private fun asAdmin(request: MockHttpServletRequestBuilder) =
        mvc.perform(request.with(user(AppUserDetails(admin))).contentType(MediaType.APPLICATION_JSON))

    private fun adminCode() = Totp.codeAt(Totp.base32Decode(adminSecret), now / Totp.PERIOD_SECONDS)

    @Test
    fun `admin creates supervisors and editors with a valid password, but not admins`() {
        asAdmin(post("/api/admin/users").content("""{"username":"newsup","password":"Battlefront!","role":"supervisor"}"""))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("supervisor"))
            .andExpect(jsonPath("$.twoFactorEnabled").value(false))
        assertTrue(passwordEncoder.matches("Battlefront!", userRepository.findByUsername("newsup")!!.password))

        asAdmin(
            post("/api/admin/users").content("""{"username":"newadmin","password":"Battlefront!","role":"admin"}"""),
        ).andExpect(status().isBadRequest)
        asAdmin(post("/api/admin/users").content("""{"username":"newsup","password":"Battlefront!","role":"editor"}"""))
            .andExpect(jsonPath("$.message").value("Username is already taken"))
        asAdmin(post("/api/admin/users").content("""{"username":"weak","password":"tooshort","role":"editor"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Password must be at least 10 characters")))
    }

    @Test
    fun `admin sets passwords for editors and supervisors, but not for admins or themselves`() {
        asAdmin(put("/api/admin/users/${editor.id}/password").content("""{"password":"New-password1"}""")).andExpect(status().isNoContent)
        assertTrue(passwordEncoder.matches("New-password1", userRepository.findById(editor.id).get().password))

        asAdmin(put("/api/admin/users/${otherAdmin.id}/password").content("""{"password":"New-password1"}"""))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Admin accounts cannot be changed by other admins"))
        asAdmin(put("/api/admin/users/${admin.id}/password").content("""{"password":"New-password1"}""")).andExpect(status().isBadRequest)
        asAdmin(put("/api/admin/users/${editor.id}/password").content("""{"password":"short"}""")).andExpect(status().isBadRequest)
    }

    @Test
    fun `admin resets an editor's two-factor login`() {
        asAdmin(post("/api/admin/users/${editor.id}/reset-2fa")).andExpect(status().isNoContent)
        val reset = userRepository.findById(editor.id).get()
        assertFalse(reset.totpEnabled)
        asAdmin(get("/api/admin/users")).andExpect(jsonPath("$[?(@.username == 'ed')].twoFactorEnabled").value(false))
        asAdmin(post("/api/admin/users/${otherAdmin.id}/reset-2fa")).andExpect(status().isBadRequest)
    }

    @Test
    fun `admin changes their own password with the current password and an app code`() {
        asAdmin(
            put(
                "/api/admin/account/password",
            ).content("""{"currentPassword":"wrong","newPassword":"New-password1","code":"${adminCode()}"}"""),
        ).andExpect(jsonPath("$.message").value("Current password is incorrect"))
        asAdmin(put("/api/admin/account/password").content("""{"currentPassword":"$OLD","newPassword":"New-password1","code":"000000"}"""))
            .andExpect(jsonPath("$.message").value("Invalid two-factor code"))
        asAdmin(
            put(
                "/api/admin/account/password",
            ).content("""{"currentPassword":"$OLD","newPassword":"New-password1","code":"${adminCode()}"}"""),
        ).andExpect(status().isNoContent)
        assertTrue(passwordEncoder.matches("New-password1", userRepository.findById(admin.id).get().password))
    }

    @Test
    fun `editors and supervisors cannot manage users or passwords`() {
        val editorSession = user(AppUserDetails(editor))
        mvc
            .perform(
                post("/api/admin/users").with(editorSession).contentType(MediaType.APPLICATION_JSON).content("{}"),
            ).andExpect(status().isForbidden)
        mvc
            .perform(
                put("/api/admin/account/password").with(editorSession).contentType(MediaType.APPLICATION_JSON).content("{}"),
            ).andExpect(status().isForbidden)
        mvc
            .perform(
                put("/api/admin/users/${editor.id}/password").with(editorSession).contentType(MediaType.APPLICATION_JSON).content("{}"),
            ).andExpect(status().isForbidden)
    }

    private companion object {
        const val OLD = "Old-password1"
    }
}
