package no.battlefront.balancer.service

import no.battlefront.balancer.model.CurrentSeason
import no.battlefront.balancer.model.RankedPlayerStat
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SeasonService(
    private val currentSeasonRepository: CurrentSeasonRepository,
    private val playerRepository: PlayerRepository,
    private val rankedPlayerStatRepository: RankedPlayerStatRepository,
) {
    fun currentSeason(): Int = currentSeasonRepository.findCurrentSeason() ?: 1

    /**
     * Starts the next season: inserts a new row in current_season and creates
     * a ranked_pstats row for every player with starting BR derived from rating.
     */
    @Transactional
    fun startNextSeason(): Map<String, Int> {
        val newSeason = currentSeason() + 1
        currentSeasonRepository.save(CurrentSeason(season = newSeason))

        val players = playerRepository.findAll()
        for (player in players) {
            val br = startingBr(player.rating)
            rankedPlayerStatRepository.save(
                RankedPlayerStat(
                    playerId = player.id,
                    season = newSeason,
                    br = br,
                    best = br,
                    played = 0,
                    won = 0,
                    lost = 0,
                    draw = 0,
                    score = 0,
                    mvp = 0,
                ),
            )
        }
        return mapOf("newSeason" to newSeason, "playersInitialized" to players.size)
    }

    /**
     * Deletes ranked_pstats rows from the given season where played = 0.
     */
    @Transactional
    fun cleanupSeason(season: Int): Map<String, Int> {
        require(season >= 1) { "Season must be >= 1" }
        require(season < currentSeason()) { "Cannot cleanup the current season" }
        val deleted = rankedPlayerStatRepository.deleteBySeasonAndPlayedZero(season)
        return mapOf("season" to season, "deletedRows" to deleted)
    }

    private fun startingBr(rating: Int): Int =
        when {
            rating <= 60 -> 750
            rating <= 65 -> 800
            rating <= 71 -> 850
            rating <= 78 -> 900
            rating <= 86 -> 950
            rating <= 89 -> 1000
            else -> 1050
        }
}
