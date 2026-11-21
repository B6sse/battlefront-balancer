package no.battlefront.balancer.service

import java.net.URI
import java.net.URISyntaxException

/** Validation shared by the approved mods list and the Auric version settings. */
internal object FieldValidation {
    private val SHA256_RE = Regex("^[0-9a-fA-F]{64}$")

    /**
     * @return [value] trimmed and in lower case
     * @throws IllegalArgumentException if it is missing or not 64 hex characters
     */
    fun requireSha256(
        value: String?,
        field: String,
    ): String {
        val v = value?.trim().orEmpty()
        require(v.isNotEmpty()) { "$field is required" }
        require(SHA256_RE.matches(v)) { "$field must be 64 hex characters (SHA-256)" }
        return v.lowercase()
    }

    /** Like [requireSha256], but null or blank means none. */
    fun optionalSha256(
        value: String?,
        field: String,
    ): String? = if (value.isNullOrBlank()) null else requireSha256(value, field)

    /**
     * @return [value] trimmed
     * @throws IllegalArgumentException if it is missing, blank or longer than [max]
     */
    fun requireText(
        value: String?,
        field: String,
        max: Int,
    ): String {
        val v = value?.trim().orEmpty()
        require(v.isNotEmpty()) { "$field is required" }
        require(v.length <= max) { "$field max $max characters" }
        return v
    }

    /** Null or blank means none; otherwise trimmed and at most [max] characters. */
    fun optionalText(
        value: String?,
        field: String,
        max: Int,
    ): String? {
        val v = value?.trim()
        if (v.isNullOrEmpty()) return null
        require(v.length <= max) { "$field max $max characters" }
        return v
    }

    /**
     * Null or blank means no URL.
     *
     * @return the trimmed URL
     * @throws IllegalArgumentException if it is not an absolute https URL with a host, or longer than 2000 characters
     */
    fun optionalHttpsUrl(
        value: String?,
        field: String,
    ): String? {
        val v = value?.trim()
        if (v.isNullOrEmpty()) return null
        require(v.length <= MAX_URL_LENGTH) { "$field max $MAX_URL_LENGTH characters" }
        val uri =
            try {
                URI(v)
            } catch (_: URISyntaxException) {
                null
            }
        require(uri != null && uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrEmpty()) {
            "$field must be an https:// URL"
        }
        return v
    }

    private const val MAX_URL_LENGTH = 2000
}
