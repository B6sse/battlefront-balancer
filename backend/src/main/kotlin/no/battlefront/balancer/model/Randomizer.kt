package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

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
    var rule: String = "",
)
