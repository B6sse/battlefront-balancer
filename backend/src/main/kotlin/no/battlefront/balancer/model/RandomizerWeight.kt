package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "randomizer_weights")
class RandomizerWeight(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    @Column(nullable = false, length = 10)
    val type: String = "",
    @Column(nullable = false, length = 50)
    val name: String = "",
    @Column(nullable = false)
    var weight: Int = 0,
)
