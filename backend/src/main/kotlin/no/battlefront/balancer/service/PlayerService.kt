package no.battlefront.balancer.service

import no.battlefront.balancer.dto.PlayerWithStatsDto
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import org.springframework.stereotype.Service

@Service
class PlayerService(
    private val playerRepository: PlayerRepository,
    private val rankedPlayerStatRepository: RankedPlayerStatRepository,
    private val currentSeasonRepository: CurrentSeasonRepository
) {

    /**
     * Spillere med sesongstatistikk for nåværende sesong (tilsvarer api_players.php).
     */
    fun getPlayersWithCurrentSeasonStats(): List<PlayerWithStatsDto> {
        val season = currentSeasonRepository.findCurrentSeason() ?: 1
        val stats = rankedPlayerStatRepository.findBySeason(season)
        return stats.map { stat ->
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
                mvp = stat.mvp
            )
        }.filterNotNull()
    }
}
