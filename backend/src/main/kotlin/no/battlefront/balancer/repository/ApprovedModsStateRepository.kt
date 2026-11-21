package no.battlefront.balancer.repository

import no.battlefront.balancer.model.ApprovedModsState
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for the single [ApprovedModsState] row.
 */
interface ApprovedModsStateRepository : JpaRepository<ApprovedModsState, Int>
