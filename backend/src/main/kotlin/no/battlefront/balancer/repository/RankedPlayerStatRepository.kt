package no.battlefront.balancer.repository

import no.battlefront.balancer.model.RankedPlayerStat
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

/**
 * Spring Data JPA repository for [RankedPlayerStat] (season stats per player).
 */
interface RankedPlayerStatRepository : JpaRepository<RankedPlayerStat, Long> {

    /**
     * Returns all season stats for the given season, ordered by BR descending.
     */
    @Query("SELECT r FROM RankedPlayerStat r WHERE r.season = :season ORDER BY r.br DESC")
    fun findBySeason(season: Int): List<RankedPlayerStat>

    /**
     * Returns the season stat for the given player and season, or null if none.
     */
    fun findByPlayerIdAndSeason(playerId: Long, season: Int): RankedPlayerStat?
}
