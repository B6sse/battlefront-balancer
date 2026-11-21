package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.AuricVersionAdminDto
import no.battlefront.balancer.dto.AuricVersionRequest
import no.battlefront.balancer.service.AuricVersionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Latest and minimum Auric version: public read, admin edit.
 */
@RestController
@RequestMapping("/api")
class AuricVersionController(
    private val auricVersionService: AuricVersionService,
    private val etagResponses: EtagResponses,
) {
    /**
     * Public.
     *
     * @return 200 with the version info and an ETag (304 if If-None-Match matches), or 404 if an admin has not set it yet
     */
    @GetMapping("/auric/version")
    fun version(): ResponseEntity<Any> {
        val version =
            auricVersionService.get()
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("message" to "Auric version info has not been set"))
        return etagResponses.ok<Any>(version)
    }

    @GetMapping("/admin/auric-version")
    fun get(): ResponseEntity<AuricVersionAdminDto> = ResponseEntity.ok(auricVersionService.getForAdmin())

    /**
     * @return 200 with the saved settings, or 400 with a message if a field is invalid
     */
    @PutMapping("/admin/auric-version")
    fun update(
        @RequestBody request: AuricVersionRequest,
    ): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(auricVersionService.update(request))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        }
}
