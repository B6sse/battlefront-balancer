package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.ApprovedModDto
import no.battlefront.balancer.dto.ApprovedModRequest
import no.battlefront.balancer.dto.ApprovedModsResponse
import no.battlefront.balancer.service.ApprovedModService
import no.battlefront.balancer.service.DuplicateModException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * The approved game mods list: public list of active mods, and admin management.
 */
@RestController
@RequestMapping("/api")
class ApprovedModController(
    private val approvedModService: ApprovedModService,
    private val etagResponses: EtagResponses,
) {
    /**
     * Public. Every Auric host fetches this when it starts a server, and the launcher to offer downloads.
     *
     * @return 200 with the active mods and an ETag, or 304 if If-None-Match matches
     */
    @GetMapping("/mods/approved")
    fun approved(): ResponseEntity<ApprovedModsResponse> = etagResponses.ok(approvedModService.publicList())

    /** All mods, including inactive ones and notes. */
    @GetMapping("/admin/mods")
    fun list(): ResponseEntity<List<ApprovedModDto>> = ResponseEntity.ok(approvedModService.list())

    /**
     * @return 200 with the mod, 400 on an invalid field, or 409 if the contentSha256 is already in the list
     */
    @PostMapping("/admin/mods")
    fun create(
        @RequestBody request: ApprovedModRequest,
    ): ResponseEntity<Any> = handle { approvedModService.create(request) }

    /**
     * Replaces all fields; also used to deactivate and reactivate (field `active`).
     *
     * @return 200 with the mod, 400 on an invalid field or unknown id, or 409 if the contentSha256 belongs to another mod
     */
    @PutMapping("/admin/mods/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody request: ApprovedModRequest,
    ): ResponseEntity<Any> = handle { approvedModService.update(id, request) }

    @DeleteMapping("/admin/mods/{id}")
    fun delete(
        @PathVariable id: Long,
    ): ResponseEntity<Any> = handle { approvedModService.delete(id) }

    private fun handle(action: () -> Any): ResponseEntity<Any> =
        try {
            val result = action()
            if (result is Unit) ResponseEntity.noContent().build() else ResponseEntity.ok(result)
        } catch (e: DuplicateModException) {
            ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("message" to e.message, "existingId" to e.existingId))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        }
}
