package no.battlefront.balancer.dto

data class MatchSummaryDto(
    val id: Long,
    val date: String,
    val map: String,
    val rule: String,
    val teamSize: Int,
    val mvpName: String?,
    val supervisorName: String?,
)

data class MatchPlayerStatDto(
    val nickname: String,
    val nation: String,
    val score: Int,
    val updateBr: Int,
    val newBr: Int,
    val perf: Double,
)

data class MatchDetailDto(
    val id: Long,
    val rebelScore: Int,
    val imperialScore: Int,
    val teamSize: Int,
    val rebels: List<MatchPlayerStatDto>,
    val imperials: List<MatchPlayerStatDto>,
)
