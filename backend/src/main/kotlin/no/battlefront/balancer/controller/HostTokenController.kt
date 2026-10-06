package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.HostTokenCreateRequest
import no.battlefront.balancer.dto.HostTokenDto
import no.battlefront.balancer.service.HostTokenService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Admin endpoints for Auric host tokens.
 */
@RestController
@RequestMapping("/api/admin/host-tokens")
class HostTokenController(
    private val hostTokenService: HostTokenService,
) {
    @GetMapping
    fun listTokens(): ResponseEntity<List<HostTokenDto>> = ResponseEntity.ok(hostTokenService.listTokens())

    /**
     * Creates a token. The response contains the plain token, which is not shown again.
     */
    @PostMapping
    fun createToken(
        @RequestBody request: HostTokenCreateRequest,
    ): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(hostTokenService.createToken(request))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        }

    /**
     * Revokes a token; it stays in the list as revoked.
     */
    @DeleteMapping("/{id}")
    fun revokeToken(
        @PathVariable id: Long,
    ): ResponseEntity<Any> =
        try {
            hostTokenService.revokeToken(id)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        }
}
