package no.battlefront.balancer.repository

import no.battlefront.balancer.model.Relay
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [Relay] entities.
 */
interface RelayRepository : JpaRepository<Relay, String> {
    fun findAllByOrderByRegionAscIdAsc(): List<Relay>
}
