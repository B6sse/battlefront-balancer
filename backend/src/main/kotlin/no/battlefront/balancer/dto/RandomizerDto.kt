package no.battlefront.balancer.dto

/**
 * Respons for GET /api/randomizer (tilsvarer api_randomizer.php).
 */
data class RandomizerDto(
    val map: String,
    val rule: String
)
