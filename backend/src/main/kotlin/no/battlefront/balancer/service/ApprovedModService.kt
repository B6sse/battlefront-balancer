package no.battlefront.balancer.service

import no.battlefront.balancer.APP_TIME_ZONE
import no.battlefront.balancer.dto.ApprovedModDto
import no.battlefront.balancer.dto.ApprovedModPublicDto
import no.battlefront.balancer.dto.ApprovedModRequest
import no.battlefront.balancer.dto.ApprovedModsResponse
import no.battlefront.balancer.model.ApprovedMod
import no.battlefront.balancer.model.ApprovedModsState
import no.battlefront.balancer.model.ModCategory
import no.battlefront.balancer.repository.ApprovedModRepository
import no.battlefront.balancer.repository.ApprovedModsStateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Thrown when a mod with the same contentSha256 is already in the list.
 */
class DuplicateModException(
    val existingId: Long,
    message: String,
) : RuntimeException(message)

/**
 * The list of approved game mods: admin create, edit, (de)activate and delete, and the public list of active mods.
 * Every change also records when the list last changed, so deletions show up in the public `updatedAt`.
 */
@Service
class ApprovedModService(
    private val approvedModRepository: ApprovedModRepository,
    private val stateRepository: ApprovedModsStateRepository,
) {
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun list(): List<ApprovedModDto> = approvedModRepository.findAllByOrderByTitleAscVersionAscIdAsc().map(::toDto)

    /**
     * Active mods only, without notes.
     */
    @Transactional(readOnly = true)
    fun publicList(): ApprovedModsResponse {
        val mods = approvedModRepository.findAllByActiveTrueOrderByTitleAscVersionAscIdAsc()
        val updatedAt = stateRepository.findById(ApprovedModsState.ID).map { it.updatedAt }.orElse(null)
        return ApprovedModsResponse(
            updatedAt = toUtcIso(updatedAt),
            mods =
                mods.map {
                    ApprovedModPublicDto(
                        title = it.title,
                        version = it.version,
                        author = it.author,
                        category = it.category.name,
                        contentSha256 = it.contentSha256,
                        contentSize = it.contentSize,
                        keyId = it.keyId,
                        axSha256 = it.axSha256,
                        axSize = it.axSize,
                        downloadUrl = it.downloadUrl,
                    )
                },
        )
    }

    /**
     * @throws IllegalArgumentException on an invalid field
     * @throws DuplicateModException if the contentSha256 is already in the list
     */
    @Transactional
    fun create(request: ApprovedModRequest): ApprovedModDto {
        val fields = validate(request)
        ensureUnique(fields.contentSha256, exceptId = null)
        val now = LocalDateTime.now()
        val mod = ApprovedMod(createdAt = now)
        fields.applyTo(mod)
        mod.active = request.active ?: true
        mod.updatedAt = now
        val saved = approvedModRepository.save(mod)
        touch(now)
        return toDto(saved)
    }

    /**
     * Replaces all fields of a mod. [ApprovedModRequest.active] null keeps the current state.
     *
     * @throws IllegalArgumentException if the mod does not exist or a field is invalid
     * @throws DuplicateModException if the new contentSha256 belongs to another mod
     */
    @Transactional
    fun update(
        id: Long,
        request: ApprovedModRequest,
    ): ApprovedModDto {
        val mod = approvedModRepository.findById(id).orElse(null) ?: throw IllegalArgumentException("Mod not found")
        val fields = validate(request)
        ensureUnique(fields.contentSha256, exceptId = id)
        fields.applyTo(mod)
        request.active?.let { mod.active = it }
        val now = LocalDateTime.now()
        mod.updatedAt = now
        val saved = approvedModRepository.save(mod)
        touch(now)
        return toDto(saved)
    }

    /**
     * @throws IllegalArgumentException if the mod does not exist
     */
    @Transactional
    fun delete(id: Long) {
        val mod = approvedModRepository.findById(id).orElse(null) ?: throw IllegalArgumentException("Mod not found")
        approvedModRepository.delete(mod)
        touch(LocalDateTime.now())
    }

    private fun ensureUnique(
        contentSha256: String,
        exceptId: Long?,
    ) {
        val existing = approvedModRepository.findByContentSha256(contentSha256) ?: return
        if (existing.id == exceptId) return
        val state = if (existing.active) "" else ", inactive"
        throw DuplicateModException(
            existing.id,
            "A mod with this contentSha256 is already in the list: ${existing.title} ${existing.version}$state",
        )
    }

    private fun touch(now: LocalDateTime) {
        val state = stateRepository.findById(ApprovedModsState.ID).orElse(null) ?: ApprovedModsState()
        state.updatedAt = now
        stateRepository.save(state)
    }

    private fun validate(r: ApprovedModRequest): ValidFields {
        val category = r.category?.trim().orEmpty()
        require(category.isNotEmpty()) { "category is required" }
        val parsedCategory =
            ModCategory.entries.firstOrNull { it.name == category }
                ?: throw IllegalArgumentException(
                    "Unknown category \"$category\": must be ${ModCategory.entries.joinToString(" or ") { it.name }}",
                )
        return ValidFields(
            title = FieldValidation.requireText(r.title, "title", 200),
            version = FieldValidation.requireText(r.version, "version", 50),
            author = FieldValidation.requireText(r.author, "author", 200),
            category = parsedCategory,
            contentSha256 = FieldValidation.requireSha256(r.contentSha256, "contentSha256"),
            contentSize = requireSize(r.contentSize, "contentSize"),
            keyId = FieldValidation.requireText(r.keyId, "keyId", 100),
            axSha256 = FieldValidation.requireSha256(r.axSha256, "axSha256"),
            axSize = requireSize(r.axSize, "axSize"),
            downloadUrl = FieldValidation.optionalHttpsUrl(r.downloadUrl, "downloadUrl"),
            notes = FieldValidation.optionalText(r.notes, "notes", MAX_NOTES_LENGTH),
        )
    }

    private fun requireSize(
        value: Long?,
        field: String,
    ): Long {
        require(value != null) { "$field is required" }
        require(value > 0) { "$field must be a positive number of bytes" }
        return value
    }

    private fun toDto(m: ApprovedMod) =
        ApprovedModDto(
            id = m.id,
            title = m.title,
            version = m.version,
            author = m.author,
            category = m.category.name,
            contentSha256 = m.contentSha256,
            contentSize = m.contentSize,
            keyId = m.keyId,
            axSha256 = m.axSha256,
            axSize = m.axSize,
            downloadUrl = m.downloadUrl,
            active = m.active,
            notes = m.notes,
            createdAt = m.createdAt.format(isoFormatter),
            updatedAt = m.updatedAt.format(isoFormatter),
        )

    /** Local (Oslo) time as an ISO 8601 UTC instant, to whole seconds; the epoch if the list has never changed. */
    private fun toUtcIso(time: LocalDateTime?): String {
        val instant = time?.atZone(ZoneId.of(APP_TIME_ZONE))?.toInstant() ?: Instant.EPOCH
        return instant.truncatedTo(ChronoUnit.SECONDS).toString()
    }

    private data class ValidFields(
        val title: String,
        val version: String,
        val author: String,
        val category: ModCategory,
        val contentSha256: String,
        val contentSize: Long,
        val keyId: String,
        val axSha256: String,
        val axSize: Long,
        val downloadUrl: String?,
        val notes: String?,
    ) {
        fun applyTo(mod: ApprovedMod) {
            mod.title = title
            mod.version = version
            mod.author = author
            mod.category = category
            mod.contentSha256 = contentSha256
            mod.contentSize = contentSize
            mod.keyId = keyId
            mod.axSha256 = axSha256
            mod.axSize = axSize
            mod.downloadUrl = downloadUrl
            mod.notes = notes
        }
    }

    private companion object {
        const val MAX_NOTES_LENGTH = 5000
    }
}
