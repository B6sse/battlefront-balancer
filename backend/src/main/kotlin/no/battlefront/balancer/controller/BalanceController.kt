package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.BalanceRequest
import no.battlefront.balancer.service.BalanceService
import no.battlefront.balancer.service.UnknownPlayersException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class BalanceController(
    private val balanceService: BalanceService,
) {
    /**
     * Splits the given players into two balanced teams (used by Auric to set teams in game).
     *
     * @return 200 with the teams, 400 on invalid input, or 422 with `unknown` if any persona ID is not registered.
     */
    @PostMapping("/balance")
    fun balance(
        @RequestBody request: BalanceRequest,
    ): ResponseEntity<Any> =
        try {
            ResponseEntity.ok(balanceService.balance(request))
        } catch (e: UnknownPlayersException) {
            ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(mapOf("message" to "Some players are not registered", "unknown" to e.unknown))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("message" to (e.message ?: "Invalid request")))
        }
}
