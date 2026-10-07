package no.battlefront.balancer.repository

import no.battlefront.balancer.model.RankedMatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * Spring Data JPA repository for [RankedMatch] entities.
 */
interface RankedMatchRepository : JpaRepository<RankedMatch, Long> {
    /**
     * Returns matches for the given season, newest first.
     */
    fun findBySeasonOrderByIdDesc(season: Int): List<RankedMatch>

    /**
     * Returns all matches across all seasons, newest first.
     */
    fun findAllByOrderByIdDesc(): List<RankedMatch>

    /**
     * Returns the most recently created match by id, or null if none exist.
     */
    fun findTop1ByOrderByIdDesc(): RankedMatch?

    /**
     * Returns the most recent match in the given season, or null.
     */
    fun findTop1BySeasonOrderByIdDesc(season: Int): RankedMatch?

    /**
     * Returns all distinct season numbers present in the matches table.
     */
    @Query("SELECT DISTINCT m.season FROM RankedMatch m ORDER BY m.season")
    fun findDistinctSeasons(): List<Int>
}
