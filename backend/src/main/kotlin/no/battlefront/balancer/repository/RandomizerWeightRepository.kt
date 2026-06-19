package no.battlefront.balancer.repository

import no.battlefront.balancer.model.RandomizerWeight
import org.springframework.data.jpa.repository.JpaRepository

interface RandomizerWeightRepository : JpaRepository<RandomizerWeight, Int> {
    fun findByTypeOrderByIdAsc(type: String): List<RandomizerWeight>
}
