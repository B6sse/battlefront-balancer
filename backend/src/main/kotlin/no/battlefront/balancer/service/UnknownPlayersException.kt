package no.battlefront.balancer.service

/**
 * Thrown when a request refers to persona IDs that belong to no registered player.
 */
class UnknownPlayersException(
    val unknown: List<Long>,
) : RuntimeException("Unknown persona IDs: ${unknown.joinToString()}")
