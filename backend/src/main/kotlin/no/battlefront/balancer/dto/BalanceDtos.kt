package no.battlefront.balancer.dto

/**
 * Request body for POST /api/balance.
 *
 * @param ratingKey which rating to balance on: "br" (season battle rating), "rating" (intern) or "dzrating" (Drop Zone)
 * @param personaIds EA persona IDs of the players in the lobby (Auric); even count, no duplicates
 * @param playerIds player primary keys instead of persona IDs (website); send exactly one of the two lists
 */
data class BalanceRequest(
    val ratingKey: String = "br",
    val personaIds: List<Long> = emptyList(),
    val playerIds: List<Long> = emptyList(),
)

/**
 * Response for POST /api/balance. Teams are listed by the same kind of ID as the request, highest rating first.
 */
data class BalanceResponse(
    val rebels: List<Long>,
    val imperials: List<Long>,
    val rebelAverage: Double,
    val imperialAverage: Double,
)

/**
 * Response for GET /api/players/by-persona.
 *
 * @param players registered players with current-season stats, in request order
 * @param unknown requested persona IDs that do not belong to any player
 */
data class PlayersByPersonaDto(
    val players: List<PlayerWithStatsDto>,
    val unknown: List<Long>,
)
