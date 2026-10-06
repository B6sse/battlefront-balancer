package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Player in the ranked system. Identified by nickname; has rating, nation and ELO/BR-related fields.
 *
 * @param nation 2-letter ISO country code (e.g. "no", "us")
 * @param personaId EA persona ID, used to identify the player in game; null if unknown
 * @param lastSeenName the in-game name last reported for this player; never replaces [nickname]
 */
@Entity
@Table(name = "players")
class Player(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, length = 100)
    var nickname: String = "",
    @Column(nullable = false, length = 2)
    var nation: String = "",
    @Column(nullable = false)
    var rating: Int = 0,
    @Column(name = "dz_rating", nullable = false)
    var dzrating: Int = 0,
    @Column(name = "persona_id", unique = true)
    var personaId: Long? = null,
    @Column(name = "last_seen_name", length = 100)
    var lastSeenName: String? = null,
)
