package no.battlefront.balancer.dto

/**
 * Request body for creating a new player (POST /api/players).
 *
 * @param nickname display name
 * @param nation 2-letter country code
 * @param rating overall rating (1–99); used to derive initial BR/best
 */
data class PlayerCreateRequest(
    val nickname: String,
    val nation: String,
    val rating: Int,
)

/**
 * Request body for updating a player (PUT /api/players/{id}).
 *
 * @param nickname display name
 * @param nation 2-letter country code
 * @param rating overall rating
 * @param dzrating DZ rating
 * @param br new battle rating for the current season
 */
data class PlayerUpdateRequest(
    val nickname: String,
    val nation: String,
    val rating: Int,
    val dzrating: Int,
    val br: Int,
)
