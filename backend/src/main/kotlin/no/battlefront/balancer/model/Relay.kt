package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * A relay server that forwards the game's UDP packets between a host and the players. The relay never talks to this
 * site; it accepts tickets signed with a secret it shares with the site.
 *
 * @param id short text chosen by the admin, e.g. "eu1"; part of every ticket for this relay
 * @param region e.g. "EU" or "NA"
 * @param host DNS name or IP address players connect to
 * @param active false hides the relay and its sessions
 */
@Entity
@Table(name = "relays")
class Relay(
    @Id
    @Column(length = 20)
    val id: String = "",
    @Column(nullable = false, length = 10)
    var region: String = "",
    @Column(nullable = false, length = 60)
    var name: String = "",
    @Column(nullable = false, length = 253)
    var host: String = "",
    @Column(nullable = false)
    var port: Int = DEFAULT_PORT,
    @Column(nullable = false)
    var active: Boolean = true,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        const val DEFAULT_PORT = 25200
    }
}
