package no.battlefront.balancer.model

import jakarta.persistence.*

@Entity
@Table(name = "current_season")
class CurrentSeason(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(nullable = false)
    var season: Int = 0
)
