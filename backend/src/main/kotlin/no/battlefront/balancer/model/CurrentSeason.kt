package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Singleton-style row holding the current ranked season number. Used to know which season is active.
 */
@Entity
@Table(name = "current_season")
class CurrentSeason(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,
    @Column(nullable = false)
    var season: Int = 0,
)
