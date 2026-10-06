package no.battlefront.balancer.service

import no.battlefront.balancer.APP_TIME_ZONE
import no.battlefront.balancer.dto.PlayerMatchStatDto
import no.battlefront.balancer.dto.RatedPlayer
import no.battlefront.balancer.dto.RatingPlayer
import no.battlefront.balancer.dto.RawMatchPlayer
import no.battlefront.balancer.dto.RawMatchPlayerResultDto
import no.battlefront.balancer.dto.RawMatchRequest
import no.battlefront.balancer.dto.RawMatchResultDto
import no.battlefront.balancer.model.Player
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import no.battlefront.balancer.security.CurrentUserService
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

/**
 * Stores a match from raw results (scores, kills, deaths): computes BR, perf and MVP with [RatingService] and
 * saves it exactly like a match submitted on the website. Used by Auric after every map.
 */
@Service
class RawMatchService(
    private val playerRepository: PlayerRepository,
    private val rankedPlayerStatRepository: RankedPlayerStatRepository,
    private val currentSeasonRepository: CurrentSeasonRepository,
    private val ratingService: RatingService,
    private val matchService: MatchService,
    private val randomizerService: RandomizerService,
    private val currentUserService: CurrentUserService,
) {
    /**
     * Rates and stores the match. The supervisor is the current user, or the token owner for Auric.
     *
     * @throws IllegalArgumentException on invalid input (team sizes, ranges, duplicates, players without BR).
     * @throws UnknownPlayersException if any persona ID belongs to no player; nothing is stored then.
     */
    @Transactional
    fun submit(request: RawMatchRequest): RawMatchResultDto {
        val supervisorId = currentUserService.currentUserId() ?: throw AccessDeniedException("Not authenticated")
        validate(request)
        val date = parseEndedAt(request.endedAt)
        val players = resolvePlayers(request.players)
        require(players.map { it.id }.toSet().size == players.size) { "The same player appears more than once" }

        request.players.zip(players).forEach { (raw, player) ->
            raw.name
                ?.trim()
                ?.take(100)
                ?.ifEmpty { null }
                ?.let { player.lastSeenName = it }
        }

        val season = currentSeasonRepository.findCurrentSeason() ?: 1
        val brByPlayer =
            rankedPlayerStatRepository.findBySeasonAndPlayerIdIn(season, players.map { it.id }).associate { it.playerId to it.br }
        val missing = players.filter { it.id !in brByPlayer }
        require(missing.isEmpty()) { "No BR this season for: ${missing.joinToString { it.nickname }}" }

        val entries = request.players.zip(players)

        fun team(faction: String) =
            entries
                .filter { (raw, _) -> normalizeFaction(raw.faction) == faction }
                .map { (raw, p) -> RatingPlayer(p.id, brByPlayer.getValue(p.id), raw.score, raw.kills, raw.deaths, raw.partnerless) }
        val rebels = team(REBEL)
        val rating = ratingService.rate(rebels, team(IMPERIAL), request.rebelScore, request.imperialScore)

        val map = MapNames.displayName(request.map)
        val rule =
            request.rule
                ?.trim()
                ?.take(50)
                ?.ifEmpty { null } ?: "?"
        val match =
            matchService.recordMatch(
                map = map,
                rule = rule,
                teamSize = rebels.size,
                rebelScore = request.rebelScore,
                imperialScore = request.imperialScore,
                mvpId = rating.mvpId,
                supervisorId = supervisorId,
                allPlayers = (rating.rebels + rating.imperials).map { it.toStatDto() },
                date = date,
            )
        val next = randomizerService.pickAndSave()

        val byId = players.associateBy { it.id }

        fun results(rated: List<RatedPlayer>) =
            rated.map { r ->
                val p = byId.getValue(r.playerId)
                RawMatchPlayerResultDto(p.id, p.personaId, p.nickname, r.outcome, r.score, r.perf, r.change, r.newBr)
            }
        return RawMatchResultDto(
            matchId = match.id,
            map = map,
            rule = rule,
            mvpPlayerId = rating.mvpId,
            rebels = results(rating.rebels),
            imperials = results(rating.imperials),
            nextMap = next.map,
            nextRule = next.rule,
        )
    }

    private fun validate(request: RawMatchRequest) {
        require(request.map.isNotBlank()) { "Map is required" }
        require(request.rebelScore in 0..MAX_PODS && request.imperialScore in 0..MAX_PODS) { "Scores must be between 0 and $MAX_PODS" }
        request.players.forEach { p ->
            require((p.personaId == null) != (p.playerId == null)) { "Each player needs exactly one of personaId or playerId" }
            require(p.personaId == null || p.personaId > 0) { "Persona ID must be a positive number" }
            require(p.score in 0..MAX_STAT && p.kills in 0..MAX_STAT && p.deaths in 0..MAX_STAT) {
                "Score, kills and deaths must be between 0 and $MAX_STAT"
            }
            requireNotNull(normalizeFaction(p.faction)) { "Faction must be Rebel or Imperial" }
        }
        val rebels = request.players.filter { normalizeFaction(it.faction) == REBEL }
        val imperials = request.players.filter { normalizeFaction(it.faction) == IMPERIAL }
        require(rebels.isNotEmpty() && imperials.isNotEmpty()) { "Both teams need players" }
        require(rebels.size == imperials.size) { "Teams must be the same size (${rebels.size} vs ${imperials.size})" }
        require(rebels.size <= MAX_TEAM_SIZE) { "At most $MAX_TEAM_SIZE players per team" }
        require(rebels.count { it.partnerless } <= 1 && imperials.count { it.partnerless } <= 1) {
            "At most one partnerless player per team"
        }
    }

    /** Returns the players in request order. */
    private fun resolvePlayers(raw: List<RawMatchPlayer>): List<Player> {
        val personaIds = raw.mapNotNull { it.personaId }
        val byPersona =
            if (personaIds.isEmpty()) {
                emptyMap()
            } else {
                playerRepository
                    .findByPersonaIdIn(
                        personaIds,
                    ).associateBy { it.personaId }
            }
        val unknown = personaIds.filter { it !in byPersona }.distinct()
        if (unknown.isNotEmpty()) throw UnknownPlayersException(unknown)

        val playerIds = raw.mapNotNull { it.playerId }
        val byId = if (playerIds.isEmpty()) emptyMap() else playerRepository.findAllById(playerIds).associateBy { it.id }
        val unknownIds = playerIds.filter { it !in byId }.distinct()
        require(unknownIds.isEmpty()) { "Unknown player ids: ${unknownIds.joinToString()}" }

        return raw.map { r -> r.personaId?.let { byPersona.getValue(it) } ?: byId.getValue(checkNotNull(r.playerId)) }
    }

    private fun parseEndedAt(endedAt: String?): LocalDateTime {
        if (endedAt.isNullOrBlank()) return LocalDateTime.now()
        return try {
            LocalDateTime.ofInstant(Instant.parse(endedAt), ZoneId.of(APP_TIME_ZONE))
        } catch (e: DateTimeParseException) {
            throw IllegalArgumentException("endedAt must be an ISO-8601 instant, e.g. 2026-10-06T19:14:40Z")
        }
    }

    private fun normalizeFaction(faction: String): String? =
        when (faction.trim().lowercase()) {
            "rebel", "rebels" -> REBEL
            "imperial", "imperials", "empire" -> IMPERIAL
            else -> null
        }

    private fun RatedPlayer.toStatDto() = PlayerMatchStatDto(playerId, faction, outcome, score, perf, change, newBr, kills, deaths)

    private companion object {
        const val REBEL = "Rebel"
        const val IMPERIAL = "Imperial"
        const val MAX_PODS = 5
        const val MAX_STAT = 100_000
        const val MAX_TEAM_SIZE = 16
    }
}
