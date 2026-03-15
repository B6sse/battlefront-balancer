package no.battlefront.balancer.model

import jakarta.persistence.*

@Entity
@Table(name = "ranked_pstats")

class RankedPlayerStat(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "player_id", nullable = false)
    var playerId: Long = 0,

    @Column(nullable = false)
    var season: Int = 0,

    @Column(nullable = false)
    var br: Int = 0,

    @Column(nullable = false)
    var best: Int = 0,

    @Column(nullable = false)
    var played: Int = 0,

    @Column(nullable = false)
    var won: Int = 0,

    @Column(nullable = false)
    var lost: Int = 0,

    @Column(nullable = false)
    var draw: Int = 0,

    @Column(nullable = false)
    var score: Int = 0,

    @Column(nullable = false)
    var mvp: Int = 0
)