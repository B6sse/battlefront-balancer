package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.PlayerCreateRequest
import no.battlefront.balancer.dto.PlayerUpdateRequest
import no.battlefront.balancer.dto.PlayerWithStatsDto
import no.battlefront.balancer.service.PlayerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
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

    /**
     * POST /api/players – opprett spiller + initial sesongstat.
     * Tilsvarer action/add.php (uten auth/CSRF).
     */
    @PostMapping("/players")
    fun createPlayer(@RequestBody request: PlayerCreateRequest): ResponseEntity<Any> =
        try {
            val saved = playerService.createPlayer(request)
            ResponseEntity.ok(saved)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        }

    /**
     * PUT /api/players/{id} – oppdater spiller + BR for gjeldende sesong.
     * Tilsvarer action/update.php (uten auth/CSRF).
     */
    @PutMapping("/players/{id}")
    fun updatePlayer(
        @PathVariable id: Long,
        @RequestBody request: PlayerUpdateRequest
    ): ResponseEntity<Any> =
        try {
            val saved = playerService.updatePlayer(id, request)
            ResponseEntity.ok(saved)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        } catch (e: IllegalStateException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Season stats not found")))
        }

    /**
     * DELETE /api/players/{id} – slett spiller.
     * Tilsvarer action/delete.php (uten auth/CSRF).
     */
    @DeleteMapping("/players/{id}")
    fun deletePlayer(@PathVariable id: Long): ResponseEntity<Void> {
        playerService.deletePlayer(id)
        return ResponseEntity.noContent().build()
    }
}

