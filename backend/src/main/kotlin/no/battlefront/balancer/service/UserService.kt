package no.battlefront.balancer.service

import no.battlefront.balancer.dto.CreateUserRequest
import no.battlefront.balancer.dto.UserDto
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.PasswordPolicy
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * User management for admins. Admins can create supervisors and editors, set their passwords and reset their
 * two-factor login, but never act on another admin. Admins change their own password with their current password
 * and a two-factor code.
 */
@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val twoFactorService: TwoFactorService,
) {
    fun listUsers(): List<UserDto> =
        userRepository
            .findAll()
            .sortedWith(compareBy({ it.role }, { it.username }))
            .map { it.toDto() }

    @Transactional
    fun createUser(request: CreateUserRequest): UserDto {
        val username = request.username.trim()
        PasswordPolicy.requireValidUsername(username)
        require(request.role in MANAGED_ROLES) { "Role must be 'editor' or 'supervisor'" }
        require(userRepository.findByUsername(username) == null) { "Username is already taken" }
        PasswordPolicy.requireValid(request.password)
        val saved =
            userRepository.save(
                User(username = username, password = passwordEncoder.encode(request.password)!!, role = request.role),
            )
        return saved.toDto()
    }

    @Transactional
    fun updateUserRole(
        targetId: Long,
        newRole: String,
        currentUserId: Long,
    ): UserDto {
        require(newRole in MANAGED_ROLES) { "Role must be 'editor' or 'supervisor'" }
        val target = managedUser(targetId, currentUserId, "change your own role")
        target.role = newRole
        return userRepository.save(target).toDto()
    }

    /**
     * Sets the password of a supervisor or editor.
     */
    @Transactional
    fun setPassword(
        targetId: Long,
        password: String,
        currentUserId: Long,
    ) {
        val target = managedUser(targetId, currentUserId, "set your own password here; use Change my password")
        PasswordPolicy.requireValid(password)
        target.password = passwordEncoder.encode(password)!!
        userRepository.save(target)
    }

    /**
     * Removes the two-factor login of a supervisor or editor (e.g. a lost phone); they set it up again at next login.
     */
    @Transactional
    fun resetTwoFactor(
        targetId: Long,
        currentUserId: Long,
    ) {
        val target = managedUser(targetId, currentUserId, "reset your own two-factor login")
        twoFactorService.reset(target.id)
    }

    /**
     * An admin changing their own password; requires the current password and a code from the authenticator app.
     */
    @Transactional
    fun changeOwnPassword(
        currentUserId: Long,
        currentPassword: String,
        newPassword: String,
        code: String,
    ) {
        val user = userRepository.findById(currentUserId).orElseThrow { IllegalArgumentException("User not found") }
        require(user.role == "admin") { "Only admins can change passwords" }
        require(passwordEncoder.matches(currentPassword, user.password)) { "Current password is incorrect" }
        require(twoFactorService.verifyAppCode(user.id, code)) { "Invalid two-factor code" }
        PasswordPolicy.requireValid(newPassword)
        require(newPassword != currentPassword) { "New password must be different from the current one" }
        user.password = passwordEncoder.encode(newPassword)!!
        userRepository.save(user)
    }

    @Transactional
    fun deleteUser(
        targetId: Long,
        currentUserId: Long,
    ) {
        userRepository.delete(managedUser(targetId, currentUserId, "delete your own account"))
    }

    /** A user an admin may manage: not themselves and not another admin. */
    private fun managedUser(
        targetId: Long,
        currentUserId: Long,
        selfAction: String,
    ): User {
        require(targetId != currentUserId) { "You cannot $selfAction" }
        val target = userRepository.findById(targetId).orElseThrow { IllegalArgumentException("User not found") }
        require(target.role != "admin") { "Admin accounts cannot be changed by other admins" }
        return target
    }

    private fun User.toDto() = UserDto(id, username, role, totpEnabled)

    private companion object {
        val MANAGED_ROLES = setOf("editor", "supervisor")
    }
}
