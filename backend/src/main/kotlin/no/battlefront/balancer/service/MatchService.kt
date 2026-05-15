package no.battlefront.balancer.service

import no.battlefront.balancer.dto.LastMatchPlayerDto
import no.battlefront.balancer.dto.MatchDetailDto
import no.battlefront.balancer.dto.MatchPlayerStatDto
import no.battlefront.balancer.dto.MatchSubmitRequest
import no.battlefront.balancer.dto.MatchSummaryDto
import no.battlefront.balancer.dto.PlayerMatchStatDto
import no.battlefront.balancer.model.RankedMatch
import no.battlefront.balancer.model.RankedMatchStat
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedMatchRepository
import no.battlefront.balancer.repository.RankedMatchStatRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.CurrentUserService
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.format.DateTimeFormatter

@Service
class MatchService(
    private val rankedMatchRepository: RankedMatchRepository,
    private val rankedMatchStatRepository: RankedMatchStatRepository,
    private val playerRepository: PlayerRepository,
    private val rankedPlayerStatRepository: RankedPlayerStatRepository,
    private val currentSeasonRepository: CurrentSeasonRepository,
    private val userRepository: UserRepository,
    private val currentUserService: CurrentUserService,
) {
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * Returns all distinct seasons that have at least one match.
     */
    fun getSeasons(): List<Int> = rankedMatchRepository.findDistinctSeasons()

    /**
     * Returns a summary list of matches.
     * - [season] null or blank → current season
     * - [season] "all" → all seasons
     * - [season] numeric string → that season
     */
    fun getMatchList(season: String?): List<MatchSummaryDto> {
        val matches =
            when {
                season == "all" -> rankedMatchRepository.findAllByOrderByIdDesc()
                season != null && season.isNotBlank() ->
                    rankedMatchRepository.findBySeasonOrderByIdDesc(season.toInt())
                else -> {
                    val current = currentSeasonRepository.findCurrentSeason() ?: 1
                    rankedMatchRepository.findBySeasonOrderByIdDesc(current)
                }
            }
        return matches.map { m ->
            val mvpName = m.mvpId?.let { playerRepository.findById(it).orElse(null)?.nickname }
            val supervisorName = userRepository.findById(m.supervisorId).orElse(null)?.username
            MatchSummaryDto(
                id = m.id,
                date = m.date.format(isoFormatter),
                map = m.map,
                rule = m.rule,
                teamSize = m.teamSize,
                mvpName = mvpName,
                supervisorName = supervisorName,
            )
        }
    }

    /**
     * Returns the full detail for a single match.
     * - [matchId] null → latest match for the effective season
     * - [season] null or blank → current season; "all" → any season (ignores season filter)
     */
    fun getMatchDetail(
        matchId: Long?,
        season: String?,
    ): MatchDetailDto? {
        val match: RankedMatch =
            if (matchId != null) {
                rankedMatchRepository.findById(matchId).orElse(null) ?: return null
            } else {
                when {
                    season == "all" -> rankedMatchRepository.findTop1ByOrderByIdDesc()
                    season != null && season.isNotBlank() ->
                        rankedMatchRepository.findTop1BySeasonOrderByIdDesc(season.toInt())
                    else -> {
                        val current = currentSeasonRepository.findCurrentSeason() ?: 1
                        rankedMatchRepository.findTop1BySeasonOrderByIdDesc(current)
                    }
                } ?: return null
            }

        val stats = rankedMatchStatRepository.findByMatchId(match.id)
        val rebels = mutableListOf<MatchPlayerStatDto>()
        val imperials = mutableListOf<MatchPlayerStatDto>()

        for (stat in stats) {
            val player = playerRepository.findById(stat.playerId).orElse(null) ?: continue
            val dto =
                MatchPlayerStatDto(
                    nickname = player.nickname,
                    nation = player.nation,
                    score = stat.score,
                    updateBr = stat.updateBr,
                    newBr = stat.newBr,
                    perf = stat.perf,
                )
            if (stat.faction == "Rebel") rebels.add(dto) else imperials.add(dto)
        }

        val comparator = compareByDescending<MatchPlayerStatDto> { it.updateBr }.thenByDescending { it.score }
        return MatchDetailDto(
            id = match.id,
            rebelScore = match.rebelScore,
            imperialScore = match.imperialScore,
            teamSize = match.teamSize,
            rebels = rebels.sortedWith(comparator),
            imperials = imperials.sortedWith(comparator),
        )
    }

    /**
     * Persists a match, per-player stats ([RankedMatchStat]), and updates [RankedPlayerStat][no.battlefront.balancer.model.RankedPlayerStat] for all participants.
     * Expects [MatchSubmitRequest.matchData] as list: [map, team_size, mvp_id, rebel_score, imperial_score, rule].
     *
     * @param request the match payload (matchData, rebels, imperials, optional supervisorId).
     * @throws IllegalArgumentException if matchData has fewer than 6 elements.
     */
    @Transactional
    fun submitMatch(request: MatchSubmitRequest) {
        require(request.matchData.size >= 6) { "Incomplete data provided" }
        val supervisorId = currentUserService.currentUserId() ?: throw AccessDeniedException("Not authenticated")
        val map = request.matchData[0].toString()
        val teamSize = (request.matchData[1] as Number).toInt()
        val mvpIdRaw = (request.matchData[2] as Number).toLong()
        val mvpId = if (mvpIdRaw == 0L) null else mvpIdRaw
        val rebelScore = (request.matchData[3] as Number).toInt()
        val imperialScore = (request.matchData[4] as Number).toInt()
        val rule = request.matchData[5].toString()
        val season = currentSeasonRepository.findCurrentSeason() ?: 1

        val match =
            RankedMatch(
                map = map,
                rule = rule,
                season = season,
                teamSize = teamSize,
                rebelScore = rebelScore,
                imperialScore = imperialScore,
                mvpId = mvpId,
                supervisorId = supervisorId,
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
                    newBr = p.newBR,
                ),
            )
            val pstat =
                rankedPlayerStatRepository.findByPlayerIdAndSeason(p.id, season)
                    ?: continue
            val newBest = maxOf(pstat.best, p.newBR)
            val (won, lost, draw) =
                when (p.outcome) {
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
            val pstat =
                rankedPlayerStatRepository.findByPlayerIdAndSeason(stat.playerId, season)
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
                mvp = pstat.mvp,
            )
        }
    }
}
