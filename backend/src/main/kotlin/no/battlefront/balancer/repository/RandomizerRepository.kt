package no.battlefront.balancer.repository

import no.battlefront.balancer.model.Randomizer
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [Randomizer] (map/rule entries).
 */
interface RandomizerRepository : JpaRepository<Randomizer, Long> {

    /**
     * Returns the most recently added randomizer entry, or null if none exist.
     */
    fun findTop1ByOrderByIdDesc(): Randomizer?
}
