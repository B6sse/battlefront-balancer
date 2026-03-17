package no.battlefront.balancer.model

import jakarta.persistence.*

/**
 * A map/rule pair used by the randomizer (e.g. for picking a random map and rule combination).
 */
@Entity
@Table(name = "randomizer")
class Randomizer(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    var map: String = "",

    @Column(nullable = false, length = 50)
    var rule: String = ""
)