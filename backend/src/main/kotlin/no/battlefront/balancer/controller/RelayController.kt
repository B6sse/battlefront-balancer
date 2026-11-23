package no.battlefront.balancer.controller

import jakarta.servlet.http.HttpServletRequest
import no.battlefront.balancer.dto.RelayAdminDto
import no.battlefront.balancer.dto.RelayJoinRequest
import no.battlefront.balancer.dto.RelayRequest
import no.battlefront.balancer.dto.RelaySessionCreateRequest
import no.battlefront.balancer.dto.RelaySessionHeartbeatRequest
import no.battlefront.balancer.dto.RelaysResponse
import no.battlefront.balancer.ratelimit.RelayJoinRateLimiter
import no.battlefront.balancer.security.HostPrincipal
import no.battlefront.balancer.service.DuplicateRelayException
import no.battlefront.balancer.service.RelayNotConfiguredException
import no.battlefront.balancer.service.RelayService
import no.battlefront.balancer.service.RelaySessionNotFoundException
import no.battlefront.balancer.service.RelaySessionService
import no.battlefront.balancer.service.WrongSessionPasswordException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Relays and relay sessions for Auric: the public relay and server lists, session management for hosts (host token),
 * joining for players, and admin management of the relay table.
 */
@RestController
@RequestMapping("/api")
class RelayController(
    private val relayService: RelayService,
    private val relaySessionService: RelaySessionService,
    private val joinRateLimiter: RelayJoinRateLimiter,
    private val etagResponses: EtagResponses,
) {
    /** Public. Active relays, with an ETag (304 if If-None-Match matches). */
    @GetMapping("/relays")
    fun relays(): ResponseEntity<RelaysResponse> = etagResponses.ok(relayService.publicList())

    /**
     * Host token. Starts a session and ends the token's previous one.
     *
     * @return 201 with the session id and host ticket, 400 on an invalid field, or 503 without a ticket secret
     */
    @PostMapping("/relay/sessions")
    fun create(
        @RequestBody request: RelaySessionCreateRequest,
        @AuthenticationPrincipal host: HostPrincipal,
    ): ResponseEntity<Any> = handle { ResponseEntity.status(HttpStatus.CREATED).body(relaySessionService.create(request, host.tokenId)) }

    /**
     * Host token that created the session. The heartbeat, at least every 90 seconds.
     *
     * @return 200 `{}`, or 404 if the session is gone (the host then creates a new one)
     */
    @PutMapping("/relay/sessions/{sid}")
    fun heartbeat(
        @PathVariable sid: String,
        @RequestBody(required = false) request: RelaySessionHeartbeatRequest?,
        @AuthenticationPrincipal host: HostPrincipal,
    ): ResponseEntity<Any> =
        handle {
            relaySessionService.heartbeat(sid, request ?: RelaySessionHeartbeatRequest(), host.tokenId)
            ResponseEntity.ok(emptyMap<String, Any>())
        }

    /** Host token that created the session. Ends it: 204, or 404 if it is already gone. */
    @DeleteMapping("/relay/sessions/{sid}")
    fun end(
        @PathVariable sid: String,
        @AuthenticationPrincipal host: HostPrincipal,
    ): ResponseEntity<Any> =
        handle {
            relaySessionService.end(sid, host.tokenId)
            ResponseEntity.noContent().build()
        }

    /** Public. The server list: live sessions, with an ETag (304 if If-None-Match matches). */
    @GetMapping("/relay/sessions")
    fun sessions(): ResponseEntity<Any> = handle { etagResponses.ok<Any>(relaySessionService.list()) }

    /**
     * Public, rate limited per IP address.
     *
     * @return 200 with a client ticket and where to connect, 403 on a wrong or missing password, 404 if the session is
     * gone, or 429 after too many attempts
     */
    @PostMapping("/relay/sessions/{sid}/join")
    fun join(
        @PathVariable sid: String,
        @RequestBody(required = false) request: RelayJoinRequest?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<Any> {
        joinRateLimiter.retryAfterSeconds(httpRequest)?.let {
            return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, it.toString())
                .body(mapOf("message" to "Too many join attempts. Try again in $it seconds."))
        }
        return handle { ResponseEntity.ok(relaySessionService.join(sid, request?.password)) }
    }

    /** All relays, including inactive ones. */
    @GetMapping("/admin/relays")
    fun adminList(): ResponseEntity<List<RelayAdminDto>> = ResponseEntity.ok(relayService.list())

    /**
     * @return 200 with the relay, 400 on an invalid field, or 409 if the id is taken
     */
    @PostMapping("/admin/relays")
    fun adminCreate(
        @RequestBody request: RelayRequest,
    ): ResponseEntity<Any> = handle { ResponseEntity.ok(relayService.create(request)) }

    /** Replaces all fields except the id; also used to deactivate and reactivate (field `active`). */
    @PutMapping("/admin/relays/{id}")
    fun adminUpdate(
        @PathVariable id: String,
        @RequestBody request: RelayRequest,
    ): ResponseEntity<Any> = handle { ResponseEntity.ok(relayService.update(id, request)) }

    /** Deletes the relay and ends the sessions on it. */
    @DeleteMapping("/admin/relays/{id}")
    fun adminDelete(
        @PathVariable id: String,
    ): ResponseEntity<Any> =
        handle {
            relayService.delete(id)
            ResponseEntity.noContent().build()
        }

    private fun handle(action: () -> ResponseEntity<Any>): ResponseEntity<Any> =
        try {
            action()
        } catch (e: RelayNotConfiguredException) {
            message(HttpStatus.SERVICE_UNAVAILABLE, e)
        } catch (e: RelaySessionNotFoundException) {
            message(HttpStatus.NOT_FOUND, e)
        } catch (e: WrongSessionPasswordException) {
            message(HttpStatus.FORBIDDEN, e)
        } catch (e: DuplicateRelayException) {
            message(HttpStatus.CONFLICT, e)
        } catch (e: IllegalArgumentException) {
            message(HttpStatus.BAD_REQUEST, e)
        }

    private fun message(
        status: HttpStatus,
        e: Exception,
    ): ResponseEntity<Any> = ResponseEntity.status(status).body(mapOf("message" to (e.message ?: "Invalid request")))
}
