package no.battlefront.balancer.service

import no.battlefront.balancer.dto.MatchRating
import no.battlefront.balancer.dto.RatedPlayer
import no.battlefront.balancer.dto.RatingPlayer
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Computes BR changes, performance and MVP for a ranked match.
 *
 * Ported 1:1 from the calculation in the frontend's RankedPage. Rounding follows JavaScript:
 * [roundToInt] matches `Math.round` (ties towards positive infinity) and [roundPerf] matches `toFixed(2)`.
 */
@Service
class RatingService {
    /**
     * Rates a match between [rebels] and [imperials] given the pods each team captured.
     *
     * @throws IllegalArgumentException if a team is empty or a player has a non-positive BR.
     */
    fun rate(
        rebels: List<RatingPlayer>,
        imperials: List<RatingPlayer>,
        rebelScore: Int,
        imperialScore: Int,
    ): MatchRating {
        require(rebels.isNotEmpty() && imperials.isNotEmpty()) { "Both teams need at least one player" }
        require((rebels + imperials).all { it.br > 0 }) { "BR must be positive" }

        val rebelOutcome =
            when {
                rebelScore > imperialScore -> WON
                rebelScore < imperialScore -> LOST
                else -> DRAW
            }
        val scoreDiff = abs(rebelScore - imperialScore)
        val rebelBr = rebels.map { it.br }.average().roundToInt()
        val imperialBr = imperials.map { it.br }.average().roundToInt()
        val expRebel = expectedScore(rebelBr, imperialBr)
        val expImperial = expectedScore(imperialBr, rebelBr)

        val (rebelResults, imperialResults) =
            when (rebelOutcome) {
                WON -> {
                    val delta = (K_FACTOR * (1 - expRebel)).roundToInt()
                    calcWin(rebels, REBEL, rebelBr, delta) to
                        calcLoss(imperials, IMPERIAL, imperialBr, -delta, scoreDiff)
                }
                LOST -> {
                    val delta = (K_FACTOR * (1 - expImperial)).roundToInt()
                    calcLoss(rebels, REBEL, rebelBr, -delta, scoreDiff) to
                        calcWin(imperials, IMPERIAL, imperialBr, delta)
                }
                else -> {
                    val rebelDelta = (K_FACTOR * (0.5 - expRebel)).roundToInt()
                    calcDraw(rebels, REBEL, rebelBr, rebelDelta) to
                        calcDraw(imperials, IMPERIAL, imperialBr, -rebelDelta)
                }
            }

        return MatchRating(rebelResults, imperialResults, pickMvp(rebelResults, imperialResults, rebelOutcome))
    }

    private fun expectedScore(
        teamBr: Int,
        opponentBr: Int,
    ): Double = 1 / (1 + 10.0.pow((opponentBr - teamBr) / 400.0))

    private fun calcWin(
        team: List<RatingPlayer>,
        faction: String,
        teamBr: Int,
        teamDelta: Int,
    ) = rateTeam(team, faction, WON, teamBr) { acCarry ->
        (teamDelta - (1 - acCarry) * teamDelta).roundToInt()
    }

    private fun calcLoss(
        team: List<RatingPlayer>,
        faction: String,
        teamBr: Int,
        teamDelta: Int,
        scoreDiff: Int,
    ): List<RatedPlayer> {
        val objectiveBonus = OBJECTIVE_BONUS[min(scoreDiff, 5)]
        return rateTeam(team, faction, LOST, teamBr) { acCarry ->
            (teamDelta + LOSS_WEIGHT * (1 - acCarry) * teamDelta).roundToInt() + objectiveBonus
        }
    }

    private fun calcDraw(
        team: List<RatingPlayer>,
        faction: String,
        teamBr: Int,
        teamDelta: Int,
    ) = rateTeam(team, faction, DRAW, teamBr) { acCarry ->
        (teamDelta - (1 - acCarry) * DRAW_WEIGHT).roundToInt() + DRAW_BONUS
    }

