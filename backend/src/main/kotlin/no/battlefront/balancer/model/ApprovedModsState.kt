package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Single row (id 1) recording when the approved mods list last changed, including deletions, which leave no row
 * behind to carry a timestamp.
 */
@Entity
@Table(name = "approved_mods_state")
class ApprovedModsState(
    @Id
    val id: Int = ID,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        const val ID = 1
    }
}
