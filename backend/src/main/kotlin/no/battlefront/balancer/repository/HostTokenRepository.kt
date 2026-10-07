package no.battlefront.balancer.repository

import no.battlefront.balancer.model.HostToken
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [HostToken] entities.
 */
interface HostTokenRepository : JpaRepository<HostToken, Long> {
    fun findByTokenHash(tokenHash: String): HostToken?

    fun findAllByOrderByIdDesc(): List<HostToken>
}
