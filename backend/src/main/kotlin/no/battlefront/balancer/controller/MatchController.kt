package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.LastMatchPlayerDto
import no.battlefront.balancer.service.MatchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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
}
