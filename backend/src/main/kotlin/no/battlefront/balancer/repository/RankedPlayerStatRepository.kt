package no.battlefront.balancer.repository

import no.battlefront.balancer.model.RankedPlayerStat
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RankedPlayerStatRepository : JpaRepository<RankedPlayerStat, Long> {

    @Query("SELECT r FROM RankedPlayerStat r WHERE r.season = :season ORDER BY r.br DESC")
    fun findBySeason(season: Int): List<RankedPlayerStat>

    fun findByPlayerIdAndSeason(playerId: Long, season: Int): RankedPlayerStat?
}
