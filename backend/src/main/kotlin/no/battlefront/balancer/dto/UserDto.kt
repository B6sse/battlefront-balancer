package no.battlefront.balancer.dto

/**
 * User as listed for admins.
 *
 * @param twoFactorEnabled whether the user has set up the authenticator app
 */
data class UserDto(
    val id: Long,
    val username: String,
    val role: String,
    val twoFactorEnabled: Boolean = false,
)

/**
 * Request body for POST /api/admin/users. [role] must be "supervisor" or "editor".
 */
data class CreateUserRequest(
    val username: String,
    val password: String,
    val role: String,
)

/**
 * Request body for PUT /api/admin/users/{id}/password.
 */
data class SetPasswordRequest(
    val password: String,
)

/**
 * Request body for PUT /api/admin/account/password (an admin changing their own password).
 *
 * @param code current code from the admin's authenticator app
 */
data class ChangeOwnPasswordRequest(
    val currentPassword: String,
    val newPassword: String,
    val code: String,
)

data class UpdateUserRoleRequest(
    val role: String,
)
