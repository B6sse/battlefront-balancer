package no.battlefront.balancer.dto

/**
 * Current authenticated user (GET /api/me and after login).
 */
data class CurrentUserDto(
    val id: Long,
    val username: String,
    val role: String,
)
