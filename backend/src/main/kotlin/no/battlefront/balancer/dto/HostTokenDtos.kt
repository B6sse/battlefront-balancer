package no.battlefront.balancer.dto

/**
 * Request body for POST /api/admin/host-tokens.
 *
 * @param name label for the token, e.g. the host's name
 * @param userId admin/supervisor who owns the token
 */
data class HostTokenCreateRequest(
    val name: String,
    val userId: Long,
)

/**
 * Response for POST /api/admin/host-tokens. [token] is shown only here and cannot be retrieved later.
 */
data class HostTokenCreatedDto(
    val id: Long,
    val name: String,
    val token: String,
    val userId: Long,
    val username: String,
)

/**
 * Host token as listed for admins (GET /api/admin/host-tokens). Never includes the token itself.
 *
 * @param username owner's username, or null if the owner no longer exists
 */
data class HostTokenDto(
    val id: Long,
    val name: String,
    val userId: Long,
    val username: String?,
    val createdAt: String,
    val lastUsedAt: String?,
    val revokedAt: String?,
)
