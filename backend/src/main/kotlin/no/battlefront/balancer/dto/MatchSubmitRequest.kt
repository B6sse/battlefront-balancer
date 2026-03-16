package no.battlefront.balancer.dto

/**
 * Request body for POST /api/matches (tilsvarer matchSubmit.php).
 * matchData = [map, team_size, mvp_id, rebel_score, imperial_score, rule] (indices 0..5).
 * supervisorId brukes som FK til users; sett 0 inntil auth er på plass.
 */
data class MatchSubmitRequest(
    val matchData: List<Any>,
    val rebels: List<PlayerMatchStatDto>,
    val imperials: List<PlayerMatchStatDto>,
    val supervisorId: Long = 0L
)
