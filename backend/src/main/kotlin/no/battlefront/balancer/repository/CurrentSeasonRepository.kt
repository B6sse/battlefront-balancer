package no.battlefront.balancer.repository

import no.battlefront.balancer.model.CurrentSeason
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * Spring Data JPA repository for [CurrentSeason] (single row indicating active season).
 */
interface CurrentSeasonRepository : JpaRepository<CurrentSeason, Int> {

    /**
     * Returns the current season number (max season in table), or null if empty.
     */
    @Query("SELECT MAX(c.season) FROM CurrentSeason c")
    fun findCurrentSeason(): Int?
}
