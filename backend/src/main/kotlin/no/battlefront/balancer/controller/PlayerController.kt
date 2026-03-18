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
class PlayerController(
    private val playerService: PlayerService,
) {
    /**
     * Returns all players with their season statistics for the current season.
     *
     * @return [ResponseEntity] containing a list of [PlayerWithStatsDto]; never null.
     */
    @GetMapping("/players")
    fun getPlayers(): ResponseEntity<List<PlayerWithStatsDto>> = ResponseEntity.ok(playerService.getPlayersWithCurrentSeasonStats())

    /**
     * Creates a new player and initial season stats for the current season.
     *
     * @param request the player data (nickname, nation, rating).
     * @return [ResponseEntity] with the saved [Player][no.battlefront.balancer.model.Player] on success, or 400 with error message on validation failure.
     */
    @PostMapping("/players")
    fun createPlayer(
        @RequestBody request: PlayerCreateRequest,
    ): ResponseEntity<Any> =
        try {
            val saved = playerService.createPlayer(request)
            ResponseEntity.ok(saved)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        }

    /**
     * Updates an existing player and their BR for the current season.
     *
     * @param id the player's primary key.
     * @param request the updated player data and new BR.
     * @return [ResponseEntity] with the saved [Player][no.battlefront.balancer.model.Player] on success, or 400 with error message on failure.
     */
    @PutMapping("/players/{id}")
    fun updatePlayer(
        @PathVariable id: Long,
        @RequestBody request: PlayerUpdateRequest,
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
     * Deletes a player by id. Related season stats are removed via cascade.
     *
     * @param id the player's primary key.
     * @return [ResponseEntity] with 204 No Content on success.
     */
    @DeleteMapping("/players/{id}")
    fun deletePlayer(
        @PathVariable id: Long,
    ): ResponseEntity<Void> {
        playerService.deletePlayer(id)
        return ResponseEntity.noContent().build()
    }
}
