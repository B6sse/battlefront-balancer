package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.PlayerWithStatsDto
import no.battlefront.balancer.service.PlayerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class PlayerController(private val playerService: PlayerService) {

    /**
     * GET /api/players – spillere med sesongstatistikk for nåværende sesong.
     * Tilsvarer gammel api_players.php.
     */
    @GetMapping("/players")
    fun getPlayers(): ResponseEntity<List<PlayerWithStatsDto>> =
        ResponseEntity.ok(playerService.getPlayersWithCurrentSeasonStats())
}
