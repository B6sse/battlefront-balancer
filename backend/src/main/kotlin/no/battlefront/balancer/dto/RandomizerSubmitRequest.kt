package no.battlefront.balancer.dto

/**
 * Request body for POST /api/randomizer.
 *
 * @param map map name
 * @param rule rule code
 */
data class RandomizerSubmitRequest(
    val map: String,
    val rule: String,
)
