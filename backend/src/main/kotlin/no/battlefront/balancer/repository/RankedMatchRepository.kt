package no.battlefront.balancer.repository

import no.battlefront.balancer.model.RankedMatch
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [RankedMatch] entities.
 */
interface RankedMatchRepository : JpaRepository<RankedMatch, Long> {
    /**
     * Returns matches for the given season, newest first.
     */
    fun findBySeasonOrderByDateDesc(season: Int): List<RankedMatch>

    /**
     * Returns the most recently created match by id, or null if none exist.
     */
    fun findTop1ByOrderByIdDesc(): RankedMatch?
}
