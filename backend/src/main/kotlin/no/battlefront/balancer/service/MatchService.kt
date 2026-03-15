package no.battlefront.balancer.service

import no.battlefront.balancer.dto.LastMatchPlayerDto
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedMatchRepository
import no.battlefront.balancer.repository.RankedMatchStatRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import org.springframework.stereotype.Service

@Service
class MatchService(
    private val rankedMatchRepository: RankedMatchRepository,
    private val rankedMatchStatRepository: RankedMatchStatRepository,
    private val playerRepository: PlayerRepository,
    private val rankedPlayerStatRepository: RankedPlayerStatRepository,
    private val currentSeasonRepository: CurrentSeasonRepository
) {

    /**
     * Spillere som deltok i siste match, med sesongstat for nåværende sesong.
     * Tilsvarer api_lastMatch.php.
     */
    fun getPlayersInLastMatch(): List<LastMatchPlayerDto> {
        val latestMatch = rankedMatchRepository.findTop1ByOrderByIdDesc() ?: return emptyList()
        val season = currentSeasonRepository.findCurrentSeason() ?: 1
        val stats = rankedMatchStatRepository.findByMatchId(latestMatch.id)
        return stats.mapNotNull { stat ->
            val player = playerRepository.findById(stat.playerId).orElse(null) ?: return@mapNotNull null
            val pstat = rankedPlayerStatRepository.findByPlayerIdAndSeason(stat.playerId, season)
                ?: return@mapNotNull null
            LastMatchPlayerDto(
                id = player.id,
                nickname = player.nickname,
                nation = player.nation,
                rating = player.rating,
                br = pstat.br,
                best = pstat.best,
                played = pstat.played,
                won = pstat.won,
                lost = pstat.lost,
                draw = pstat.draw,
                score = pstat.score,
                mvp = pstat.mvp
            )
        }
    }
}
