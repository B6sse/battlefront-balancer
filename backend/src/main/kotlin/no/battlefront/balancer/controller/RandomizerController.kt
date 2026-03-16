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
class RandomizerController(private val randomizerService: RandomizerService) {

    /**
     * GET /api/randomizer – siste map/rule (tilsvarer api_randomizer.php).
     * Default "Dune Sea" / "DSE" ved tom tabell.
     */
    @GetMapping("/randomizer")
    fun getLatest(): ResponseEntity<RandomizerDto> =
        ResponseEntity.ok(randomizerService.getLatest())

    /**
     * POST /api/randomizer – lagre ny map/rule (tilsvarer randomizerSubmit.php).
     */
    @PostMapping("/randomizer")
    fun submit(@RequestBody request: RandomizerSubmitRequest): ResponseEntity<Map<String, Any>> {
        return try {
            randomizerService.save(request.map, request.rule)
            ResponseEntity.ok(mapOf("success" to true, "message" to "Data saved successfully"))
        } catch (e: Exception) {
            ResponseEntity.badRequest()
                .body(mapOf("success" to false, "message" to (e.message ?: "Error saving randomizer")))
        }
    }
}
