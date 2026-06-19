package no.battlefront.balancer.controller

import no.battlefront.balancer.service.SeasonService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/season")
class SeasonController(
    private val seasonService: SeasonService,
) {
    @GetMapping("/current")
    fun getCurrent(): ResponseEntity<Map<String, Int>> = ResponseEntity.ok(mapOf("season" to seasonService.currentSeason()))

    @PostMapping("/start")
    fun startNextSeason(): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(seasonService.startNextSeason())
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Failed to start season")))
        }

    @PostMapping("/cleanup")
    fun cleanupSeason(
        @RequestBody body: Map<String, Int>,
    ): ResponseEntity<Any> =
        try {
            val season = body["season"] ?: return ResponseEntity.badRequest().body(mapOf("message" to "season is required"))
            ResponseEntity.ok(seasonService.cleanupSeason(season))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Failed to cleanup")))
        }
}
