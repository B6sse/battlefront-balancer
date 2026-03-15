package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.RandomizerDto
import no.battlefront.balancer.service.RandomizerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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
}
