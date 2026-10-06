package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * API token that lets an Auric host call the host endpoints. The plain token is never stored.
 *
 * @param tokenHash hex-encoded SHA-256 of the token
 * @param userId admin/supervisor who owns the token; becomes the supervisor of matches uploaded with it
 * @param createdBy admin who created the token, or null if that user was deleted
 * @param revokedAt when the token was revoked; a revoked token no longer authenticates
 */
@Entity
@Table(name = "host_tokens")
class HostToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, length = 100)
    var name: String = "",
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    var tokenHash: String = "",
    @Column(name = "user_id", nullable = false)
    var userId: Long = 0,
    @Column(name = "created_by")
    var createdBy: Long? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "revoked_at")
    var revokedAt: LocalDateTime? = null,
    @Column(name = "last_used_at")
    var lastUsedAt: LocalDateTime? = null,
)
