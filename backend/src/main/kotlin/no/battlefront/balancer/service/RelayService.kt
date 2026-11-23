package no.battlefront.balancer.service

import no.battlefront.balancer.dto.RelayAdminDto
import no.battlefront.balancer.dto.RelayDto
import no.battlefront.balancer.dto.RelayRequest
import no.battlefront.balancer.dto.RelaysResponse
import no.battlefront.balancer.model.Relay
import no.battlefront.balancer.repository.RelayRepository
import no.battlefront.balancer.repository.RelaySessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** Thrown when a relay with the same id already exists. */
class DuplicateRelayException(
    id: String,
) : RuntimeException("A relay with id \"$id\" already exists")

/**
 * The relay servers: admin create, edit, (de)activate and delete, and the public list of active relays.
 */
@Service
class RelayService(
    private val relayRepository: RelayRepository,
    private val relaySessionRepository: RelaySessionRepository,
    private val relaySessionService: RelaySessionService,
) {
    fun list(): List<RelayAdminDto> {
        val live = relaySessionService.liveCountsByRelay()
        return relayRepository.findAllByOrderByRegionAscIdAsc().map {
            RelayAdminDto(it.id, it.region, it.name, it.host, it.port, it.active, live[it.id] ?: 0)
        }
    }

    /** Active relays only. */
    fun publicList(): RelaysResponse =
        RelaysResponse(
            relayRepository.findAllByOrderByRegionAscIdAsc().filter { it.active }.map {
                RelayDto(
                    it.id,
                    it.region,
                    it.name,
                    it.host,
                    it.port,
                )
            },
        )

    /**
     * @throws IllegalArgumentException on an invalid field
     * @throws DuplicateRelayException if the id is taken
     */
    @Transactional
    fun create(request: RelayRequest): RelayAdminDto {
        val id = request.id?.trim().orEmpty()
        require(id.isNotEmpty()) { "id is required" }
        require(ID_RE.matches(id)) { "id must be 1 to 20 characters: a-z, 0-9 and -, starting with a letter or digit (e.g. eu1)" }
        if (relayRepository.existsById(id)) throw DuplicateRelayException(id)
        val relay = Relay(id = id)
        apply(relay, request)
        relay.active = request.active ?: true
        return toDto(relayRepository.save(relay))
    }

    /**
     * Replaces all fields except the id. [RelayRequest.active] null keeps the current state.
     *
     * @throws IllegalArgumentException if the relay does not exist or a field is invalid
     */
    @Transactional
    fun update(
        id: String,
        request: RelayRequest,
    ): RelayAdminDto {
        val relay = relayRepository.findById(id).orElse(null) ?: throw IllegalArgumentException("Relay not found")
        apply(relay, request)
        request.active?.let { relay.active = it }
        relay.updatedAt = LocalDateTime.now()
        val saved = relayRepository.save(relay)
        relaySessionService.invalidateList()
        return toDto(saved)
    }

    /**
     * Deletes a relay and ends the sessions on it.
     *
     * @throws IllegalArgumentException if the relay does not exist
     */
    @Transactional
    fun delete(id: String) {
        val relay = relayRepository.findById(id).orElse(null) ?: throw IllegalArgumentException("Relay not found")
        relaySessionRepository.deleteByRelay(relay.id)
        relayRepository.delete(relay)
        relaySessionService.invalidateList()
    }

    private fun apply(
        relay: Relay,
        r: RelayRequest,
    ) {
        val region = FieldValidation.requireText(r.region, "region", 10).uppercase()
        require(REGION_RE.matches(region)) { "region must be 2 to 10 letters or digits, e.g. EU" }
        val host = FieldValidation.requireText(r.host, "host", 253)
        require(HOSTNAME_RE.matches(host) || IPV6_RE.matches(host)) {
            "host must be a DNS name or IP address, without a scheme or port (e.g. relay-eu.example.com)"
        }
        val port = r.port ?: Relay.DEFAULT_PORT
        require(port in 1..65535) { "port must be 1 to 65535" }
        relay.region = region
        relay.name = FieldValidation.requireText(r.name, "name", 60)
        relay.host = host
        relay.port = port
    }

    private fun toDto(r: Relay) = RelayAdminDto(r.id, r.region, r.name, r.host, r.port, r.active, 0)

    private companion object {
        val ID_RE = Regex("^[a-z0-9][a-z0-9-]{0,19}$")
        val REGION_RE = Regex("^[A-Z0-9]{2,10}$")
        val HOSTNAME_RE = Regex("^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*$")
        val IPV6_RE = Regex("^(?=.*:)[0-9A-Fa-f:.]{2,45}$")
    }
}
