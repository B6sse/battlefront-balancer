package no.battlefront.balancer.dto

/**
 * Request body for POST /api/admin/relays and PUT /api/admin/relays/{id}. Fields are nullable so a missing one gets
 * a clear message.
 *
 * @param id short text such as "eu1" (create only; ignored on update)
 * @param port null means 25200
 * @param active null means true on create and unchanged on update
 */
data class RelayRequest(
    val id: String? = null,
    val region: String? = null,
    val name: String? = null,
    val host: String? = null,
    val port: Int? = null,
    val active: Boolean? = null,
)

/** Relay as listed for admins (GET /api/admin/relays), including inactive ones. */
data class RelayAdminDto(
    val id: String,
    val region: String,
    val name: String,
    val host: String,
    val port: Int,
    val active: Boolean,
    val liveSessions: Int,
)

/** Public view of an active relay. */
data class RelayDto(
    val id: String,
    val region: String,
    val name: String,
    val host: String,
    val port: Int,
)

/** Response for GET /api/relays. */
data class RelaysResponse(
    val relays: List<RelayDto>,
)

/**
 * Request body for POST /api/relay/sessions (host token).
 *
 * @param password optional; null or empty means anyone may join
 */
data class RelaySessionCreateRequest(
    val relayId: String? = null,
    val name: String? = null,
    val mode: String? = null,
    val level: String? = null,
    val maxPlayers: Int? = null,
    val password: String? = null,
    val auricVersion: String? = null,
)

/**
 * Response for POST /api/relay/sessions.
 *
 * @param hostTicket what the host shows the relay
 * @param heartbeatSeconds how often to send PUT /api/relay/sessions/{sid}
 */
data class RelaySessionCreatedDto(
    val sid: String,
    val hostTicket: String,
    val heartbeatSeconds: Int,
)

/** Request body for PUT /api/relay/sessions/{sid}, the heartbeat. A missing field keeps its current value. */
data class RelaySessionHeartbeatRequest(
    val players: Int? = null,
    val mode: String? = null,
    val level: String? = null,
)

/** A live session in the public server list. Never contains anything about the host. */
data class RelaySessionDto(
    val sid: String,
    val name: String,
    val relayId: String,
    val region: String,
    val mode: String,
    val level: String,
    val players: Int,
    val maxPlayers: Int,
    val hasPassword: Boolean,
    val auricVersion: String?,
)

/** Response for GET /api/relay/sessions. */
data class RelaySessionsResponse(
    val sessions: List<RelaySessionDto>,
)

/** Request body for POST /api/relay/sessions/{sid}/join. */
data class RelayJoinRequest(
    val password: String? = null,
)

/** Where to connect, in the response to a join. */
data class RelayEndpointDto(
    val id: String,
    val host: String,
    val port: Int,
)

/** Response for POST /api/relay/sessions/{sid}/join. */
data class RelayJoinDto(
    val clientTicket: String,
    val relay: RelayEndpointDto,
)
