package no.battlefront.balancer.service

import no.battlefront.balancer.dto.RelayEndpointDto
import no.battlefront.balancer.dto.RelayJoinDto
import no.battlefront.balancer.dto.RelaySessionCreateRequest
import no.battlefront.balancer.dto.RelaySessionCreatedDto
import no.battlefront.balancer.dto.RelaySessionDto
import no.battlefront.balancer.dto.RelaySessionHeartbeatRequest
import no.battlefront.balancer.dto.RelaySessionsResponse
import no.battlefront.balancer.model.Relay
import no.battlefront.balancer.model.RelaySession
import no.battlefront.balancer.repository.RelayRepository
import no.battlefront.balancer.repository.RelaySessionRepository
import no.battlefront.balancer.security.RelayTicketSigner
import no.battlefront.balancer.security.RelayTickets
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.HexFormat

/** Thrown when the ticket secret is missing or too short; the session endpoints then answer 503. */
class RelayNotConfiguredException(
    message: String,
) : RuntimeException(message)

/** Thrown when a session does not exist, has expired, or (for its host's calls) belongs to another host token. */
class RelaySessionNotFoundException : RuntimeException("Session not found")

/** Thrown when a join has the wrong password, or none for a session that needs one. */
class WrongSessionPasswordException : RuntimeException("Wrong password")

/**
 * Relay sessions: the list of running game servers, and the tickets that let hosts and players use a relay.
 *
 * Sessions are rows in the database, so a restart of the backend (a deploy) does not end running games. A session is
 * live while its last heartbeat is at most [EXPIRY_SECONDS] old; expired rows are removed when a session is created.
 * The public list is fetched by every player's game, so it is kept in memory for up to [LIST_CACHE_MS] and dropped
 * whenever a session or relay changes.
 */
