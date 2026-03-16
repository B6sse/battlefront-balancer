package no.battlefront.balancer.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Per-player stat sent when submitting a match (rebels/imperials arrays).
 *
 * @param id player primary key
 * @param faction "Rebel" or "Imperial"
 * @param outcome "Won", "Lost", or "Draw"
 * @param score player score in the match
 * @param perf performance/carry value
 * @param change BR change (+/-)
 * @param newBR BR after the match (JSON field name: "NewBR")
 */
data class PlayerMatchStatDto(
    val id: Long,
    val faction: String,
    val outcome: String,
    val score: Int,
    val perf: Double,
    val change: Int,
    @param:JsonProperty("NewBR") val newBR: Int
)
