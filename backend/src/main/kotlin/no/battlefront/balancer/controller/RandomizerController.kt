package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.RandomizerDto
import no.battlefront.balancer.dto.RandomizerSubmitRequest
import no.battlefront.balancer.dto.RandomizerWeightsResponse
import no.battlefront.balancer.dto.RandomizerWeightsUpdateRequest
import no.battlefront.balancer.service.RandomizerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class RandomizerController(
    private val randomizerService: RandomizerService,
) {
    @GetMapping("/randomizer")
    fun getLatest(): ResponseEntity<RandomizerDto> = ResponseEntity.ok(randomizerService.getLatest())

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

    @GetMapping("/randomizer/weights")
    fun getWeights(): ResponseEntity<RandomizerWeightsResponse> = ResponseEntity.ok(randomizerService.getWeights())

    @PutMapping("/randomizer/weights")
    fun updateWeights(
        @RequestBody request: RandomizerWeightsUpdateRequest,
    ): ResponseEntity<Any> =
        try {
            randomizerService.updateWeights(request)
            ResponseEntity.ok(randomizerService.getWeights())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        }
}
