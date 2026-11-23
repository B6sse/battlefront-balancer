package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * One running game server in the server list. Live as long as its host keeps sending heartbeats.
 *
 * @param sid random hex id, public (players join with it)
 * @param hostTokenId the host token that created the session and may update or end it; never public
 * @param passwordHash bcrypt hash of the join password, or null if anyone may join
 * @param lastHeartbeatMs Unix time in milliseconds of the last heartbeat (or of creation)
 */
@Entity
@Table(name = "relay_sessions")
class RelaySession(
    @Id
    @Column(length = 32)
    val sid: String = "",
    @Column(name = "host_token_id", nullable = false, unique = true)
    var hostTokenId: Long = 0,
    @Column(name = "relay_id", nullable = false, length = 20)
    var relayId: String = "",
    @Column(nullable = false, length = 40)
    var name: String = "",
    @Column(nullable = false, length = 40)
    var mode: String = "",
    @Column(nullable = false, length = 200)
    var level: String = "",
    @Column(nullable = false)
    var players: Int = 0,
    @Column(name = "max_players", nullable = false)
    var maxPlayers: Int = 0,
    @Column(name = "password_hash", length = 100)
    var passwordHash: String? = null,
    @Column(name = "auric_version", length = 20)
    var auricVersion: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "last_heartbeat_ms", nullable = false)
    var lastHeartbeatMs: Long = 0,
)
