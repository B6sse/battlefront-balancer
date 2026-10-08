package no.battlefront.balancer.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TotpTest {
    // RFC 6238 appendix B: SHA1 key is the ASCII string "12345678901234567890"; expected 8-digit values, of which
    // authenticator apps show the last 6 digits.
    private val rfcSecret = "12345678901234567890".toByteArray()
    private val rfcBase32 = Totp.base32Encode(rfcSecret)

    @Test
    fun `codes match the RFC 6238 test vectors`() {
        val vectors =
            mapOf(
                59L to "287082",
                1111111109L to "081804",
                1111111111L to "050471",
                1234567890L to "005924",
                2000000000L to "279037",
                20000000000L to "353130",
            )
        vectors.forEach { (time, expected) -> assertEquals(expected, Totp.codeAt(rfcSecret, time / 30), "time $time") }
    }

    @Test
    fun `base32 matches RFC 4648 and round-trips`() {
        assertEquals("MZXW6YTBOI", Totp.base32Encode("foobar".toByteArray()))
        assertEquals("foobar", String(Totp.base32Decode("mzxw6ytboi======")))
        val secret = Totp.generateSecret()
        assertEquals(32, secret.length)
        assertEquals(secret, Totp.base32Encode(Totp.base32Decode(secret)))
    }

    @Test
    fun `accepts codes one step either side and returns the step`() {
        val now = 1234567890L
        val step = now / 30
        assertEquals(step, Totp.matchingStep(rfcBase32, Totp.codeAt(rfcSecret, step), now, null))
        assertEquals(step - 1, Totp.matchingStep(rfcBase32, Totp.codeAt(rfcSecret, step - 1), now, null))
        assertEquals(step + 1, Totp.matchingStep(rfcBase32, Totp.codeAt(rfcSecret, step + 1), now, null))
        assertNull(Totp.matchingStep(rfcBase32, Totp.codeAt(rfcSecret, step - 2), now, null))
    }

    @Test
    fun `rejects reused codes and malformed input`() {
        val now = 1234567890L
        val step = now / 30
        val code = Totp.codeAt(rfcSecret, step)
        assertNull(Totp.matchingStep(rfcBase32, code, now, lastUsedStep = step))
        assertNull(Totp.matchingStep(rfcBase32, "12345", now, null))
        assertNull(Totp.matchingStep(rfcBase32, "abcdef", now, null))
    }

    @Test
    fun `otpauth uri carries issuer, account and secret`() {
        val uri = Totp.otpauthUri("Battlefront Balancer", "Basse", "ABC")
        assertTrue(uri.startsWith("otpauth://totp/Battlefront%20Balancer:Basse?secret=ABC&issuer=Battlefront%20Balancer"), uri)
    }
}
