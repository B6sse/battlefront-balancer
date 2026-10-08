package no.battlefront.balancer.dto

/**
 * Response for POST /api/login and the two-factor steps.
 *
 * @param status "OK" (logged in), "TOTP_REQUIRED" (enter the code from the authenticator app) or
 *   "TOTP_SETUP_REQUIRED" (set up the authenticator app first)
 * @param user the logged-in user when [status] is "OK"
 * @param recoveryCodes one-time recovery codes, only right after two-factor setup; shown once
 */
data class LoginResponse(
    val status: String,
    val user: CurrentUserDto? = null,
    val recoveryCodes: List<String>? = null,
) {
    companion object {
        const val OK = "OK"
        const val TOTP_REQUIRED = "TOTP_REQUIRED"
        const val TOTP_SETUP_REQUIRED = "TOTP_SETUP_REQUIRED"
    }
}

/** A code from the authenticator app, or a recovery code. */
data class TwoFactorCodeRequest(
    val code: String,
)

/**
 * Secret for setting up the authenticator app.
 *
 * @param otpauthUri the value to show as a QR code
 * @param secret the same secret for typing in by hand
 */
data class TwoFactorSetupDto(
    val secret: String,
    val otpauthUri: String,
)
