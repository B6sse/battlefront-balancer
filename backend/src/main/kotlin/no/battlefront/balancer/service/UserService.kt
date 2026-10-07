package no.battlefront.balancer.service

import no.battlefront.balancer.dto.UserDto
import no.battlefront.balancer.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserService(
    private val userRepository: UserRepository,
) {
    fun listUsers(): List<UserDto> =
        userRepository
            .findAll()
            .sortedWith(compareBy({ it.role }, { it.username }))
            .map { UserDto(it.id, it.username, it.role) }

    @Transactional
    fun updateUserRole(
        targetId: Long,
        newRole: String,
        currentUserId: Long,
    ): UserDto {
        require(newRole == "editor" || newRole == "supervisor") { "Role must be 'editor' or 'supervisor'" }
        require(targetId != currentUserId) { "Cannot change your own role" }
        val target = userRepository.findById(targetId).orElseThrow { IllegalArgumentException("User not found") }
        require(target.role != "admin") { "Cannot change role of an admin" }
        target.role = newRole
        val saved = userRepository.save(target)
        return UserDto(saved.id, saved.username, saved.role)
    }

    @Transactional
    fun deleteUser(
        targetId: Long,
        currentUserId: Long,
    ) {
        require(targetId != currentUserId) { "Cannot delete your own account" }
        val target = userRepository.findById(targetId).orElseThrow { IllegalArgumentException("User not found") }
        require(target.role != "admin") { "Cannot delete an admin" }
        userRepository.delete(target)
    }
}
