package no.battlefront.balancer.dto

data class UserDto(
    val id: Long,
    val username: String,
    val role: String,
)

data class UpdateUserRoleRequest(
    val role: String,
)
