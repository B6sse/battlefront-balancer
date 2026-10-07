package no.battlefront.balancer.dto

data class PlayerMatchHistoryDto(
    val matchId: Long,
    val date: String,
    val map: String,
    val rule: String,
    val result: String,
    val score: Int,
    val updateBr: Int,
    val newBr: Int,
)
