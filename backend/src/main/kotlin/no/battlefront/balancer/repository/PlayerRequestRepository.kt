package no.battlefront.balancer.repository

import no.battlefront.balancer.model.PlayerRequest
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [PlayerRequest] entities.
 */
interface PlayerRequestRepository : JpaRepository<PlayerRequest, Long> {
    fun findByPersonaIdAndStatus(
        personaId: Long,
        status: String,
    ): PlayerRequest?

    fun findByStatusOrderByRequestedAtDesc(status: String): List<PlayerRequest>
}
