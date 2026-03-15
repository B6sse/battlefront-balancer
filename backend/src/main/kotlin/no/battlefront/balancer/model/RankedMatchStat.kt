package no.battlefront.balancer.model

import jakarta.persistence.*

@Entity
@Table(name = "ranked_mstats")

class RankedMatchStat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "player_id", nullable = false)
    var playerId: Long = 0, // FK to players table

    @Column(name = "match_id", nullable = false)
    var matchId: Long = 0, // FK to matches table

    @Column(nullable = false)
    var season: Int = 0,

    @Column(nullable = false, length = 20)
    var faction: String = "",  // "Rebel" or "Imperial"

    @Column(nullable = false, length = 10)
    var result: String = "",  // "Won", "Lost", "Draw"

    @Column(nullable = false)
    var score: Int = 0,

    @Column(nullable = false)
    var perf: Double = 0.0,  // performance/carry

    @Column(name = "update_br", nullable = false)
    var updateBr: Int = 0,   // BR-change (+/-)

    @Column(name = "new_br", nullable = false)
    var newBr: Int = 0       // BR after match
)