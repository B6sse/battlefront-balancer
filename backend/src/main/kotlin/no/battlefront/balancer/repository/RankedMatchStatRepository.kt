package no.battlefront.balancer.repository

import no.battlefront.balancer.model.RankedMatchStat
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [RankedMatchStat] (per-player stats per match).
 */
interface RankedMatchStatRepository : JpaRepository<RankedMatchStat, Long> {
    /**
     * Returns all player stats for the given match.
     */
    fun findByMatchId(matchId: Long): List<RankedMatchStat>
}
