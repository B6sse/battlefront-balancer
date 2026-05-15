package no.battlefront.balancer.service

import no.battlefront.balancer.dto.PlayerCreateRequest
import no.battlefront.balancer.dto.PlayerMatchHistoryDto
import no.battlefront.balancer.dto.PlayerUpdateRequest
import no.battlefront.balancer.dto.PlayerWithStatsDto
import no.battlefront.balancer.model.Player
import no.battlefront.balancer.model.RankedPlayerStat
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedMatchRepository
import no.battlefront.balancer.repository.RankedMatchStatRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

@Service
class PlayerService(
    private val playerRepository: PlayerRepository,
    private val rankedPlayerStatRepository: RankedPlayerStatRepository,
    private val currentSeasonRepository: CurrentSeasonRepository,
    private val rankedMatchRepository: RankedMatchRepository,
    private val rankedMatchStatRepository: RankedMatchStatRepository,
) {
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Returns players with stats for the given season parameter.
     * - null/blank → current season
     * - "all" → aggregate across all seasons
     * - numeric string → that season
     */
    fun getPlayersWithSeasonStats(seasonParam: String?): List<PlayerWithStatsDto> =
        when {
            seasonParam == "all" -> getPlayersAllSeasons()
            seasonParam != null && seasonParam.isNotBlank() -> {
                val season = seasonParam.toIntOrNull() ?: return emptyList()
                getPlayersForSeason(season)
            }
            else -> getPlayersWithCurrentSeasonStats()
        }

    /**
     * Returns all players with their season statistics for the current season.
     * Falls back to all players with zero stats when no stats exist for the current season.
     */
    fun getPlayersWithCurrentSeasonStats(): List<PlayerWithStatsDto> {
        val season = currentSeasonRepository.findCurrentSeason() ?: 1
        val stats = rankedPlayerStatRepository.findBySeason(season)
        if (stats.isNotEmpty()) {
            val playerIds = stats.map { it.playerId }
            val playerMap = playerRepository.findAllById(playerIds).associateBy { it.id }
            return stats
                .mapNotNull { stat ->
                    val player = playerMap[stat.playerId] ?: return@mapNotNull null
                    stat.toDto(player)
                }.sortedWith(
                    compareByDescending<PlayerWithStatsDto> { it.played >= 5 }
                        .thenByDescending { it.br }
                        .thenBy { it.nickname.lowercase() },
                )
        }
        return playerRepository.findAll().map { player ->
            PlayerWithStatsDto(
                id = player.id,
                nickname = player.nickname,
                nation = player.nation,
                rating = player.rating,
                dzrating = player.dzrating,
                br = 0,
                played = 0,
                best = 0,
                won = 0,
                lost = 0,
                draw = 0,
                score = 0,
                mvp = 0,
            )
        }
    }

    /**
     * Returns the match history for a player, newest first.
     * - null/blank → current season
     * - "all" → all seasons
     * - numeric string → that season
     */
    fun getPlayerMatchHistory(
        playerId: Long,
        seasonParam: String?,
    ): List<PlayerMatchHistoryDto> {
        val stats =
            when {
                seasonParam == "all" -> rankedMatchStatRepository.findByPlayerIdOrderByMatchIdDesc(playerId)
                seasonParam != null && seasonParam.isNotBlank() -> {
                    val season = seasonParam.toIntOrNull() ?: return emptyList()
                    rankedMatchStatRepository.findByPlayerIdAndSeasonOrderByMatchIdDesc(playerId, season)
                }
                else -> {
                    val season = currentSeasonRepository.findCurrentSeason() ?: 1
                    rankedMatchStatRepository.findByPlayerIdAndSeasonOrderByMatchIdDesc(playerId, season)
                }
            }
        if (stats.isEmpty()) return emptyList()
        val matchMap = rankedMatchRepository.findAllById(stats.map { it.matchId }).associateBy { it.id }
        return stats.mapNotNull { stat ->
            val match = matchMap[stat.matchId] ?: return@mapNotNull null
            PlayerMatchHistoryDto(
                matchId = match.id,
                date = match.date.format(isoFormatter),
                map = match.map,
                rule = match.rule,
                result = stat.result,
                score = stat.score,
                updateBr = stat.updateBr,
                newBr = stat.newBr,
            )
        }
    }

    /**
     * Creates a new player and initial [RankedPlayerStat] row for the current season.
     */
    @Transactional
    fun createPlayer(request: PlayerCreateRequest): Player {
        val nickname = request.nickname.trim()
        val nation = request.nation.trim().lowercase()
        val rating = request.rating

        require(nickname.isNotEmpty()) { "Name is required" }
        require(rating in 1..99) { "Rating must be between 1 and 99" }
        require(nation.length == 2) { "Nation must be a 2-letter code" }

        val dzrating = rating
        val br =
            when {
                rating <= 60 -> 750
                rating <= 65 -> 800
                rating <= 71 -> 850
                rating <= 78 -> 900
                rating <= 86 -> 950
                rating <= 89 -> 1000
                else -> 1050
            }
        val best = br
        val season = currentSeasonRepository.findCurrentSeason() ?: 1

        val player = Player(nickname = nickname, nation = nation, rating = rating, dzrating = dzrating)
        val savedPlayer = playerRepository.save(player)

        val stats =
            RankedPlayerStat(
                playerId = savedPlayer.id,
                season = season,
                br = br,
                best = best,
                played = 0,
                won = 0,
                lost = 0,
                draw = 0,
                score = 0,
                mvp = 0,
            )
        rankedPlayerStatRepository.save(stats)

        return savedPlayer
    }

    /**
     * Updates an existing player's profile and their BR for the current season.
     */
    @Transactional
    fun updatePlayer(
        id: Long,
        request: PlayerUpdateRequest,
    ): Player {
        val player = playerRepository.findById(id).orElseThrow { IllegalArgumentException("Player not found") }

        val nickname = request.nickname.trim()
        val nation = request.nation.trim().lowercase()
        val rating = request.rating
        val dzrating = request.dzrating
        val br = request.br

        require(nickname.isNotEmpty()) { "Name is required" }
        require(rating in 1..99) { "Rating must be between 1 and 99" }
        require(dzrating in 1..99) { "DZ rating must be between 1 and 99" }
        require(br in 1..9999) { "BR must be between 1 and 9999" }
        require(nation.length == 2) { "Nation must be a 2-letter code" }

        player.nickname = nickname
        player.nation = nation
        player.rating = rating
        player.dzrating = dzrating
        val savedPlayer = playerRepository.save(player)

        val season = currentSeasonRepository.findCurrentSeason() ?: 1
        val stats =
            rankedPlayerStatRepository.findByPlayerIdAndSeason(savedPlayer.id, season)
                ?: throw IllegalStateException("Season stats not found for player")

        stats.br = br
        rankedPlayerStatRepository.save(stats)

        return savedPlayer
    }

    /**
     * Deletes a player by id. Related [RankedPlayerStat] rows are removed by DB cascade.
     */
    @Transactional
    fun deletePlayer(id: Long) {
        if (!playerRepository.existsById(id)) return
        playerRepository.deleteById(id)
    }

    private fun getPlayersForSeason(season: Int): List<PlayerWithStatsDto> {
        val stats = rankedPlayerStatRepository.findBySeason(season)
        if (stats.isEmpty()) return emptyList()
        val playerMap = playerRepository.findAllById(stats.map { it.playerId }).associateBy { it.id }
        return stats
            .mapNotNull { stat ->
                val player = playerMap[stat.playerId] ?: return@mapNotNull null
                stat.toDto(player)
            }.sortedWith(
                compareByDescending<PlayerWithStatsDto> { it.played >= 5 }
                    .thenByDescending { it.br }
                    .thenBy { it.nickname.lowercase() },
            )
    }

    private fun getPlayersAllSeasons(): List<PlayerWithStatsDto> {
        val allStats = rankedPlayerStatRepository.findAll()
        if (allStats.isEmpty()) return emptyList()
        val playerMap = playerRepository.findAll().associateBy { it.id }
        return allStats
            .groupBy { it.playerId }
            .mapNotNull { (playerId, stats) ->
                val player = playerMap[playerId] ?: return@mapNotNull null
                val latestSeason = stats.maxOf { it.season }
                val latestBr = stats.first { it.season == latestSeason }.br
                PlayerWithStatsDto(
                    id = player.id,
                    nickname = player.nickname,
                    nation = player.nation,
                    rating = player.rating,
                    dzrating = player.dzrating,
                    br = latestBr,
                    played = stats.sumOf { it.played },
                    best = stats.maxOf { it.best },
                    won = stats.sumOf { it.won },
                    lost = stats.sumOf { it.lost },
                    draw = stats.sumOf { it.draw },
                    score = stats.sumOf { it.score },
                    mvp = stats.sumOf { it.mvp },
                )
            }.sortedWith(
                compareByDescending<PlayerWithStatsDto> { it.played >= 5 }
                    .thenByDescending { it.br }
                    .thenBy { it.nickname.lowercase() },
            )
    }

    private fun RankedPlayerStat.toDto(player: Player) =
        PlayerWithStatsDto(
            id = player.id,
            nickname = player.nickname,
            nation = player.nation,
            rating = player.rating,
            dzrating = player.dzrating,
            br = br,
            played = played,
            best = best,
            won = won,
            lost = lost,
            draw = draw,
            score = score,
            mvp = mvp,
        )
}
