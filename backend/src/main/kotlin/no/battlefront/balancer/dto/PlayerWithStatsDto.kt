package no.battlefront.balancer.dto

/**
 * Tilsvarer responsen fra gammel api_players.php: spiller + sesongstatistikk for nåværende sesong.
 */
data class PlayerWithStatsDto(
    val id: Long,
    val nickname: String,
    val nation: String,
    val rating: Int,
    val dzrating: Int,
    val elo: Int,
    val br: Int,
    val played: Int,
    val best: Int,
    val won: Int,
    val lost: Int,
    val draw: Int,
    val score: Int,
    val mvp: Int
)
