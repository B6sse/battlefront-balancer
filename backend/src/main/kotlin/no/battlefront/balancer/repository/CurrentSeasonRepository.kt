package no.battlefront.balancer.repository

import no.battlefront.balancer.model.CurrentSeason
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CurrentSeasonRepository : JpaRepository<CurrentSeason, Int> {

    @Query("SELECT MAX(c.season) FROM CurrentSeason c")
    fun findCurrentSeason(): Int?
}
