package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * User account for authentication. Used by Spring Security and session-based login.
 *
 * @param role one of "admin", "supervisor" (stored without ROLE_ prefix; mapped to authority in [no.battlefront.balancer.security.AppUserDetails])
 * @param password bcrypt-hashed; never stored in plain text
 * @param totpSecret base32 secret for the authenticator app, or null if two-factor is not set up
 * @param totpEnabled true once the user has confirmed the authenticator app; required for admins and editors
 * @param totpLastStep last accepted 30-second time step, so the same code cannot be used twice
 */
@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, unique = true, length = 100)
    var username: String = "",
    @Column(nullable = false, length = 255)
    var password: String = "",
    @Column(nullable = false, length = 50)
    var role: String = "",
    @Column(name = "totp_secret", length = 64)
    var totpSecret: String? = null,
    @Column(name = "totp_enabled", nullable = false)
    var totpEnabled: Boolean = false,
    @Column(name = "totp_last_step")
    var totpLastStep: Long? = null,
)
