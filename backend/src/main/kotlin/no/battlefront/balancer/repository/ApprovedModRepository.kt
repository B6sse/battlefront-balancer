package no.battlefront.balancer.repository

import no.battlefront.balancer.model.ApprovedMod
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [ApprovedMod] entities.
 */
interface ApprovedModRepository : JpaRepository<ApprovedMod, Long> {
    fun findByContentSha256(contentSha256: String): ApprovedMod?

    fun findAllByOrderByTitleAscVersionAscIdAsc(): List<ApprovedMod>

    fun findAllByActiveTrueOrderByTitleAscVersionAscIdAsc(): List<ApprovedMod>
}
