package no.battlefront.balancer.dto

/**
 * One player's input to the BR calculation.
 *
 * @param playerId player primary key
 * @param br current season battle rating (must be positive)
 * @param score in-game score
 * @param kills kills in the match
 * @param deaths deaths in the match
 * @param partnerless true if the player had no partner; gives 10% extra score (at most one per team)
 */
data class RatingPlayer(
    val playerId: Long,
    val br: Int,
    val score: Int,
    val kills: Int,
    val deaths: Int,
    val partnerless: Boolean = false,
)

/**
 * One player's result from the BR calculation.
 *
 * @param faction "Rebel" or "Imperial"
 * @param outcome "Won", "Lost" or "Draw"
 * @param perf actual carry relative to expected carry, rounded to 2 decimals
 * @param change BR change for this match
 * @param newBr BR after the match
 */
data class RatedPlayer(
    val playerId: Long,
    val faction: String,
    val outcome: String,
    val score: Int,
    val kills: Int,
    val deaths: Int,
    val perf: Double,
    val change: Int,
    val newBr: Int,
)

/**
 * Result of rating a match.
 *
 * @param mvpId MVP player id, or null if nobody scored
 */
data class MatchRating(
    val rebels: List<RatedPlayer>,
    val imperials: List<RatedPlayer>,
    val mvpId: Long?,
)
