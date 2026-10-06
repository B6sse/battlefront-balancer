package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Request from an Auric host to register a player.
 *
 * @param inGameName the player's name in game when requested; becomes [Player.lastSeenName] on approval
 * @param status "pending", "approved" or "rejected"
 * @param tokenId host token that sent the request, or null if the token was deleted
 * @param resolvedBy admin/editor who approved or rejected the request
 * @param playerId player created or linked on approval
 */
@Entity
@Table(name = "player_requests")
class PlayerRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "persona_id", nullable = false)
    var personaId: Long = 0,
    @Column(nullable = false, length = 100)
    var nickname: String = "",
    @Column(nullable = false, length = 2)
    var nation: String = "",
    @Column(nullable = false)
    var rating: Int = 0,
    @Column(name = "in_game_name", length = 100)
    var inGameName: String? = null,
    @Column(nullable = false, length = 20)
    var status: String = "",
    @Column(name = "token_id")
    var tokenId: Long? = null,
    @Column(name = "requested_at", nullable = false)
    var requestedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "resolved_at")
    var resolvedAt: LocalDateTime? = null,
    @Column(name = "resolved_by")
    var resolvedBy: Long? = null,
    @Column(name = "player_id")
    var playerId: Long? = null,
)
