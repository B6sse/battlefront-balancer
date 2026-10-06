package no.battlefront.balancer.dto

/**
 * Request body for POST /api/matches/raw: the raw result of one map; the server computes BR, perf and MVP.
 *
 * @param map the game's level path (e.g. "Levels/Desert/Desert_04/Desert_04") or a map name
 * @param rule community rule (DSE, DACE, ...); stored as "?" when missing
 * @param rebelScore pods captured by the Rebels (0–5)
 * @param imperialScore pods captured by the Imperials (0–5)
 * @param endedAt when the map ended (ISO-8601 instant, e.g. "2026-10-06T19:14:40Z"); stored in Oslo time, defaults to now
 */
data class RawMatchRequest(
    val map: String,
    val rule: String? = null,
    val rebelScore: Int,
    val imperialScore: Int,
    val endedAt: String? = null,
    val players: List<RawMatchPlayer>,
)

/**
 * One player's raw result. Identify the player with exactly one of [personaId] (Auric) or [playerId] (website).
 *
 * @param name the player's in-game name; stored as the player's last seen name
 * @param faction "Rebel" or "Imperial"
 * @param partnerless true if the player had no partner (at most one per team)
 */
data class RawMatchPlayer(
    val personaId: Long? = null,
    val playerId: Long? = null,
    val name: String? = null,
    val faction: String,
    val score: Int,
    val kills: Int,
    val deaths: Int,
    val partnerless: Boolean = false,
)

/**
 * Response for POST /api/matches/raw: what was stored, so Auric can log it.
 *
 * @param matchId id of the stored match; null for a dry run
 * @param nextMap next suggested map from the randomizer; null for a dry run
 * @param nextRule next suggested rule from the randomizer; null for a dry run
 */
data class RawMatchResultDto(
    val matchId: Long?,
    val map: String,
    val rule: String,
    val mvpPlayerId: Long?,
    val rebels: List<RawMatchPlayerResultDto>,
    val imperials: List<RawMatchPlayerResultDto>,
    val nextMap: String?,
    val nextRule: String?,
)

data class RawMatchPlayerResultDto(
    val playerId: Long,
    val personaId: Long?,
    val nickname: String,
    val outcome: String,
    val score: Int,
    val perf: Double,
    val change: Int,
    val newBr: Int,
)
