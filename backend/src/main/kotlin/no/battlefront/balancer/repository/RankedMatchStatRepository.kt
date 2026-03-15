package no.battlefront.balancer.repository

import no.battlefront.balancer.model.RankedMatchStat
import org.springframework.data.jpa.repository.JpaRepository

interface RankedMatchStatRepository : JpaRepository<RankedMatchStat, Long> {

    fun findByMatchId(matchId: Long): List<RankedMatchStat>
}
