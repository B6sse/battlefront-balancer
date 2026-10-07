package no.battlefront.balancer.service

import no.battlefront.balancer.dto.MatchRating
import no.battlefront.balancer.dto.RatedPlayer
import no.battlefront.balancer.dto.RatingPlayer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * JUnit test class for [RatingService].
 *
 * Expected values were produced by running the original calculation from the frontend's RankedPage in Node
 * with the same inputs, so these tests pin the port to the JavaScript behaviour.
 */
@Tag("RatingService")
class RatingServiceTest {
    private val service = RatingService()

    private fun p(
        id: Long,
        br: Int,
        score: Int,
        kills: Int,
        deaths: Int,
        partnerless: Boolean = false,
    ) = RatingPlayer(id, br, score, kills, deaths, partnerless)

    // Team BR: rebels round(1007.5) = 1008, imperials round(987.5) = 988
    private fun rebels() =
        listOf(
            p(1, 1100, 12000, 20, 10),
            p(2, 1050, 9500, 15, 12),
            p(3, 980, 8000, 12, 14),
            p(4, 900, 6000, 8, 15),
        )

    private fun imperials() =
        listOf(
            p(5, 1080, 10500, 18, 12),
            p(6, 1000, 9000, 14, 13),
            p(7, 950, 7000, 10, 15),
            p(8, 920, 5500, 6, 16),
        )

    private data class Row(
        val playerId: Long,
        val change: Int,
        val perf: Double,
        val newBr: Int,
    )

    private fun row(
        playerId: Long,
        change: Int,
        perf: Double,
        newBr: Int,
    ) = Row(playerId, change, perf, newBr)

    private fun assertTeam(
        expected: List<Row>,
        actual: List<RatedPlayer>,
    ) {
        assertEquals(expected, actual.map { Row(it.playerId, it.change, it.perf, it.newBr) })
    }

    private val imperialWinners =
        listOf(row(5, 26, 1.24, 1106), row(6, 24, 1.12, 1024), row(7, 19, 0.9, 969), row(8, 15, 0.72, 935))

    /**
     * Rebels win 5-2.
     *
     * Hand check for player 1: expRebel = 1 / (1 + 10^(-20/400)) = 0.5288, team delta = round(40 * 0.4712) = 19.
     * K/D 2.0 vs team average 1.16 gives modifier 1.0362, so score 12000 counts as 12434.
     * acCarry = (12434 / 35500) / (1/4) / (1100/1008) = 1.2838, change = round(19 - (1 - 1.2838) * 19) = 24.
     */
    @Test
    fun `clear win`() {
        val result = service.rate(rebels(), imperials(), 5, 2)

        assertTeam(
            listOf(row(1, 24, 1.28, 1124), row(2, 20, 1.03, 1070), row(3, 17, 0.92, 997), row(4, 14, 0.74, 914)),
            result.rebels,
        )
        assertTeam(
            listOf(row(5, -12, 1.24, 1068), row(6, -15, 1.12, 985), row(7, -20, 0.9, 930), row(8, -25, 0.72, 895)),
            result.imperials,
        )
        assertEquals(1L, result.mvpId)
        result.rebels.forEach {
            assertEquals("Rebel", it.faction)
            assertEquals("Won", it.outcome)
        }
        result.imperials.forEach {
            assertEquals("Imperial", it.faction)
            assertEquals("Lost", it.outcome)
        }
    }

    @Test
    fun `loss by one pod gives objective bonus +3`() {
        val result = service.rate(rebels(), imperials(), 4, 5)
        assertTeam(
            listOf(row(1, -11, 1.28, 1089), row(2, -17, 1.03, 1033), row(3, -20, 0.92, 960), row(4, -25, 0.74, 875)),
            result.rebels,
        )
        assertTeam(imperialWinners, result.imperials)
        result.rebels.forEach { assertEquals("Lost", it.outcome) }
        result.imperials.forEach { assertEquals("Won", it.outcome) }
    }

    @Test
    fun `loss by two pods gives objective bonus +2`() {
        val result = service.rate(rebels(), imperials(), 3, 5)
        assertTeam(
            listOf(row(1, -12, 1.28, 1088), row(2, -18, 1.03, 1032), row(3, -21, 0.92, 959), row(4, -26, 0.74, 874)),
            result.rebels,
        )
        assertTeam(imperialWinners, result.imperials)
    }

    @Test
    fun `loss by three pods gives objective bonus +1`() {
        val result = service.rate(rebels(), imperials(), 2, 5)
        assertTeam(
            listOf(row(1, -13, 1.28, 1087), row(2, -19, 1.03, 1031), row(3, -22, 0.92, 958), row(4, -27, 0.74, 873)),
            result.rebels,
        )
    }

