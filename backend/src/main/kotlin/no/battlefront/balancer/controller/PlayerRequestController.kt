package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.PlayerRequestApproveRequest
import no.battlefront.balancer.dto.PlayerRequestCreateRequest
import no.battlefront.balancer.dto.PlayerRequestDto
import no.battlefront.balancer.dto.PlayerRequestLinkRequest
import no.battlefront.balancer.security.HostPrincipal
import no.battlefront.balancer.service.PersonaAlreadyRegisteredException
import no.battlefront.balancer.service.PlayerRequestService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/player-requests")
class PlayerRequestController(
    private val playerRequestService: PlayerRequestService,
) {
    /**
     * Auric (host token) asks for a player to be registered.
     *
     * @return 200 with the request id, 400 on invalid fields, or 409 if the persona ID is already registered.
     */
    @PostMapping
    fun submit(
        @RequestBody request: PlayerRequestCreateRequest,
        @AuthenticationPrincipal host: HostPrincipal,
    ): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(playerRequestService.submit(request, host.tokenId))
        } catch (e: PersonaAlreadyRegisteredException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("message" to e.message, "playerId" to e.playerId))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        }

    @GetMapping
    fun listPending(): ResponseEntity<List<PlayerRequestDto>> = ResponseEntity.ok(playerRequestService.listPending())

    /**
     * Creates the player from the request; the body may correct nickname, nation or rating.
     */
    @PostMapping("/{id}/approve")
    fun approve(
        @PathVariable id: Long,
        @RequestBody(required = false) overrides: PlayerRequestApproveRequest?,
    ): ResponseEntity<Any> = handle { mapOf("playerId" to playerRequestService.approve(id, overrides ?: PlayerRequestApproveRequest()).id) }

    /**
     * Gives an existing player the request's persona ID.
     */
    @PostMapping("/{id}/link")
    fun link(
        @PathVariable id: Long,
        @RequestBody body: PlayerRequestLinkRequest,
    ): ResponseEntity<Any> = handle { mapOf("playerId" to playerRequestService.link(id, body.playerId).id) }

    @PostMapping("/{id}/reject")
    fun reject(
        @PathVariable id: Long,
    ): ResponseEntity<Any> = handle { playerRequestService.reject(id) }

    private fun handle(action: () -> Any): ResponseEntity<Any> =
        try {
            val result = action()
            if (result is Unit) ResponseEntity.noContent().build() else ResponseEntity.ok(result)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        }
}
