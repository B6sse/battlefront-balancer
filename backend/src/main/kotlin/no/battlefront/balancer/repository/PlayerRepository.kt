package no.battlefront.balancer.repository

import no.battlefront.balancer.model.Player
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [Player] entities.
 * Provides CRUD via [JpaRepository] plus custom lookups.
 */
interface PlayerRepository : JpaRepository<Player, Long> {
    fun findByPersonaId(personaId: Long): Player?

    fun findByPersonaIdIn(personaIds: Collection<Long>): List<Player>
}