@Service
class RelaySessionService(
    private val relaySessionRepository: RelaySessionRepository,
    private val relayRepository: RelayRepository,
    private val relayTickets: RelayTickets,
    private val passwordEncoder: PasswordEncoder,
    transactionManager: PlatformTransactionManager,
) {
    /** Replaced in tests to move time forward. */
    internal var clock: Clock = Clock.systemUTC()

    private val transaction = TransactionTemplate(transactionManager)
    private val secureRandom = SecureRandom()

    /** [untilMs] is the first moment the list may be wrong: the cache's age limit or the earliest expiry in it. */
    private class CachedList(
        val fromMs: Long,
        val untilMs: Long,
        val response: RelaySessionsResponse,
    )

    @Volatile private var cachedList: CachedList? = null

    /**
     * Creates a session for the host token, ending that token's previous one.
     *
     * @throws RelayNotConfiguredException if tickets cannot be signed
     * @throws IllegalArgumentException on an invalid field or a relay that does not exist or is inactive
     */
    fun create(
        request: RelaySessionCreateRequest,
        hostTokenId: Long,
    ): RelaySessionCreatedDto {
        val signer = signer()
        val relayId = request.relayId?.trim().orEmpty()
        require(relayId.isNotEmpty()) { "relayId is required" }
        val relay = relayRepository.findById(relayId).orElse(null)
        require(relay != null && relay.active) { "Unknown relay \"$relayId\"" }
        val name = cleanText(request.name)
        require(name.isNotEmpty()) { "name is required" }
        require(name.length <= MAX_NAME_LENGTH) { "name max $MAX_NAME_LENGTH characters" }
        val mode = requireText(request.mode, "mode", MAX_MODE_LENGTH)
        val level = requireText(request.level, "level", MAX_LEVEL_LENGTH)
        val maxPlayers = request.maxPlayers
        require(maxPlayers != null) { "maxPlayers is required" }
        require(maxPlayers in MIN_PLAYERS..MAX_PLAYERS) { "maxPlayers must be $MIN_PLAYERS to $MAX_PLAYERS" }
        val password = request.password.orEmpty()
        require(password.length <= MAX_PASSWORD_LENGTH) { "password max $MAX_PASSWORD_LENGTH characters" }
        val auricVersion = cleanText(request.auricVersion).ifEmpty { null }
        require(auricVersion == null || auricVersion.length <= MAX_VERSION_LENGTH) { "auricVersion max $MAX_VERSION_LENGTH characters" }

        val now = clock.millis()
        val session =
            RelaySession(
                sid = randomHex(SID_BYTES),
                hostTokenId = hostTokenId,
                relayId = relay.id,
                name = name,
                mode = mode,
                level = level,
                maxPlayers = maxPlayers,
                passwordHash = if (password.isEmpty()) null else passwordEncoder.encode(prehash(password)),
                auricVersion = auricVersion,
                lastHeartbeatMs = now,
            )
        transaction.executeWithoutResult {
            relaySessionRepository.deleteByHostToken(hostTokenId)
            relaySessionRepository.deleteExpired(now - EXPIRY_MS)
            relaySessionRepository.save(session)
        }
        invalidateList()
        return RelaySessionCreatedDto(
            sid = session.sid,
            hostTicket = signer.hostTicket(session.sid, relay.id, maxPlayers, expiresAt(now)),
            heartbeatSeconds = HEARTBEAT_SECONDS,
        )
    }

    /**
     * The heartbeat: keeps the session live and updates what the list shows. A null field keeps its value.
     *
     * @throws RelaySessionNotFoundException if the session is gone or was created with another host token
     * @throws IllegalArgumentException on an invalid field
     */
    fun heartbeat(
        sid: String,
        request: RelaySessionHeartbeatRequest,
        hostTokenId: Long,
    ) {
        signer()
        val now = clock.millis()
        transaction.executeWithoutResult {
            val session = relaySessionRepository.findById(sid).orElse(null)
            if (session == null || session.hostTokenId != hostTokenId || !isLive(session, now)) throw RelaySessionNotFoundException()
            request.players?.let {
                require(it in 0..session.maxPlayers) { "players must be 0 to ${session.maxPlayers}" }
                session.players = it
            }
            request.mode?.let { session.mode = requireText(it, "mode", MAX_MODE_LENGTH) }
            request.level?.let { session.level = requireText(it, "level", MAX_LEVEL_LENGTH) }
            session.lastHeartbeatMs = now
            relaySessionRepository.save(session)
        }
        invalidateList()
    }

    /**
     * Ends a session.
     *
     * @throws RelaySessionNotFoundException if the session is gone or was created with another host token
     */
    fun end(
        sid: String,
        hostTokenId: Long,
    ) {
        signer()
        val deleted = transaction.execute { relaySessionRepository.deleteOwned(sid, hostTokenId) } ?: 0
        if (deleted == 0) throw RelaySessionNotFoundException()
        invalidateList()
    }

    /** Live sessions on active relays, oldest first. */
    fun list(): RelaySessionsResponse {
        signer()
        val now = clock.millis()
        cachedList?.let { if (now >= it.fromMs && now < it.untilMs) return it.response }
        val relays = relayRepository.findAll().filter { it.active }.associateBy { it.id }
        val live = relaySessionRepository.findAllByLastHeartbeatMsGreaterThanEqualOrderByCreatedAtAscSidAsc(now - EXPIRY_MS)
        val response =
            RelaySessionsResponse(
                live
                    .mapNotNull { s ->
                        val relay = relays[s.relayId] ?: return@mapNotNull null
                        RelaySessionDto(
                            sid = s.sid,
                            name = s.name,
                            relayId = relay.id,
                            region = relay.region,
                            mode = s.mode,
                            level = s.level,
                            players = s.players,
                            maxPlayers = s.maxPlayers,
                            hasPassword = s.passwordHash != null,
                            auricVersion = s.auricVersion,
                        )
                    },
            )
        val firstExpiry = live.minOfOrNull { it.lastHeartbeatMs + EXPIRY_MS + 1 } ?: Long.MAX_VALUE
        cachedList = CachedList(now, minOf(now + LIST_CACHE_MS, firstExpiry), response)
        return response
    }

    /**
     * Gives a player a ticket for the session's relay.
     *
     * @throws RelaySessionNotFoundException if the session is gone or its relay is no longer active
     * @throws WrongSessionPasswordException if the session has a password and [password] is not it
     */
    fun join(
        sid: String,
        password: String?,
    ): RelayJoinDto {
        val signer = signer()
        val now = clock.millis()
        val session = relaySessionRepository.findById(sid).orElse(null)
        if (session == null || !isLive(session, now)) throw RelaySessionNotFoundException()
        val relay: Relay =
            relayRepository.findById(session.relayId).orElse(null)?.takeIf { it.active } ?: throw RelaySessionNotFoundException()
        val hash = session.passwordHash
        if (hash != null &&
            (password.isNullOrEmpty() || password.length > MAX_PASSWORD_LENGTH || !passwordEncoder.matches(prehash(password), hash))
        ) {
            throw WrongSessionPasswordException()
        }
        return RelayJoinDto(
            clientTicket = signer.clientTicket(session.sid, relay.id, randomHex(CID_BYTES), expiresAt(now)),
            relay = RelayEndpointDto(id = relay.id, host = relay.host, port = relay.port),
        )
    }

    /** Number of live sessions per relay id, for the admin page. */
    fun liveCountsByRelay(): Map<String, Int> =
        relaySessionRepository
            .findAllByLastHeartbeatMsGreaterThanEqualOrderByCreatedAtAscSidAsc(clock.millis() - EXPIRY_MS)
            .groupingBy { it.relayId }
            .eachCount()

    /** Drops the cached list; called after every change to a session or relay. */
    fun invalidateList() {
        cachedList = null
    }

    private fun signer(): RelayTicketSigner =
        relayTickets.signer ?: throw RelayNotConfiguredException(relayTickets.problem ?: "Relay sessions are not configured")

    private fun isLive(
        session: RelaySession,
        nowMs: Long,
    ): Boolean = session.lastHeartbeatMs >= nowMs - EXPIRY_MS

    private fun expiresAt(nowMs: Long): Long = nowMs / 1000 + TICKET_SECONDS

    private fun randomHex(bytes: Int): String = HexFormat.of().formatHex(ByteArray(bytes).also(secureRandom::nextBytes))

    /** bcrypt only reads 72 bytes, and 40 characters can be more; hash first so every character counts. */
    private fun prehash(password: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8)))

    private fun requireText(
        value: String?,
        field: String,
        max: Int,
    ): String {
        val v = cleanText(value)
        require(v.isNotEmpty()) { "$field is required" }
        require(v.length <= max) { "$field max $max characters" }
        return v
    }

    companion object {
        const val HEARTBEAT_SECONDS = 30
        const val EXPIRY_SECONDS = 90L
        const val TICKET_SECONDS = 12 * 60 * 60L
        private const val EXPIRY_MS = EXPIRY_SECONDS * 1000
        private const val LIST_CACHE_MS = 5_000L
        private const val SID_BYTES = 16
        private const val CID_BYTES = 8
        private const val MAX_NAME_LENGTH = 40
        private const val MAX_MODE_LENGTH = 40
        private const val MAX_LEVEL_LENGTH = 200
        private const val MAX_PASSWORD_LENGTH = 40
        private const val MAX_VERSION_LENGTH = 20
        private const val MIN_PLAYERS = 2
        private const val MAX_PLAYERS = 40

        /** Removes control and invisible formatting characters (new lines, zero-width, text direction marks), then trims. */
        internal fun cleanText(value: String?): String =
            value
                .orEmpty()
                .filterNot {
                    val type = Character.getType(it).toByte()
                    type == Character.CONTROL ||
                        type == Character.FORMAT ||
                        type == Character.LINE_SEPARATOR ||
                        type == Character.PARAGRAPH_SEPARATOR
                }.trim()
    }
}
