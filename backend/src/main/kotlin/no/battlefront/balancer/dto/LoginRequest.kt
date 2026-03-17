package no.battlefront.balancer.dto

/**
 * Request body for POST /api/login.
 */
data class LoginRequest(
    val username: String,
    val password: String
)
