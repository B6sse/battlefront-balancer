package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * One-time recovery code for two-factor login when the authenticator app is unavailable.
 *
 * @param codeHash hex-encoded SHA-256 of the normalised code; the code itself is shown once and never stored
 * @param usedAt when the code was used; a used code is no longer accepted
 */
@Entity
@Table(name = "user_recovery_codes")
class RecoveryCode(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,
    @Column(name = "code_hash", nullable = false, length = 64)
    var codeHash: String = "",
    @Column(name = "used_at")
    var usedAt: LocalDateTime? = null,
)
