package no.battlefront.balancer.dto

/**
 * Response for GET /api/randomizer (latest map and rule).
 *
 * @param map map name
 * @param rule rule code
 */
data class RandomizerDto(
    val map: String,
    val rule: String,
)
