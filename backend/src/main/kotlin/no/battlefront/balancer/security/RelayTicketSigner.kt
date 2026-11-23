package no.battlefront.balancer.security

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signs relay tickets: `base64url(payload) + "." + base64url(HMAC-SHA256(secret, payload))`, base64url without
 * padding. The payload is UTF-8 JSON, and the HMAC is over exactly the bytes that are encoded. The relay checks
 * tickets with the same secret.
 *
 * @param secret the shared secret; its UTF-8 bytes are the HMAC key
 */
class RelayTicketSigner(
    secret: String,
) {
    private val key = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM)

    fun sign(payload: String): String {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(key)
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return encoder.encodeToString(bytes) + "." + encoder.encodeToString(mac.doFinal(bytes))
    }

    /** Ticket for the host of session [sid] on [relayId]; [max] is the session's player limit, [exp] Unix seconds. */
    fun hostTicket(
        sid: String,
        relayId: String,
        max: Int,
        exp: Long,
    ): String = sign("""{"sid":"${plain(sid)}","role":"host","relay":"${plain(relayId)}","max":$max,"exp":$exp}""")

    /** Ticket for one player joining session [sid]; [cid] is random and new for every join. */
    fun clientTicket(
        sid: String,
        relayId: String,
        cid: String,
        exp: Long,
    ): String = sign("""{"sid":"${plain(sid)}","role":"client","relay":"${plain(relayId)}","cid":"${plain(cid)}","exp":$exp}""")

    /** The payload is written by hand to fix its exact bytes, so values must not need JSON escaping. */
    private fun plain(value: String): String {
        require(PLAIN_RE.matches(value)) { "Ticket values may only contain a-z, 0-9 and -" }
        return value
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        val PLAIN_RE = Regex("^[a-z0-9-]+$")
    }
}

/**
 * Holds the ticket signer built from configuration (`app.relay.ticket-secret`, environment variable
 * RELAY_TICKET_SECRET). The secret is never logged or returned. Without a usable secret [signer] is null and the
 * relay session endpoints answer 503; the rest of the site is unaffected.
 */
@Component
class RelayTickets(
    @Value("\${app.relay.ticket-secret:}") secret: String,
) {
    /** Why there is no signer, for the 503 message; null when the secret is usable. */
    val problem: String? =
        when {
            secret.isBlank() -> "Relay sessions are not configured on this server (RELAY_TICKET_SECRET is not set)"
            secret.trim().toByteArray(Charsets.UTF_8).size < MIN_SECRET_BYTES ->
                "Relay sessions are not configured on this server (RELAY_TICKET_SECRET is shorter than $MIN_SECRET_BYTES bytes)"
            else -> null
        }

    val signer: RelayTicketSigner? = if (problem == null) RelayTicketSigner(secret.trim()) else null

    init {
        if (problem != null) LoggerFactory.getLogger(RelayTickets::class.java).warn(problem)
    }

    private companion object {
        const val MIN_SECRET_BYTES = 32
    }
}