    /**
     * Shared per-player part of win/loss/draw. The team total uses raw scores while each player's own score
     * gets the partnerless and K/D adjustments, exactly as in the frontend.
     */
    private fun rateTeam(
        team: List<RatingPlayer>,
        faction: String,
        outcome: String,
        teamBr: Int,
        changeFor: (acCarry: Double) -> Int,
    ): List<RatedPlayer> {
        val teamTotal = team.sumOf { it.score }
        val avgKd = team.map { kd(it) }.average()
        return team.map { p ->
            val exCarry = p.br.toDouble() / teamBr
            val acCarry =
                if (teamTotal == 0) {
                    1.0
                } else {
                    (effectiveScore(p, avgKd).toDouble() / teamTotal) / (1.0 / team.size) / exCarry
                }
            val change = changeFor(acCarry)
            RatedPlayer(
                playerId = p.playerId,
                faction = faction,
                outcome = outcome,
                score = p.score,
                kills = p.kills,
                deaths = p.deaths,
                perf = roundPerf(acCarry),
                change = change,
                newBr = p.br + change,
            )
        }
    }

    private fun kd(p: RatingPlayer): Double = p.kills.toDouble() / max(p.deaths, 1)

    private fun kdModifier(
        p: RatingPlayer,
        teamAverageKd: Double,
    ): Double {
        val relativeKd = if (teamAverageKd > 0) kd(p) / teamAverageKd else 1.0
        val cappedKdImpact = max(-1.0, min(1.0, relativeKd - 1))
        return 1 + KD_WEIGHT * cappedKdImpact
    }

    private fun effectiveScore(
        p: RatingPlayer,
        teamAverageKd: Double,
    ): Int {
        val partnerlessScore = if (p.partnerless) (p.score * PARTNERLESS_FACTOR).roundToInt() else p.score
        return (partnerlessScore * kdModifier(p, teamAverageKd)).roundToInt()
    }

    /** Same result as JavaScript's `parseFloat(x.toFixed(2))`, which rounds the exact binary value half up. */
    private fun roundPerf(value: Double): Double = BigDecimal(value).setScale(2, RoundingMode.HALF_UP).toDouble()

    /**
     * MVP is the highest scorer. If the best Rebel and best Imperial tie, the winning team's player wins;
     * on a draw the one with the larger BR change, and if those are equal too, the Imperial.
     */
    private fun pickMvp(
        rebels: List<RatedPlayer>,
        imperials: List<RatedPlayer>,
        rebelOutcome: String,
    ): Long? {
        val bestRebel = rebels.filter { it.score > 0 }.maxByOrNull { it.score }
        val bestImperial = imperials.filter { it.score > 0 }.maxByOrNull { it.score }
        val rebelTop = bestRebel?.score ?: 0
        val imperialTop = bestImperial?.score ?: 0
        return when {
            imperialTop > rebelTop -> bestImperial?.playerId
            rebelTop > imperialTop -> bestRebel?.playerId
            rebelOutcome == WON -> bestRebel?.playerId
            rebelOutcome == LOST -> bestImperial?.playerId
            (bestRebel?.change ?: 0) > (bestImperial?.change ?: 0) -> bestRebel?.playerId
            else -> bestImperial?.playerId
        }
    }

    private companion object {
        const val REBEL = "Rebel"
        const val IMPERIAL = "Imperial"
        const val WON = "Won"
        const val LOST = "Lost"
        const val DRAW = "Draw"

        const val K_FACTOR = 40
        const val LOSS_WEIGHT = 1.25
        const val DRAW_WEIGHT = 20
        const val DRAW_BONUS = 4
        const val PARTNERLESS_FACTOR = 1.1
        const val KD_WEIGHT = 0.05

        /** Bonus for the losing team by pod difference: a close loss costs less, a 0-5 loss costs more. */
        val OBJECTIVE_BONUS = listOf(0, 3, 2, 1, 0, -1)
    }
}
