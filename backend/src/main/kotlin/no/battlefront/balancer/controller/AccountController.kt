package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.ChangeOwnPasswordRequest
import no.battlefront.balancer.security.AppUserDetails
import no.battlefront.balancer.service.UserService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The logged-in admin's own account.
 */
@RestController
@RequestMapping("/api/admin/account")
class AccountController(
    private val userService: UserService,
) {
    /**
     * Changes the admin's own password; requires the current password and a code from the authenticator app.
     */
    @PutMapping("/password")
    fun changePassword(
        @RequestBody body: ChangeOwnPasswordRequest,
        @AuthenticationPrincipal principal: AppUserDetails,
    ): ResponseEntity<Any> =
        try {
            userService.changeOwnPassword(principal.userId, body.currentPassword, body.newPassword, body.code)
            ResponseEntity.noContent().build()
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Failed to change password")))
        }
}
