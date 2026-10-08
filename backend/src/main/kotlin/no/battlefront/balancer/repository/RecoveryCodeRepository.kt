package no.battlefront.balancer.repository

import no.battlefront.balancer.model.RecoveryCode
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [RecoveryCode] entities.
 */
interface RecoveryCodeRepository : JpaRepository<RecoveryCode, Long> {
    fun findByUserIdAndCodeHashAndUsedAtIsNull(
        userId: Long,
        codeHash: String,
    ): RecoveryCode?

    fun deleteByUserId(userId: Long)
}
