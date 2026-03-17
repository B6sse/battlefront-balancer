package no.battlefront.balancer.dto

/**
 * Request body for POST /api/matches.
 * Supervisor is set server-side from the authenticated user; do not send from client.
 *
 * @param matchData list of 6 elements: [map, team_size, mvp_id, rebel_score, imperial_score, rule] (indices 0..5)
 * @param rebels list of per-player stats for the Rebel team
 * @param imperials list of per-player stats for the Imperial team
 */
data class MatchSubmitRequest(
    val matchData: List<Any>,
    val rebels: List<PlayerMatchStatDto>,
    val imperials: List<PlayerMatchStatDto>
)
