package no.battlefront.balancer.service

import no.battlefront.balancer.dto.BalanceRequest
import no.battlefront.balancer.dto.BalanceResponse
import no.battlefront.balancer.model.Player
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import org.springframework.stereotype.Service
import java.text.Collator
import java.util.Locale
import kotlin.math.abs

/**
 * Splits a lobby into two teams with ratings as even as possible.
 *
 * The algorithm is ported from `generateCombinations` in the frontend's RankedPage and InternPage.
 */
@Service
class BalanceService(
    private val playerRepository: PlayerRepository,
    private val rankedPlayerStatRepository: RankedPlayerStatRepository,
    private val currentSeasonRepository: CurrentSeasonRepository,
) {
    /**
     * Balances the players in [request], given either as persona IDs (Auric) or player IDs (website). The teams are
     * returned with the same kind of ID. The first team becomes the Rebels.
     *
     * @throws IllegalArgumentException on an invalid rating key, both or neither ID list, duplicate IDs, an odd or
     *   out-of-range player count, unknown player IDs, or (for "br") players without stats in the current season.
     * @throws UnknownPlayersException if any persona ID belongs to no player.
     */
    fun balance(request: BalanceRequest): BalanceResponse {
        require(request.ratingKey in RATING_KEYS) { "ratingKey must be one of ${RATING_KEYS.joinToString()}" }
        require(request.personaIds.isEmpty() != request.playerIds.isEmpty()) { "Send either personaIds or playerIds" }
        val byPersona = request.personaIds.isNotEmpty()
        val ids = if (byPersona) request.personaIds else request.playerIds
        require(ids.size == ids.toSet().size) { "Duplicate IDs" }
        require(ids.size in MIN_PLAYERS..MAX_PLAYERS) { "Between $MIN_PLAYERS and $MAX_PLAYERS players are required" }
        require(ids.size % 2 == 0) { "An even number of players is required" }

        val players = if (byPersona) playerRepository.findByPersonaIdIn(ids) else playerRepository.findAllById(ids)
        val idOf: (Player) -> Long = if (byPersona) { p -> checkNotNull(p.personaId) } else { p -> p.id }
        val known = players.map(idOf).toSet()
        val unknown = ids.filter { it !in known }
        if (byPersona && unknown.isNotEmpty()) throw UnknownPlayersException(unknown)
        require(unknown.isEmpty()) { "Unknown player ids: ${unknown.joinToString()}" }

        val (rebels, imperials) = splitTeams(toCandidates(players, request.ratingKey, idOf))
        return BalanceResponse(
            rebels = rebels.map { it.id },
            imperials = imperials.map { it.id },
            rebelAverage = rebels.map { it.rating }.average(),
            imperialAverage = imperials.map { it.rating }.average(),
        )
    }

    private fun toCandidates(
        players: List<Player>,
        ratingKey: String,
        idOf: (Player) -> Long,
    ): List<Candidate> {
        val ratingOf: (Player) -> Int =
            when (ratingKey) {
                "rating" -> { p -> p.rating }
                "dzrating" -> { p -> p.dzrating }
                else -> {
                    val season = currentSeasonRepository.findCurrentSeason() ?: 1
                    val brByPlayer =
                        rankedPlayerStatRepository
                            .findBySeasonAndPlayerIdIn(season, players.map { it.id })
                            .associate { it.playerId to it.br }
                    val missing = players.filter { it.id !in brByPlayer }
                    require(missing.isEmpty()) { "No BR this season for: ${missing.joinToString { it.nickname }}" }
                    ({ p -> brByPlayer.getValue(p.id) })
                }
            }
        return players.map { p -> Candidate(idOf(p), p.nickname, ratingOf(p)) }
    }

    /** A player to place; [id] is the persona ID or player ID, whichever the request used. */
    internal data class Candidate(
        val id: Long,
        val nickname: String,
        val rating: Int,
    )

    private data class Subset(
        val members: List<Candidate>,
        val sum: Int,
    )

    /**
     * Sorts by rating, splits the list into a strong and a weak half, and for every k combines k players from the
     * strong half with teamSize - k from the weak half, picking the team whose sum is closest to half the total.
     */
    internal fun splitTeams(players: List<Candidate>): Pair<List<Candidate>, List<Candidate>> {
        val collator = Collator.getInstance(Locale.ROOT)
        val byRating = compareByDescending<Candidate> { it.rating }.thenComparator { a, b -> collator.compare(a.nickname, b.nickname) }
        val sorted = players.sortedWith(byRating)
        val teamSize = sorted.size / 2
        val total = sorted.sumOf { it.rating }
        val halfTotal = total / 2.0
        val left = sorted.subList(0, teamSize)
        val right = sorted.subList(teamSize, sorted.size)

        var bestDiff = Int.MAX_VALUE
        var bestTeam1 = emptyList<Candidate>()
        for (k in 0..teamSize) {
            val leftSubsets = subsetsOfSize(left, k)
            val rightSubsets = subsetsOfSize(right, teamSize - k).sortedBy { it.sum }
            for (ls in leftSubsets) {
                val match = findClosest(rightSubsets, halfTotal - ls.sum)
                val diff = abs(total - 2 * (ls.sum + match.sum))
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestTeam1 = ls.members + match.members
                }
                if (bestDiff == 0) break
            }
            if (bestDiff == 0) break
        }

        val bestTeam2 = sorted.filter { it !in bestTeam1 }
        return bestTeam1.sortedWith(byRating) to bestTeam2.sortedWith(byRating)
    }

    /** All subsets of exactly [size] players, in the same order as the frontend's backtracking. */
    private fun subsetsOfSize(
        players: List<Candidate>,
        size: Int,
    ): List<Subset> {
        val result = mutableListOf<Subset>()

        fun backtrack(
            start: Int,
            current: List<Candidate>,
            sum: Int,
        ) {
            if (current.size == size) {
                result.add(Subset(current, sum))
                return
            }
            for (i in start until players.size) {
                backtrack(i + 1, current + players[i], sum + players[i].rating)
            }
        }
        backtrack(0, emptyList(), 0)
        return result
    }

    /** Binary search in [subsets] (sorted by sum) for the sum closest to [target]. */
    private fun findClosest(
        subsets: List<Subset>,
        target: Double,
    ): Subset {
        var lo = 0
        var hi = subsets.size - 1
        var closest = subsets[0]
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val cur = subsets[mid]
            if (abs(cur.sum - target) < abs(closest.sum - target)) closest = cur
            if (cur.sum.toDouble() == target) {
                return cur
            } else if (cur.sum < target) {
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return closest
    }

    private companion object {
        val RATING_KEYS = listOf("br", "rating", "dzrating")
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 32
    }
}
