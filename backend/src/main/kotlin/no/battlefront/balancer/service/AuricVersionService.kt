package no.battlefront.balancer.service

import no.battlefront.balancer.dto.AuricVersionAdminDto
import no.battlefront.balancer.dto.AuricVersionDto
import no.battlefront.balancer.dto.AuricVersionRequest
import no.battlefront.balancer.model.AuricVersion
import no.battlefront.balancer.repository.AuricVersionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The latest and minimum Auric version (one row), edited by admins and read publicly by Auric and the launcher.
 */
@Service
class AuricVersionService(
    private val auricVersionRepository: AuricVersionRepository,
) {
    /**
     * @return the public version info, or null if an admin has not saved it yet
     */
    fun get(): AuricVersionDto? =
        find()?.let {
            AuricVersionDto(
                latestVersion = it.latestVersion,
                minimumVersion = it.minimumVersion,
                downloadUrl = it.downloadUrl,
                sha256 = it.sha256,
                releaseNotes = it.releaseNotes,
            )
        }

    /** The settings for the admin page; all null until saved the first time. */
    fun getForAdmin(): AuricVersionAdminDto = toAdminDto(find())

    /**
     * Saves all settings, replacing the previous ones.
     *
     * @throws IllegalArgumentException if a version is not three dot-separated numbers, minimumVersion is higher than
     * latestVersion, the URL is not https, the SHA-256 is not 64 hex characters, or the release notes are too long
     */
    @Transactional
    fun update(request: AuricVersionRequest): AuricVersionAdminDto {
        val latest = parseVersion(request.latestVersion, "latestVersion")
        val minimum = parseVersion(request.minimumVersion, "minimumVersion")
        require(compare(minimum.parts, latest.parts) <= 0) {
            "minimumVersion (${minimum.text}) must not be higher than latestVersion (${latest.text})"
        }
        val downloadUrl = FieldValidation.optionalHttpsUrl(request.downloadUrl, "downloadUrl")
        val sha256 = FieldValidation.optionalSha256(request.sha256, "sha256")
        val releaseNotes = FieldValidation.optionalText(request.releaseNotes, "releaseNotes", MAX_RELEASE_NOTES_LENGTH)

        val row = find() ?: AuricVersion()
        row.latestVersion = latest.text
        row.minimumVersion = minimum.text
        row.downloadUrl = downloadUrl
        row.sha256 = sha256
        row.releaseNotes = releaseNotes
        row.updatedAt = LocalDateTime.now()
        return toAdminDto(auricVersionRepository.save(row))
    }

    private fun find(): AuricVersion? = auricVersionRepository.findById(AuricVersion.ID).orElse(null)

    private fun toAdminDto(v: AuricVersion?) =
        AuricVersionAdminDto(
            latestVersion = v?.latestVersion,
            minimumVersion = v?.minimumVersion,
            downloadUrl = v?.downloadUrl,
            sha256 = v?.sha256,
            releaseNotes = v?.releaseNotes,
            updatedAt = v?.updatedAt?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        )

    private data class Version(
        val text: String,
        val parts: List<Int>,
    )

    private fun parseVersion(
        value: String?,
        field: String,
    ): Version {
        val v = value?.trim().orEmpty()
        require(v.isNotEmpty()) { "$field is required" }
        require(VERSION_RE.matches(v)) { "$field must be three dot-separated numbers, e.g. 1.4.0" }
        return Version(v, v.split('.').map { it.toInt() })
    }

    private fun compare(
        a: List<Int>,
        b: List<Int>,
    ): Int = a.zip(b).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0

    private companion object {
        /** Up to six digits per part keeps the numbers in an Int and the text within the 20-character column. */
        val VERSION_RE = Regex("^\\d{1,6}\\.\\d{1,6}\\.\\d{1,6}$")
        const val MAX_RELEASE_NOTES_LENGTH = 2000
    }
}
