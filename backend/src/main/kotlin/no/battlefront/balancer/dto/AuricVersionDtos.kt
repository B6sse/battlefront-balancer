package no.battlefront.balancer.dto

/**
 * Request body for PUT /api/admin/auric-version.
 *
 * @param latestVersion and [minimumVersion] are three dot-separated numbers, e.g. "1.4.0"; minimum ≤ latest
 * @param downloadUrl optional https URL of the latest public DLL; null or blank for none
 * @param sha256 optional SHA-256 (64 hex characters) of the latest public DLL
 * @param releaseNotes optional short text
 */
data class AuricVersionRequest(
    val latestVersion: String? = null,
    val minimumVersion: String? = null,
    val downloadUrl: String? = null,
    val sha256: String? = null,
    val releaseNotes: String? = null,
)

/**
 * Response for GET /api/auric/version.
 */
data class AuricVersionDto(
    val latestVersion: String,
    val minimumVersion: String,
    val downloadUrl: String?,
    val sha256: String?,
    val releaseNotes: String?,
)

/**
 * Response for GET and PUT /api/admin/auric-version. All fields are null until an admin has saved the settings.
 *
 * @param updatedAt when the settings were last saved (local time, ISO 8601), or null if never
 */
data class AuricVersionAdminDto(
    val latestVersion: String?,
    val minimumVersion: String?,
    val downloadUrl: String?,
    val sha256: String?,
    val releaseNotes: String?,
    val updatedAt: String?,
)
