package no.battlefront.balancer.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class PasswordPolicyTest {
    @Test
    fun `accepts a password with length, upper, lower and special character`() {
        assertDoesNotThrow { PasswordPolicy.requireValid("Battlefront!") }
        assertDoesNotThrow { PasswordPolicy.requireValid("ÆøåKode#2026") }
    }

    @Test
    fun `rejects passwords missing a rule`() {
        listOf(
            "Short!a",
            "alllowercase!",
            "ALLUPPERCASE!",
            "NoSpecialChars1",
            "Spaces Only Aa",
            "Aa!" + "x".repeat(70),
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
