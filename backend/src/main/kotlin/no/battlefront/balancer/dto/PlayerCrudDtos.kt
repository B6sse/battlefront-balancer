package no.battlefront.balancer.dto

/**
 * Request body for creating a new player (POST /api/players).
 *
 * @param nickname display name
 * @param nation 2-letter country code
 * @param rating overall rating (1–99); used to derive initial BR/best
 * @param personaId optional EA persona ID; must be positive and not used by another player
 */
data class PlayerCreateRequest(
    val nickname: String,
    val nation: String,
    val rating: Int,
    val personaId: Long? = null,
)

/**
 * Request body for updating a player (PUT /api/players/{id}).
 *
 * @param nickname display name
 * @param nation 2-letter country code
 * @param rating overall rating
 * @param dzrating DZ rating
 * @param br new battle rating for the current season
 * @param personaId EA persona ID; null removes it
 */
data class PlayerUpdateRequest(
    val nickname: String,
    val nation: String,
    val rating: Int,
    val dzrating: Int,
    val br: Int,
    val personaId: Long? = null,
)
