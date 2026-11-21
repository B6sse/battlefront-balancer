package no.battlefront.balancer.dto

/**
 * Request body for POST /api/admin/mods and PUT /api/admin/mods/{id}. The first nine fields are the JSON the signing
 * tool prints. Fields are nullable so a missing one gets a clear message instead of a parse error.
 *
 * @param category OPTIONAL or MUST_MATCH_HOST
 * @param downloadUrl optional https URL of the signed file; null or blank for none
 * @param active null means true on create and unchanged on update
 */
data class ApprovedModRequest(
    val title: String? = null,
    val version: String? = null,
    val author: String? = null,
    val category: String? = null,
    val contentSha256: String? = null,
    val contentSize: Long? = null,
    val keyId: String? = null,
    val axSha256: String? = null,
    val axSize: Long? = null,
    val downloadUrl: String? = null,
    val notes: String? = null,
    val active: Boolean? = null,
)

/**
 * Approved mod as listed for admins (GET /api/admin/mods), including inactive ones and notes.
 */
data class ApprovedModDto(
    val id: Long,
    val title: String,
    val version: String,
    val author: String,
    val category: String,
    val contentSha256: String,
    val contentSize: Long,
    val keyId: String,
    val axSha256: String,
    val axSize: Long,
    val downloadUrl: String?,
    val active: Boolean,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * Public view of an active approved mod. Never includes notes.
 *
 * @param downloadUrl null when the mod has no download link
 */
data class ApprovedModPublicDto(
    val title: String,
    val version: String,
    val author: String,
    val category: String,
    val contentSha256: String,
    val contentSize: Long,
    val keyId: String,
    val axSha256: String,
    val axSize: Long,
    val downloadUrl: String?,
)

/**
 * Response for GET /api/mods/approved.
 *
 * @param updatedAt when the list last changed (including deactivations and deletions), ISO 8601 in UTC
 * @param mods active mods, sorted by title, then version
 */
data class ApprovedModsResponse(
    val updatedAt: String,
    val mods: List<ApprovedModPublicDto>,
)
