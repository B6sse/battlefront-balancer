package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.RandomizerDto
import no.battlefront.balancer.dto.RandomizerSubmitRequest
import no.battlefront.balancer.service.RandomizerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class RandomizerController(
    private val randomizerService: RandomizerService,
) {
    /**
     * Returns the latest randomizer entry (map and rule). Uses default "Dune Sea" / "DSE" when the table is empty.
     *
     * @return [ResponseEntity] containing [RandomizerDto] with map and rule.
     */
    @GetMapping("/randomizer")
    fun getLatest(): ResponseEntity<RandomizerDto> = ResponseEntity.ok(randomizerService.getLatest())

    /**
     * Saves a new randomizer entry (map and rule).
     *
     * @param request the map and rule to store.
     * @return [ResponseEntity] with success or error message and status 200 or 400.
     */
    @PostMapping("/randomizer")
    fun submit(
        @RequestBody request: RandomizerSubmitRequest,
    ): ResponseEntity<Map<String, Any>> =
        try {
            randomizerService.save(request.map, request.rule)
            ResponseEntity.ok(mapOf("success" to true, "message" to "Data saved successfully"))
        } catch (e: Exception) {
            ResponseEntity
                .badRequest()
                .body(mapOf("success" to false, "message" to (e.message ?: "Error saving randomizer")))
        }
}
