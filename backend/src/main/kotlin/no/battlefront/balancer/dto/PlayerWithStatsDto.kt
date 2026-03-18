package no.battlefront.balancer.dto

/**
 * Player with current-season statistics. Used as response for GET /api/players.
 *
 * @param id player primary key
 * @param nickname display name
 * @param nation 2-letter country code (e.g. "no", "us")
 * @param rating overall rating (1–99)
 * @param dzrating DZ rating
 * @param elo ELO value
 * @param br battle rating for the season
 * @param played games played
 * @param best best BR this season
 * @param won wins
 * @param lost losses
 * @param draw draws
 * @param score total score
 * @param mvp MVP count
 */
data class PlayerWithStatsDto(
    val id: Long,
    val nickname: String,
    val nation: String,
    val rating: Int,
    val dzrating: Int,
    val elo: Int,
    val br: Int,
    val played: Int,
    val best: Int,
    val won: Int,
    val lost: Int,
    val draw: Int,
    val score: Int,
    val mvp: Int,
)
