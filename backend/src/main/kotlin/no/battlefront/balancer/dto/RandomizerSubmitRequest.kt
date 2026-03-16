package no.battlefront.balancer.dto

/**
 * Request body for POST /api/randomizer (tilsvarer randomizerSubmit.php).
 */
data class RandomizerSubmitRequest(
    val map: String,
    val rule: String
)
