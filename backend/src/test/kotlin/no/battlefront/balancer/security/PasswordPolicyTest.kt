package no.battlefront.balancer.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class PasswordPolicyTest {
    @Test
    fun `accepts any password of at least 10 characters`() {
        assertDoesNotThrow { PasswordPolicy.requireValid("battlefront") }
        assertDoesNotThrow { PasswordPolicy.requireValid("1234567890") }
        assertDoesNotThrow { PasswordPolicy.requireValid("rebel scum dz") }
    }

    @Test
    fun `rejects short, blank-padded and overly long passwords`() {
        listOf(
            "Short!a",
            "   short   ",
            "x".repeat(73),
        ).forEach { password ->
            assertThrows<IllegalArgumentException>(password) { PasswordPolicy.requireValid(password) }
        }
    }

    @Test
    fun `validates usernames`() {
        assertDoesNotThrow { PasswordPolicy.requireValidUsername("new_sup.1") }
        listOf("ab", "has space", "x".repeat(33), "<script>").forEach { username ->
            assertThrows<IllegalArgumentException>(username) { PasswordPolicy.requireValidUsername(username) }
        }
    }
}
