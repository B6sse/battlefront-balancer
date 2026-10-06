package no.battlefront.balancer.service

import no.battlefront.balancer.dto.PlayerCreateRequest
import no.battlefront.balancer.dto.PlayerRequestApproveRequest
import no.battlefront.balancer.dto.PlayerRequestCreateRequest
import no.battlefront.balancer.dto.PlayerRequestCreatedDto
import no.battlefront.balancer.dto.PlayerRequestDto
import no.battlefront.balancer.model.Player
import no.battlefront.balancer.model.PlayerRequest
import no.battlefront.balancer.repository.HostTokenRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.PlayerRequestRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.CurrentUserService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Thrown when a player request is sent for a persona ID that already belongs to a player.
 */
class PersonaAlreadyRegisteredException(
    val playerId: Long,
    nickname: String,
) : RuntimeException("Persona ID already belongs to $nickname")

/**
 * Handles requests from Auric hosts to register players, and their approval by admins and editors.
 */
@Service
class PlayerRequestService(
    private val playerRequestRepository: PlayerRequestRepository,
    private val playerRepository: PlayerRepository,
    private val hostTokenRepository: HostTokenRepository,
    private val userRepository: UserRepository,
    private val playerService: PlayerService,
    private val currentUserService: CurrentUserService,
) {
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Stores a pending request, replacing a pending request for the same persona ID.
     *
     * @param tokenId host token that sent the request
     * @throws IllegalArgumentException on invalid fields.
     * @throws PersonaAlreadyRegisteredException if the persona ID already belongs to a player.
     */
    @Transactional
    fun submit(
        request: PlayerRequestCreateRequest,
        tokenId: Long?,
    ): PlayerRequestCreatedDto {
        val nickname = request.nickname.trim()
        val nation = request.nation.trim().lowercase()
        require(request.personaId > 0) { "Persona ID must be a positive number" }
        require(NICKNAME_RE.matches(nickname)) { "Nickname must be 1–16 characters without < > \" ' `" }
        require(NATION_RE.matches(nation)) { "Nation must be a 2-letter code" }
        require(request.rating in 1..99) { "Rating must be between 1 and 99" }
        playerRepository.findByPersonaId(request.personaId)?.let { throw PersonaAlreadyRegisteredException(it.id, it.nickname) }

        val existing = playerRequestRepository.findByPersonaIdAndStatus(request.personaId, PENDING)
        val pending = existing ?: PlayerRequest(personaId = request.personaId, status = PENDING)
        pending.nickname = nickname
        pending.nation = nation
        pending.rating = request.rating
        pending.inGameName =
            request.inGameName
                ?.trim()
                ?.take(100)
                ?.ifEmpty { null }
        pending.tokenId = tokenId
        pending.requestedAt = LocalDateTime.now()
        val saved = playerRequestRepository.save(pending)
        return PlayerRequestCreatedDto(id = saved.id, status = PENDING, replaced = existing != null)
    }

    /**
     * Returns pending requests, newest first.
     */
    fun listPending(): List<PlayerRequestDto> {
        val requests = playerRequestRepository.findByStatusOrderByRequestedAtDesc(PENDING)
        val tokens = hostTokenRepository.findAllById(requests.mapNotNull { it.tokenId }.distinct()).associateBy { it.id }
        val owners = userRepository.findAllById(tokens.values.map { it.userId }.distinct()).associateBy { it.id }
        return requests.map { r ->
            val token = r.tokenId?.let { tokens[it] }
            PlayerRequestDto(
                id = r.id,
                personaId = r.personaId,
                nickname = r.nickname,
                nation = r.nation,
                rating = r.rating,
                inGameName = r.inGameName,
                status = r.status,
                requestedBy = token?.let { t -> owners[t.userId]?.let { "${t.name} (${it.username})" } ?: t.name },
                requestedAt = r.requestedAt.format(isoFormatter),
            )
        }
    }

    /**
     * Creates the player (with initial season stats) from a pending request. [overrides] can correct the
     * nickname, nation or rating first.
     *
     * @throws IllegalArgumentException if the request is not pending or the player data is invalid.
     */
    @Transactional
    fun approve(
        id: Long,
        overrides: PlayerRequestApproveRequest,
    ): Player {
        val request = findPending(id)
        val player =
            playerService.createPlayer(
                PlayerCreateRequest(
                    nickname = overrides.nickname ?: request.nickname,
                    nation = overrides.nation ?: request.nation,
                    rating = overrides.rating ?: request.rating,
                    personaId = request.personaId,
                ),
            )
        player.lastSeenName = request.inGameName
        playerRepository.save(player)
        resolve(request, APPROVED, player.id)
        return player
    }

    /**
     * Gives an existing player the request's persona ID, for players who are registered but had no persona ID yet.
     *
     * @throws IllegalArgumentException if the request is not pending, the player does not exist, or either the
     *   player or the persona ID is already linked elsewhere.
     */
    @Transactional
    fun link(
        id: Long,
        playerId: Long,
    ): Player {
        val request = findPending(id)
        val player = playerRepository.findById(playerId).orElse(null) ?: throw IllegalArgumentException("Player not found")
        require(player.personaId == null || player.personaId == request.personaId) {
            "${player.nickname} already has persona ID ${player.personaId}"
        }
        playerRepository.findByPersonaId(request.personaId)?.let { owner ->
            require(owner.id == player.id) { "Persona ID already belongs to ${owner.nickname}" }
        }
        player.personaId = request.personaId
        request.inGameName?.let { player.lastSeenName = it }
        playerRepository.save(player)
        resolve(request, APPROVED, player.id)
        return player
    }

    /**
     * @throws IllegalArgumentException if the request is not pending.
     */
    @Transactional
    fun reject(id: Long) {
        resolve(findPending(id), REJECTED, playerId = null)
    }

    private fun findPending(id: Long): PlayerRequest {
        val request = playerRequestRepository.findById(id).orElse(null) ?: throw IllegalArgumentException("Request not found")
        require(request.status == PENDING) { "Request is already ${request.status}" }
        return request
    }

    private fun resolve(
        request: PlayerRequest,
        status: String,
        playerId: Long?,
    ) {
        request.status = status
        request.resolvedAt = LocalDateTime.now()
        request.resolvedBy = currentUserService.currentUserId()
        request.playerId = playerId
        playerRequestRepository.save(request)
    }

    private companion object {
        const val PENDING = "pending"
        const val APPROVED = "approved"
        const val REJECTED = "rejected"

        /** Same rule as the nickname field on the admin page. */
        val NICKNAME_RE = Regex("^[^\\x00-\\x1f<>\"'`]{1,16}$")
        val NATION_RE = Regex("^[a-z]{2}$")
    }
}
