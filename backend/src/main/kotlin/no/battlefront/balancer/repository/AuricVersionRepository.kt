package no.battlefront.balancer.repository

import no.battlefront.balancer.model.AuricVersion
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for the single [AuricVersion] row.
 */
interface AuricVersionRepository : JpaRepository<AuricVersion, Int>
