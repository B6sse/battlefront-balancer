package no.battlefront.balancer.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Per-spiller stat som sendes inn ved match submit (rebels/imperials).
 * Tilsvarer strukturen fra script.js som sendes til matchSubmit.php.
 */
data class PlayerMatchStatDto(
    val id: Long,
    val faction: String,
    val outcome: String,  // "Won", "Lost", "Draw"
    val score: Int,
    val perf: Double,
    val change: Int,
    @param:JsonProperty("NewBR") val newBR: Int
)
