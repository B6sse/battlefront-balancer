package no.battlefront.balancer.dto

/**
 * Player with season stats; used for players who participated in the last match (GET /api/last-match).
 * Does not include dzrating/elo.
 *
 * @param id player primary key
 * @param nickname display name
 * @param nation 2-letter country code
 * @param rating overall rating
 * @param br battle rating
 * @param best best BR this season
 * @param played games played
 * @param won wins
 * @param lost losses
 * @param draw draws
 * @param score total score
 * @param mvp MVP count
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
    val mvp: Int,
)
