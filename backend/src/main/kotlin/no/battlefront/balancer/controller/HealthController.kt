package no.battlefront.balancer.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {

    /**
     * Health check endpoint for load balancers and monitoring.
     *
     * @return a map with "status" set to "UP" when the application is running.
     */
    @GetMapping("/api/health")
    fun health(): Map<String, String> = mapOf("status" to "UP")
}
