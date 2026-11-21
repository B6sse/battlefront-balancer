package no.battlefront.balancer.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * How a mod relates to the host's mods.
 */
enum class ModCategory {
    /** Any player may have it or not. */
    OPTIONAL,

    /** Changes gameplay; every player must have exactly what the host has. */
    MUST_MATCH_HOST,
}

/**
 * A game mod approved and signed by the site owner. The mod file is not stored here; [downloadUrl] may point to
 * wherever the signed file is hosted.
 *
 * @param contentSha256 lower-case hex SHA-256 of the mod's content; identifies the mod
 * @param keyId which signing key was used
 * @param axSha256 lower-case hex SHA-256 of the signed file
 * @param axSize size of the signed file in bytes
 * @param active false when the approval is withdrawn; an inactive mod counts as not approved
 * @param notes for admins only; never returned by the public endpoint
 */
@Entity
@Table(name = "approved_mods")
class ApprovedMod(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "content_sha256", nullable = false, unique = true, length = 64)
    var contentSha256: String = "",
    @Column(name = "content_size", nullable = false)
    var contentSize: Long = 0,
    @Column(nullable = false, length = 200)
    var title: String = "",
    @Column(nullable = false, length = 50)
    var version: String = "",
    @Column(nullable = false, length = 200)
    var author: String = "",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var category: ModCategory = ModCategory.OPTIONAL,
    @Column(name = "key_id", nullable = false, length = 100)
    var keyId: String = "",
    @Column(name = "ax_sha256", nullable = false, length = 64)
    var axSha256: String = "",
    @Column(name = "ax_size", nullable = false)
    var axSize: Long = 0,
    @Column(name = "download_url", length = 2000)
    var downloadUrl: String? = null,
    @Column(nullable = false)
    var active: Boolean = true,
    @Column(columnDefinition = "TEXT")
    var notes: String? = null,
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
