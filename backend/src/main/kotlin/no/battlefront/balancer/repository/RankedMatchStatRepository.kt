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

    /**
     * Returns all match stats for a player across all seasons, newest match first.
     */
    fun findByPlayerIdOrderByMatchIdDesc(playerId: Long): List<RankedMatchStat>

    /**
     * Returns all match stats for a player in a given season, newest match first.
     */
    fun findByPlayerIdAndSeasonOrderByMatchIdDesc(
        playerId: Long,
        season: Int,
    ): List<RankedMatchStat>
}
