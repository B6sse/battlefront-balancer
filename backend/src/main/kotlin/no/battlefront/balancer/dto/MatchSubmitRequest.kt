package no.battlefront.balancer.dto

/**
 * Request body for POST /api/matches.
 *
 * @param matchData list of 6 elements: [map, team_size, mvp_id, rebel_score, imperial_score, rule] (indices 0..5)
 * @param rebels list of per-player stats for the Rebel team
 * @param imperials list of per-player stats for the Imperial team
 * @param supervisorId FK to users; use 0 until auth is in place
 */
data class MatchSubmitRequest(
    val matchData: List<Any>,
    val rebels: List<PlayerMatchStatDto>,
    val imperials: List<PlayerMatchStatDto>,
    val supervisorId: Long = 0L
)
