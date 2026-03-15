package no.battlefront.balancer.repository

import no.battlefront.balancer.model.RankedMatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RankedMatchRepository : JpaRepository<RankedMatch, Long> {

    fun findBySeasonOrderByDateDesc(season: Int): List<RankedMatch>
}
