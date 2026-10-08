package no.battlefront.balancer.security

import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Time-based one-time passwords (RFC 6238) as used by authenticator apps: HMAC-SHA1, 6 digits, 30-second steps.
 */
object Totp {
    const val DIGITS = 6
    const val PERIOD_SECONDS = 30L

    /** Accept codes from one step before or after the current one, to allow for clock drift. */
    private const val ALLOWED_DRIFT_STEPS = 1
    private const val SECRET_BYTES = 20
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private val DIGITS_RE = Regex("^\\d{$DIGITS}$")
    private val secureRandom = SecureRandom()

    /** A new random secret, base32-encoded (what the authenticator app stores). */
    fun generateSecret(): String {
        val bytes = ByteArray(SECRET_BYTES)
        secureRandom.nextBytes(bytes)
        return base32Encode(bytes)
    }

    /** The code for [step] (seconds since epoch divided by [PERIOD_SECONDS]). */
    fun codeAt(
        secret: ByteArray,
        step: Long,
    ): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secret, "HmacSHA1"))
        val hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array())
        val offset = hash.last().toInt() and 0x0f
        val binary =
            ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
        return (binary % 1_000_000).toString().padStart(DIGITS, '0')
    }

    /**
     * Returns the time step [code] belongs to, or null if it is not valid at [epochSeconds]. Steps at or before
     * [lastUsedStep] are rejected, so a code cannot be used twice.
     */
    fun matchingStep(
        base32Secret: String,
        code: String,
        epochSeconds: Long,
        lastUsedStep: Long?,
    ): Long? {
        if (!DIGITS_RE.matches(code)) return null
        val secret = base32Decode(base32Secret)
        val current = epochSeconds / PERIOD_SECONDS
        return (current - ALLOWED_DRIFT_STEPS..current + ALLOWED_DRIFT_STEPS).firstOrNull { step ->
            (lastUsedStep == null || step > lastUsedStep) &&
                MessageDigest.isEqual(codeAt(secret, step).toByteArray(), code.toByteArray())
        }
    }

    /** The `otpauth://` URI that authenticator apps read from a QR code. */
    fun otpauthUri(
        issuer: String,
        account: String,
        base32Secret: String,
    ): String {
        fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")
        return "otpauth://totp/${enc(issuer)}:${enc(account)}?secret=$base32Secret&issuer=${enc(issuer)}" +
            "&algorithm=SHA1&digits=$DIGITS&period=$PERIOD_SECONDS"
    }

    fun base32Encode(bytes: ByteArray): String {
        val out = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                out.append(BASE32_ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) out.append(BASE32_ALPHABET[(buffer shl (5 - bits)) and 31])
        return out.toString()
    }

    fun base32Decode(value: String): ByteArray {
        val clean = value.uppercase().filter { it != '=' && !it.isWhitespace() }
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in clean) {
            val index = BASE32_ALPHABET.indexOf(c)
            require(index >= 0) { "Invalid base32 character" }
            buffer = (buffer shl 5) or index
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xff)
                bits -= 8
            }
        }
        return out.toByteArray()
    }
}
