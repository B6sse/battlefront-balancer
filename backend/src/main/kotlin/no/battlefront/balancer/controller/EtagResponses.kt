package no.battlefront.balancer.controller

import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Builds 200 responses with an ETag computed from the JSON body and `Cache-Control: no-cache`, so clients revalidate
 * every time. When the request's If-None-Match matches, Spring MVC turns the response into 304 Not Modified
 * without a body.
 */
@Component
class EtagResponses(
    private val jsonMapper: JsonMapper,
) {
    fun <T : Any> ok(body: T): ResponseEntity<T> {
        val digest = MessageDigest.getInstance("SHA-256").digest(jsonMapper.writeValueAsBytes(body))
        val etag = "\"" + HexFormat.of().formatHex(digest, 0, ETAG_BYTES) + "\""
        return ResponseEntity
            .ok()
            .eTag(etag)
            .cacheControl(CacheControl.noCache())
            .body(body)
    }

    private companion object {
        const val ETAG_BYTES = 16
    }
}
