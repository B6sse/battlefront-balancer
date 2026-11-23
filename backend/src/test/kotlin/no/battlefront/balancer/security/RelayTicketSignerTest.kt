package no.battlefront.balancer.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.Base64

class RelayTicketSignerTest {
    private val signer = RelayTicketSigner("example-secret-0123456789")
    private val example =
        "eyJzaWQiOiJhYmMxMjMiLCJyb2xlIjoiaG9zdCIsInJlbGF5IjoiZXUxIiwibWF4IjoxNiwiZXhwIjoxOTAwMDAwMDAwfQ" +
            ".G_Utc5RW_n5ei6ZFEbZOSH5YS8Ht0bMLIwbSyJMJQfU"

    @Test
    fun `signs the example from the relay specification exactly`() {
        assertEquals(example, signer.sign("""{"sid":"abc123","role":"host","relay":"eu1","max":16,"exp":1900000000}"""))
    }

    @Test
    fun `host ticket builds the example payload byte for byte`() {
        assertEquals(example, signer.hostTicket("abc123", "eu1", 16, 1_900_000_000))
    }

    @Test
    fun `client ticket has the fields in the agreed order and no padding`() {
        val ticket = signer.clientTicket("abc123", "eu1", "0123456789abcdef", 1_900_000_000)
        val payload = String(Base64.getUrlDecoder().decode(ticket.substringBefore('.')))
        assertEquals("""{"sid":"abc123","role":"client","relay":"eu1","cid":"0123456789abcdef","exp":1900000000}""", payload)
        assertEquals(ticket, signer.sign(payload))
        assert(!ticket.contains('=')) { ticket }
    }

    @Test
    fun `values that would need JSON escaping are refused`() {
        assertThrows(IllegalArgumentException::class.java) { signer.hostTicket("a\"b", "eu1", 16, 1) }
    }

    @Test
    fun `a missing or short secret gives no signer and a message without the secret`() {
        assertNull(RelayTickets("").signer)
        assertNull(RelayTickets("   ").signer)
        val short = RelayTickets("too-short-secret")
        assertNull(short.signer)
        assert(!short.problem!!.contains("too-short-secret"))
        assertNotNull(RelayTickets("0123456789abcdef0123456789abcdef").signer)
        assertNull(RelayTickets("0123456789abcdef0123456789abcdef").problem)
    }
}
