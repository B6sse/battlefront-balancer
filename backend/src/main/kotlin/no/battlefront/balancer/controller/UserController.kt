package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.CreateUserRequest
import no.battlefront.balancer.dto.SetPasswordRequest
import no.battlefront.balancer.dto.UpdateUserRoleRequest
import no.battlefront.balancer.security.AppUserDetails
import no.battlefront.balancer.service.UserService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/users")
class UserController(
    private val userService: UserService,
) {
    @GetMapping
    fun listUsers() = ResponseEntity.ok(userService.listUsers())

    /**
     * Creates a supervisor or editor.
     */
    @PostMapping
    fun createUser(
        @RequestBody body: CreateUserRequest,
    ): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(userService.createUser(body))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Failed to create user")))
        }

    /**
     * Sets the password of a supervisor or editor.
     */
    @PutMapping("/{id}/password")
    fun setPassword(
        @PathVariable id: Long,
        @RequestBody body: SetPasswordRequest,
        @AuthenticationPrincipal principal: AppUserDetails,
    ): ResponseEntity<Any> =
        try {
            userService.setPassword(id, body.password, principal.userId)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Failed to set password")))
        }

    /**
     * Removes the two-factor login of a supervisor or editor so they set it up again at next login.
     */
    @PostMapping("/{id}/reset-2fa")
    fun resetTwoFactor(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: AppUserDetails,
    ): ResponseEntity<Any> =
        try {
            userService.resetTwoFactor(id, principal.userId)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Failed to reset two-factor login")))
        }

    @PutMapping("/{id}/role")
    fun updateUserRole(
        @PathVariable id: Long,
        @RequestBody body: UpdateUserRoleRequest,
        @AuthenticationPrincipal principal: AppUserDetails,
    ): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(userService.updateUserRole(id, body.role, principal.userId))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Failed to update role")))
        }

    @DeleteMapping("/{id}")
    fun deleteUser(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: AppUserDetails,
    ): ResponseEntity<Any> =
        try {
            userService.deleteUser(id, principal.userId)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Failed to delete user")))
        } catch (e: DataIntegrityViolationException) {
            ResponseEntity.badRequest().body(
                mapOf("message" to "Cannot delete user: they are referenced by existing matches or other data"),
            )
        }
}
