package no.battlefront.balancer.dto

/**
 * Opprette ny spiller + initial sesongstat.
 */
data class PlayerCreateRequest(
    val nickname: String,
    val nation: String,
    val rating: Int
)

/**
 * Oppdatere eksisterende spiller + BR for nåværende sesong.
 */
data class PlayerUpdateRequest(
    val nickname: String,
    val nation: String,
    val rating: Int,
    val dzrating: Int,
    val br: Int
)

