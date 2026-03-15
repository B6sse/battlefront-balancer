package no.battlefront.balancer.dto

/**
 * Spiller med sesongstat – brukes for spillere som deltok i siste match.
 * Tilsvarer responsen fra gammel api_lastMatch.php (uten dzrating/elo).
 */
data class LastMatchPlayerDto(
    val id: Long,
    val nickname: String,
    val nation: String,
    val rating: Int,
    val br: Int,
    val best: Int,
    val played: Int,
    val won: Int,
    val lost: Int,
    val draw: Int,
    val score: Int,
    val mvp: Int
)
