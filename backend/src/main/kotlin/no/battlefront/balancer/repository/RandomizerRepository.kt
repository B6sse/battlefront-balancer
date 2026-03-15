package no.battlefront.balancer.repository

import no.battlefront.balancer.model.Randomizer
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RandomizerRepository : JpaRepository<Randomizer, Long> {

    fun findTop1ByOrderByIdDesc(): Randomizer?
}
