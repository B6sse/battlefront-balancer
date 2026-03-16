package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.LastMatchPlayerDto
import no.battlefront.balancer.dto.MatchSubmitRequest
import no.battlefront.balancer.service.MatchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class MatchController(private val matchService: MatchService) {

    /**
     * GET /api/last-match – spillere som deltok i siste match (med sesongstat).
     * Tilsvarer api_lastMatch.php.
     */
    @GetMapping("/last-match")
    fun getPlayersInLastMatch(): ResponseEntity<List<LastMatchPlayerDto>> =
        ResponseEntity.ok(matchService.getPlayersInLastMatch())

    /**
     * POST /api/matches – lagre match + stats og oppdater sesongstat (tilsvarer matchSubmit.php).
     */
    @PostMapping("/matches")
    fun submitMatch(@RequestBody request: MatchSubmitRequest): ResponseEntity<Map<String, Any>> {
        return try {
            matchService.submitMatch(request)
            ResponseEntity.ok(mapOf("success" to true, "message" to "Data saved successfully"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest()
                .body(mapOf("success" to false, "message" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            ResponseEntity.internalServerError()
                .body(mapOf("success" to false, "message" to (e.message ?: "Error saving match")))
        }
    }
}
