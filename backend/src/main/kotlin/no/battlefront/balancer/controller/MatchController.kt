package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.LastMatchPlayerDto
import no.battlefront.balancer.dto.MatchDetailDto
import no.battlefront.balancer.dto.MatchSubmitRequest
import no.battlefront.balancer.dto.MatchSummaryDto
import no.battlefront.balancer.service.MatchService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class MatchController(
    private val matchService: MatchService,
) {
    /**
     * Returns all distinct seasons that have at least one match.
     */
    @GetMapping("/seasons")
    fun getSeasons(): ResponseEntity<List<Int>> = ResponseEntity.ok(matchService.getSeasons())

    /**
     * Returns a summary list of matches for the given season.
     * @param season "all" for all seasons, a season number, or omit for current season.
     */
    @GetMapping("/matches")
    fun getMatchList(
        @RequestParam(required = false) season: String?,
    ): ResponseEntity<List<MatchSummaryDto>> = ResponseEntity.ok(matchService.getMatchList(season))

    /**
     * Returns the full detail (rebel/imperial stats) for a specific match.
     * @param id the match id
     * @param season used to find the latest match if id is not given (not used when id is present)
     */
    @GetMapping("/matches/{id}")
    fun getMatchDetail(
        @PathVariable id: Long,
        @RequestParam(required = false) season: String?,
    ): ResponseEntity<MatchDetailDto> {
        val detail = matchService.getMatchDetail(id, season)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(detail)
    }

    /**
     * Returns the players who participated in the most recent match, with their current season stats.
     *
     * @return [ResponseEntity] containing a list of [LastMatchPlayerDto]; empty if no match exists.
     */
    @GetMapping("/last-match")
    fun getPlayersInLastMatch(): ResponseEntity<List<LastMatchPlayerDto>> = ResponseEntity.ok(matchService.getPlayersInLastMatch())

    /**
     * Persists a match, per-player stats, and updates season stats for all participants.
     *
     * @param request the match payload (matchData, rebels, imperials; see [MatchSubmitRequest]).
     * @return [ResponseEntity] with success/error message and status 200, 400, or 500.
     */
    @PostMapping("/matches")
    fun submitMatch(
        @RequestBody request: MatchSubmitRequest,
    ): ResponseEntity<Map<String, Any>> =
        try {
            matchService.submitMatch(request)
            ResponseEntity.ok(mapOf("success" to true, "message" to "Data saved successfully"))
        } catch (e: IllegalArgumentException) {
            ResponseEntity
                .badRequest()
                .body(mapOf("success" to false, "message" to (e.message ?: "Invalid request")))
        } catch (e: Exception) {
            ResponseEntity
                .internalServerError()
                .body(mapOf("success" to false, "message" to (e.message ?: "Error saving match")))
        }
}
