package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.RawMatchRequest
import no.battlefront.balancer.service.RawMatchService
import no.battlefront.balancer.service.UnknownPlayersException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class RawMatchController(
    private val rawMatchService: RawMatchService,
) {
    /**
     * Stores a match from raw results; the server computes BR, perf and MVP. Used by Auric (host token) after every
     * map, and by supervisors, editors and admins.
     *
     * @return 200 with the stored per-player changes, 400 on invalid input, or 422 with `unknown` if any persona ID
     *   is not registered (nothing is stored).
     */
    @PostMapping("/matches/raw")
    fun submit(
        @RequestBody request: RawMatchRequest,
    ): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(rawMatchService.submit(request))
        } catch (e: UnknownPlayersException) {
            ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(mapOf("message" to "Some players are not registered", "unknown" to e.unknown))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        }
}
