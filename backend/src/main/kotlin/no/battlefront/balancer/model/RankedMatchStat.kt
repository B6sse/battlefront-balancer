package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Per-player statistics for one [RankedMatch]. One row per player per match (FKs to player and match).
 *
 * @param playerId FK to [Player]
 * @param matchId FK to [RankedMatch]
 * @param faction "Rebel" or "Imperial"
 * @param result "Won", "Lost" or "Draw"
 * @param updateBr BR change for this match (positive or negative)
 */
@Entity
@Table(name = "ranked_mstats")
class RankedMatchStat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "player_id", nullable = false)
    var playerId: Long = 0,
    @Column(name = "match_id", nullable = false)
    var matchId: Long = 0,
    @Column(nullable = false)
    var season: Int = 0,
    @Column(nullable = false, length = 20)
    var faction: String = "",
    @Column(nullable = false, length = 10)
    var result: String = "",
    @Column(nullable = false)
    var score: Int = 0,
    @Column(nullable = false)
    var perf: Double = 0.0,
    @Column(name = "update_br", nullable = false)
    var updateBr: Int = 0,
    @Column(name = "new_br", nullable = false)
    var newBr: Int = 0,
)
