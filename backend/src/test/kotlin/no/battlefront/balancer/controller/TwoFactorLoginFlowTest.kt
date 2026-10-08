package no.battlefront.balancer.controller

import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.RecoveryCodeRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.Totp
import no.battlefront.balancer.service.TwoFactorService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * End-to-end test of login with two-factor authentication through HTTP, sessions and the security rules (H2).
 */
@SpringBootTest
@ActiveProfiles("test")
class TwoFactorLoginFlowTest {
    @Autowired private lateinit var context: WebApplicationContext

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var recoveryCodeRepository: RecoveryCodeRepository

    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var twoFactorService: TwoFactorService

    private lateinit var mvc: MockMvc
    private var now = 1_800_000_000L

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        setClock(now)
        listOf("sup" to "supervisor", "ed" to "editor", "boss" to "admin").forEach { (name, role) ->
            userRepository.save(User(username = name, password = passwordEncoder.encode(PASSWORD)!!, role = role))
        }
    }

    @AfterEach
    fun tearDown() {
        recoveryCodeRepository.deleteAll()
        userRepository.deleteAll()
        twoFactorService.clock = Clock.systemUTC()
    }

    private fun setClock(epochSeconds: Long) {
        now = epochSeconds
        twoFactorService.clock = Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC)
    }

    /** Keeps the current session like a browser keeps its cookie, also when the server replaces the session. */
    private class Browser {
        var session = MockHttpSession()
    }

    private fun send(
        browser: Browser,
        request: MockHttpServletRequestBuilder,
    ): ResultActions {
        val actions = mvc.perform(request.session(browser.session))
        (actions.andReturn().request.getSession(false) as MockHttpSession?)?.let { browser.session = it }
        return actions
    }

    private fun json(
        path: String,
        body: String,
        session: Browser,
    ): ResultActions = send(session, post(path).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun login(
        username: String,
        session: Browser,
        password: String = PASSWORD,
    ) = json("/api/login", """{"username":"$username","password":"$password"}""", session)

    private fun code(secret: String) = Totp.codeAt(Totp.base32Decode(secret), now / Totp.PERIOD_SECONDS)

    private fun me(session: Browser) = send(session, get("/api/me"))

    /** Runs the first-login setup for [username] and returns the secret and recovery codes. */
    private fun setUpTwoFactor(username: String): Pair<String, List<String>> {
        val session = Browser()
        login(username, session).andExpect(jsonPath("$.status").value("TOTP_SETUP_REQUIRED"))
        val setup = json("/api/login/2fa/setup", "", session).andExpect(status().isOk).andReturn()
        val secret = Regex("\"secret\":\"([A-Z2-7]+)\"").find(setup.response.contentAsString)!!.groupValues[1]
        val confirmed =
            json("/api/login/2fa/setup/confirm", """{"code":"${code(secret)}"}""", session)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.recoveryCodes.length()").value(10))
                .andReturn()
        val codes = Regex("\"([a-z2-9]{4}-[a-z2-9]{4})\"").findAll(confirmed.response.contentAsString).map { it.groupValues[1] }.toList()
        me(session).andExpect(status().isOk).andExpect(jsonPath("$.username").value(username))
        return secret to codes
    }

    @Test
    fun `supervisor logs in with password only`() {
        val session = Browser()
        login(
            "sup",
            session,
        ).andExpect(status().isOk).andExpect(jsonPath("$.status").value("OK")).andExpect(jsonPath("$.user.role").value("supervisor"))
        me(session).andExpect(status().isOk)
    }

    @Test
    fun `wrong password is rejected`() {
        login("ed", Browser(), password = "Wrong-password1").andExpect(status().isUnauthorized)
    }

    @Test
    fun `editor must set up two-factor and is not logged in until then`() {
        val session = Browser()
        login("ed", session).andExpect(status().isOk).andExpect(jsonPath("$.status").value("TOTP_SETUP_REQUIRED"))
        me(session).andExpect(status().isUnauthorized)
        json("/api/players", "{}", session).andExpect(status().isUnauthorized)

        val setup =
            json("/api/login/2fa/setup", "", session)
                .andExpect(status().isOk)
                .andReturn()
                .response.contentAsString
        assertTrue(setup.contains("otpauth://totp/Battlefront%20Balancer:ed"), setup)
        // The same secret on repeated calls during one login
        assertEquals(setup, json("/api/login/2fa/setup", "", session).andReturn().response.contentAsString)

        json("/api/login/2fa/setup/confirm", """{"code":"000000"}""", session).andExpect(status().isUnauthorized)
        me(session).andExpect(status().isUnauthorized)

        setUpTwoFactor("ed")
        assertTrue(userRepository.findByUsername("ed")!!.totpEnabled)
    }

    @Test
    fun `admin with two-factor logs in with an app code, and a code cannot be reused`() {
        val (secret, _) = setUpTwoFactor("boss")
        setClock(now + 60)
        val codeNow = code(secret)

        val session = Browser()
        login("boss", session).andExpect(jsonPath("$.status").value("TOTP_REQUIRED"))
        me(session).andExpect(status().isUnauthorized)
        json(
            "/api/login/2fa",
            """{"code":"$codeNow"}""",
            session,
        ).andExpect(status().isOk).andExpect(jsonPath("$.user.role").value("admin"))
        me(session).andExpect(status().isOk)
        send(session, get("/api/admin/users")).andExpect(status().isOk)

        val second = Browser()
        login("boss", second)
        json("/api/login/2fa", """{"code":"$codeNow"}""", second).andExpect(status().isUnauthorized)
    }

    @Test
    fun `recovery codes work once`() {
        val (_, recoveryCodes) = setUpTwoFactor("ed")

        val session = Browser()
        login("ed", session)
        json("/api/login/2fa", """{"code":"${recoveryCodes[0].uppercase()}"}""", session).andExpect(status().isOk)

        val again = Browser()
        login("ed", again)
        json("/api/login/2fa", """{"code":"${recoveryCodes[0]}"}""", again).andExpect(status().isUnauthorized)
        json("/api/login/2fa", """{"code":"${recoveryCodes[1]}"}""", again).andExpect(status().isOk)
    }

    @Test
    fun `too many wrong codes ends the pending login`() {
        val (secret, _) = setUpTwoFactor("ed")
        setClock(now + 60)

        val session = Browser()
        login("ed", session)
        repeat(4) { json("/api/login/2fa", """{"code":"000000"}""", session).andExpect(jsonPath("$.message").value("Invalid code")) }
        json(
            "/api/login/2fa",
            """{"code":"000000"}""",
            session,
        ).andExpect(jsonPath("$.message").value("Too many wrong codes. Log in again."))
        json("/api/login/2fa", """{"code":"${code(secret)}"}""", session)
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Your login has expired. Log in again."))
    }

    @Test
    fun `two-factor steps without a pending login are rejected`() {
        json("/api/login/2fa", """{"code":"123456"}""", Browser()).andExpect(status().isUnauthorized)
        json("/api/login/2fa/setup", "", Browser()).andExpect(status().isUnauthorized)
    }

    private companion object {
        const val PASSWORD = "Battlefront!"
    }
}
