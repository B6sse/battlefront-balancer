package no.battlefront.balancer.repository

import no.battlefront.balancer.model.RelaySession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Spring Data JPA repository for [RelaySession] entities. The delete queries run immediately, so a host's old
 * session is gone before its new one is inserted.
 */
interface RelaySessionRepository : JpaRepository<RelaySession, String> {
    fun findAllByLastHeartbeatMsGreaterThanEqualOrderByCreatedAtAscSidAsc(cutoffMs: Long): List<RelaySession>

    @Modifying
    @Query("delete from RelaySession s where s.hostTokenId = :hostTokenId")
    fun deleteByHostToken(
        @Param("hostTokenId") hostTokenId: Long,
    ): Int

    @Modifying
    @Query("delete from RelaySession s where s.lastHeartbeatMs < :cutoffMs")
    fun deleteExpired(
        @Param("cutoffMs") cutoffMs: Long,
    ): Int

    @Modifying
    @Query("delete from RelaySession s where s.relayId = :relayId")
    fun deleteByRelay(
        @Param("relayId") relayId: String,
    ): Int

    @Modifying
    @Query("delete from RelaySession s where s.sid = :sid and s.hostTokenId = :hostTokenId")
    fun deleteOwned(
        @Param("sid") sid: String,
        @Param("hostTokenId") hostTokenId: Long,
    ): Int
}
