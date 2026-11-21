package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * Single row (id 1) with the latest and minimum version of Auric, edited by admins.
 *
 * @param latestVersion newest public version, e.g. "1.4.0"
 * @param minimumVersion oldest version that may still be used; never higher than [latestVersion]
 * @param downloadUrl https URL of the latest public DLL, if any
 * @param sha256 lower-case hex SHA-256 of the latest public DLL, if any
 */
@Entity
@Table(name = "auric_version")
class AuricVersion(
    @Id
    val id: Int = ID,
    @Column(name = "latest_version", nullable = false, length = 20)
    var latestVersion: String = "",
    @Column(name = "minimum_version", nullable = false, length = 20)
    var minimumVersion: String = "",
    @Column(name = "download_url", length = 2000)
    var downloadUrl: String? = null,
    @Column(length = 64)
    var sha256: String? = null,
    @Column(name = "release_notes", length = 2000)
    var releaseNotes: String? = null,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        const val ID = 1
    }
}
