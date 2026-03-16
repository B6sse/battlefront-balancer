package no.battlefront.balancer.model

import jakarta.persistence.*

@Entity
@Table(name = "players")
class Player(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    var nickname: String = "",

    @Column(nullable = false, length = 2)
    var nation: String = "",  // e.g. "no", "us"

    @Column(nullable = false)
    var rating: Int = 0,

    @Column(name = "dz_rating", nullable = false)
    var dzrating: Int = 0,

    @Column(nullable = false)
    var elo: Int = 0
)