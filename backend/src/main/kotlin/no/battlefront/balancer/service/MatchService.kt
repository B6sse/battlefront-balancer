package no.battlefront.balancer.service

import no.battlefront.balancer.dto.LastMatchPlayerDto
import no.battlefront.balancer.dto.MatchSubmitRequest
import no.battlefront.balancer.dto.PlayerMatchStatDto
import no.battlefront.balancer.model.RankedMatch
import no.battlefront.balancer.model.RankedMatchStat
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedMatchRepository
import no.battlefront.balancer.repository.RankedMatchStatRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MatchService(
    private val rankedMatchRepository: RankedMatchRepository,
    private val rankedMatchStatRepository: RankedMatchStatRepository,
    private val playerRepository: PlayerRepository,
    private val rankedPlayerStatRepository: RankedPlayerStatRepository,
    private val currentSeasonRepository: CurrentSeasonRepository
) {

    /**
     * Persists a match, per-player stats ([RankedMatchStat]), and updates [RankedPlayerStat] for all participants.
     * Expects [MatchSubmitRequest.matchData] as list: [map, team_size, mvp_id, rebel_score, imperial_score, rule].
     *
     * @param request the match payload (matchData, rebels, imperials, optional supervisorId).
     * @throws IllegalArgumentException if matchData has fewer than 6 elements.
     */
    @Transactional
    fun submitMatch(request: MatchSubmitRequest) {
        require(request.matchData.size >= 6) { "Incomplete data provided" }
        val map = request.matchData[0].toString()
        val teamSize = (request.matchData[1] as Number).toInt()
        val mvpIdRaw = (request.matchData[2] as Number).toLong()
        val mvpId = if (mvpIdRaw == 0L) null else mvpIdRaw
        val rebelScore = (request.matchData[3] as Number).toInt()
        val imperialScore = (request.matchData[4] as Number).toInt()
        val rule = request.matchData[5].toString()
        val season = currentSeasonRepository.findCurrentSeason() ?: 1

        val match = RankedMatch(
            map = map,
            rule = rule,
            season = season,
            teamSize = teamSize,
            rebelScore = rebelScore,
            imperialScore = imperialScore,
            mvpId = mvpId,
            supervisorId = request.supervisorId
        )
        val savedMatch = rankedMatchRepository.save(match)
        val matchId = savedMatch.id

        val allPlayers: List<PlayerMatchStatDto> = request.rebels + request.imperials
        for (p in allPlayers) {
            val mvp = if (mvpId == p.id) 1 else 0
            rankedMatchStatRepository.save(
                RankedMatchStat(
                    playerId = p.id,
                    matchId = matchId,
                    season = season,
                    faction = p.faction,
                    result = p.outcome,
                    score = p.score,
                    perf = p.perf,
                    updateBr = p.change,
                    newBr = p.newBR
                )
            )
            val pstat = rankedPlayerStatRepository.findByPlayerIdAndSeason(p.id, season)
                ?: continue
            val newBest = maxOf(pstat.best, p.newBR)
            val (won, lost, draw) = when (p.outcome) {
                "Won" -> Triple(1, 0, 0)
                "Lost" -> Triple(0, 1, 0)
                "Draw" -> Triple(0, 0, 1)
                else -> Triple(0, 0, 0)
            }
            pstat.br = p.newBR
            pstat.best = newBest
            pstat.played += 1
            pstat.won += won
            pstat.lost += lost
            pstat.draw += draw
            pstat.score += p.score
            pstat.mvp += mvp
            rankedPlayerStatRepository.save(pstat)
        }
    }

    /**
     * Returns players who participated in the most recent match, with their current season stats.
     *
     * @return list of [LastMatchPlayerDto]; empty if no match exists.
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
