package no.battlefront.balancer.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    @GetMapping("/api/health")
    fun health(): Map<String, String> = mapOf("status" to "UP")
}
