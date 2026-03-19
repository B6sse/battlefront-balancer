package no.battlefront.balancer.service

import no.battlefront.balancer.dto.PlayerCreateRequest
import no.battlefront.balancer.dto.PlayerUpdateRequest
import no.battlefront.balancer.dto.PlayerWithStatsDto
import no.battlefront.balancer.model.Player
import no.battlefront.balancer.model.RankedPlayerStat
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PlayerService(
    private val playerRepository: PlayerRepository,
    private val rankedPlayerStatRepository: RankedPlayerStatRepository,
    private val currentSeasonRepository: CurrentSeasonRepository,
) {
    /**
     * Returns all players with their season statistics for the current season.
     * If no stats exist for the current season (e.g. new DB or season mismatch),
     * returns all players with zero/default stats so the list is never empty when players exist.
     *
     * @return list of [PlayerWithStatsDto]; never null, may be empty only if there are no players.
     */
    fun getPlayersWithCurrentSeasonStats(): List<PlayerWithStatsDto> {
        val season = currentSeasonRepository.findCurrentSeason() ?: 1
        val stats = rankedPlayerStatRepository.findBySeason(season)
        if (stats.isNotEmpty()) {
            return stats
                .map { stat ->
                    val player = playerRepository.findById(stat.playerId).orElse(null) ?: return@map null
                    PlayerWithStatsDto(
                        id = player.id,
                        nickname = player.nickname,
                        nation = player.nation,
                        rating = player.rating,
                        dzrating = player.dzrating,
                        elo = player.elo,
                        br = stat.br,
                        played = stat.played,
                        best = stat.best,
                        won = stat.won,
                        lost = stat.lost,
                        draw = stat.draw,
                        score = stat.score,
                        mvp = stat.mvp,
                    )
                }.filterNotNull()
        }
        // No stats for current season: return all players with zero stats so UI shows them
        return playerRepository.findAll().map { player ->
            PlayerWithStatsDto(
                id = player.id,
                nickname = player.nickname,
                nation = player.nation,
                rating = player.rating,
                dzrating = player.dzrating,
                elo = player.elo,
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
     * Creates a new player and initial [RankedPlayerStat] row for the current season.
     * Initial BR/best are derived from rating buckets (e.g. rating 60 -> 750, 89 -> 1000).
     *
     * @param request nickname, nation (2-letter code), and rating (1–99).
     * @return the persisted [Player].
     * @throws IllegalArgumentException if nickname is empty, rating out of range, or nation not 2 letters.
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

        val player =
            Player(
                nickname = nickname,
                nation = nation,
                rating = rating,
                dzrating = dzrating,
                elo = 0,
            )
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
     *
     * @param id the player's primary key.
     * @param request nickname, nation, rating, dzrating, and new br.
     * @return the persisted [Player].
     * @throws IllegalArgumentException if id not found or validation fails.
     * @throws IllegalStateException if no season stats exist for this player and season.
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
     *
     * @param id the player's primary key. No-op if the player does not exist.
     */
    @Transactional
    fun deletePlayer(id: Long) {
        if (!playerRepository.existsById(id)) {
            return
        }
        playerRepository.deleteById(id)
    }
}