    @Test
    fun `loss by four pods gives objective bonus 0`() {
        val result = service.rate(rebels(), imperials(), 1, 5)
        assertTeam(
            listOf(row(1, -14, 1.28, 1086), row(2, -20, 1.03, 1030), row(3, -23, 0.92, 957), row(4, -28, 0.74, 872)),
            result.rebels,
        )
    }

    @Test
    fun `loss by five pods gives objective bonus -1`() {
        val result = service.rate(rebels(), imperials(), 0, 5)
        assertTeam(
            listOf(row(1, -15, 1.28, 1085), row(2, -21, 1.03, 1029), row(3, -24, 0.92, 956), row(4, -29, 0.74, 871)),
            result.rebels,
        )
    }

    @Test
    fun draw() {
        val result = service.rate(rebels(), imperials(), 3, 3)
        assertTeam(
            listOf(row(1, 9, 1.28, 1109), row(2, 4, 1.03, 1054), row(3, 1, 0.92, 981), row(4, -2, 0.74, 898)),
            result.rebels,
        )
        assertTeam(
            listOf(row(5, 10, 1.24, 1090), row(6, 7, 1.12, 1007), row(7, 3, 0.9, 953), row(8, -1, 0.72, 919)),
            result.imperials,
        )
        (result.rebels + result.imperials).forEach { assertEquals("Draw", it.outcome) }
    }

    @Test
    fun `partnerless player gets 10 percent extra score and teammates are unchanged`() {
        val team = rebels().dropLast(1) + p(4, 900, 6000, 8, 15, partnerless = true)
        val result = service.rate(team, imperials(), 5, 2)
        assertTeam(
            listOf(row(1, 24, 1.28, 1124), row(2, 20, 1.03, 1070), row(3, 17, 0.92, 997), row(4, 15, 0.81, 915)),
            result.rebels,
        )
    }

    @Test
    fun `team with zero total score gets perf 1 for everyone`() {
        val zeroImperials = imperials().map { it.copy(score = 0, kills = 0, deaths = 5) }
        val result = service.rate(rebels(), zeroImperials, 5, 0)
        assertTeam(
            listOf(row(5, -20, 1.0, 1060), row(6, -20, 1.0, 980), row(7, -20, 1.0, 930), row(8, -20, 1.0, 900)),
            result.imperials,
        )
        assertEquals(1L, result.mvpId)
    }

    private fun tieTeams(rebelBrs: Pair<Int, Int>): Pair<List<RatingPlayer>, List<RatingPlayer>> =
        listOf(p(1, rebelBrs.first, 9000, 10, 10), p(2, rebelBrs.second, 5000, 5, 10)) to
            listOf(p(5, 1000, 9000, 10, 10), p(6, 1000, 4000, 5, 10))

    private fun rateTie(
        rebelBrs: Pair<Int, Int>,
        rebelScore: Int,
        imperialScore: Int,
    ): MatchRating {
        val (r, i) = tieTeams(rebelBrs)
        return service.rate(r, i, rebelScore, imperialScore)
    }

    @Test
    fun `MVP tie goes to the winning team`() {
        assertEquals(1L, rateTie(1000 to 1000, 5, 3).mvpId)
        assertEquals(5L, rateTie(1000 to 1000, 2, 5).mvpId)
    }

    @Test
    fun `MVP tie on a draw goes to the larger BR change, Imperial if equal`() {
        // Rebel MVP candidate gains 13, Imperial 12
        assertEquals(1L, rateTie(900 to 1100, 4, 4).mvpId)
        // Rebel MVP candidate gains 8, Imperial 12
        assertEquals(5L, rateTie(1100 to 900, 4, 4).mvpId)
    }

    @Test
    fun `no MVP when nobody scored`() {
        val zero = listOf(p(1, 1000, 0, 0, 0), p(2, 1000, 0, 0, 0))
        val result = service.rate(zero, zero.map { it.copy(playerId = it.playerId + 4) }, 0, 0)
        assertNull(result.mvpId)
        (result.rebels + result.imperials).forEach { assertEquals(4, it.change) }
    }

    @Test
    fun `rejects empty team and non-positive BR`() {
        assertThrows<IllegalArgumentException> { service.rate(emptyList(), imperials(), 5, 0) }
        assertThrows<IllegalArgumentException> { service.rate(listOf(p(1, 0, 100, 1, 1)), imperials(), 5, 0) }
    }
}
