package no.battlefront.balancer.dto

/**
 * Request body for POST /api/player-requests (sent by Auric with a host token).
 *
 * @param personaId EA persona ID of the player
 * @param nickname name to register; the host chooses it
 * @param nation 2-letter country code
 * @param rating overall rating (1–99); used to derive the initial BR
 * @param inGameName the player's current in-game name, if known
 */
data class PlayerRequestCreateRequest(
    val personaId: Long,
    val nickname: String,
    val nation: String,
    val rating: Int,
    val inGameName: String? = null,
)

/**
 * Response for POST /api/player-requests.
 *
 * @param replaced true if a pending request for the same persona ID was replaced
 */
data class PlayerRequestCreatedDto(
    val id: Long,
    val status: String,
    val replaced: Boolean,
)

/**
 * Player request as listed for admins and editors.
 *
 * @param requestedBy name and owner of the host token that sent it, e.g. "Purpoz PC (purpoz)"
 */
data class PlayerRequestDto(
    val id: Long,
    val personaId: Long,
    val nickname: String,
    val nation: String,
    val rating: Int,
    val inGameName: String?,
    val status: String,
    val requestedBy: String?,
    val requestedAt: String,
)

/**
 * Optional corrections when approving a request; null fields keep the requested value.
 */
data class PlayerRequestApproveRequest(
    val nickname: String? = null,
    val nation: String? = null,
    val rating: Int? = null,
)

/**
 * Body for linking a request to an existing player.
 */
data class PlayerRequestLinkRequest(
    val playerId: Long,
)
