package no.battlefront.balancer.service

import no.battlefront.balancer.dto.BalanceRequest
import no.battlefront.balancer.model.Player
import no.battlefront.balancer.model.RankedPlayerStat
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * JUnit test class for [BalanceService].
 *
 * Expected teams for [BalanceService.splitTeams] were produced by running `generateCombinations` from the
 * frontend's InternPage in Node with the same players.
 */
@Tag("BalanceService")
class BalanceServiceTest {
    private val playerRepository: PlayerRepository = mock(PlayerRepository::class.java)
    private val rankedPlayerStatRepository: RankedPlayerStatRepository = mock(RankedPlayerStatRepository::class.java)
    private val currentSeasonRepository: CurrentSeasonRepository = mock(CurrentSeasonRepository::class.java)
    private val service = BalanceService(playerRepository, rankedPlayerStatRepository, currentSeasonRepository)

    private fun candidates(vararg players: Pair<String, Int>) =
        players.mapIndexed { i, (nickname, rating) -> BalanceService.Candidate(i + 1L, nickname, rating) }

    private fun split(players: List<BalanceService.Candidate>): Pair<List<Long>, List<Long>> {
        val (team1, team2) = service.splitTeams(players)
        return team1.map { it.personaId } to team2.map { it.personaId }
    }

    @Test
    fun `splitTeams balances eight players like the frontend`() {
        val players =
            candidates(
                "a1" to 1210,
                "a2" to 1105,
                "a3" to 1040,
                "a4" to 990,
                "a5" to 960,
                "a6" to 930,
                "a7" to 880,
                "a8" to 815,
            )
        assertEquals(listOf(1L, 4L, 5L, 8L) to listOf(2L, 3L, 6L, 7L), split(players))
    }

    @Test
    fun `splitTeams breaks rating ties by nickname, ignoring case`() {
        val players =
            candidates(
                "Bravo" to 1000,
                "alpha" to 1000,
                "charlie" to 950,
                "Delta" to 950,
                "echo" to 900,
                "Foxtrot" to 900,
                "golf" to 875,
                "Hotel" to 860,
                "india" to 1000,
                "Juliet" to 820,
            )
        assertEquals(listOf(2L, 3L, 5L, 6L, 7L) to listOf(1L, 9L, 4L, 8L, 10L), split(players))
    }

    @Test
    fun `splitTeams with two players puts the lower rated first`() {
        assertEquals(listOf(2L) to listOf(1L), split(candidates("x" to 1000, "y" to 900)))
    }

    @Test
    fun `splitTeams balances sixteen players like the frontend`() {
        val ratings = listOf(1187, 1143, 1121, 1098, 1077, 1062, 1040, 1013, 1001, 987, 964, 951, 930, 902, 877, 850)
        val players = ratings.mapIndexed { i, r -> BalanceService.Candidate(i + 1L, "p%02d".format(i + 1), r) }
        assertEquals(
            listOf(1L, 2L, 4L, 9L, 11L, 13L, 14L, 15L) to listOf(3L, 5L, 6L, 7L, 8L, 10L, 12L, 16L),
            split(players),
        )
    }

    private fun player(
        id: Long,
        personaId: Long,
        rating: Int,
        dzrating: Int = rating,
    ) = Player(id = id, nickname = "P$id", nation = "no", rating = rating, dzrating = dzrating, personaId = personaId)

    @Test
    fun `balance uses current season BR and returns persona IDs with averages`() {
        val players = listOf(player(1, 101, 80), player(2, 102, 70), player(3, 103, 60), player(4, 104, 50))
        `when`(playerRepository.findByPersonaIdIn(listOf(101L, 102L, 103L, 104L))).thenReturn(players)
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(3)
        `when`(rankedPlayerStatRepository.findBySeasonAndPlayerIdIn(3, listOf(1L, 2L, 3L, 4L))).thenReturn(
            listOf(
                RankedPlayerStat(playerId = 1, season = 3, br = 1200),
                RankedPlayerStat(playerId = 2, season = 3, br = 1100),
                RankedPlayerStat(playerId = 3, season = 3, br = 1000),
                RankedPlayerStat(playerId = 4, season = 3, br = 900),
            ),
        )

        val result = service.balance(BalanceRequest("br", listOf(101, 102, 103, 104)))

        assertEquals(listOf(101L, 104L), result.rebels)
        assertEquals(listOf(102L, 103L), result.imperials)
        assertEquals(1050.0, result.rebelAverage)
        assertEquals(1050.0, result.imperialAverage)
    }

    @Test
    fun `balance can use intern and Drop Zone ratings`() {
        val players = listOf(player(1, 101, 90, dzrating = 50), player(2, 102, 80, dzrating = 60))
        `when`(playerRepository.findByPersonaIdIn(listOf(101L, 102L))).thenReturn(players)

        assertEquals(80.0, service.balance(BalanceRequest("rating", listOf(101, 102))).rebelAverage)
        assertEquals(50.0, service.balance(BalanceRequest("dzrating", listOf(101, 102))).rebelAverage)
    }

    @Test
    fun `balance rejects unknown players and lists them`() {
        `when`(playerRepository.findByPersonaIdIn(listOf(101L, 999L))).thenReturn(listOf(player(1, 101, 80)))

        val ex = assertThrows<UnknownPlayersException> { service.balance(BalanceRequest("br", listOf(101, 999))) }
        assertEquals(listOf(999L), ex.unknown)
    }

    @Test
    fun `balance rejects players without BR this season`() {
        `when`(playerRepository.findByPersonaIdIn(listOf(101L, 102L))).thenReturn(listOf(player(1, 101, 80), player(2, 102, 70)))
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(1)
        `when`(rankedPlayerStatRepository.findBySeasonAndPlayerIdIn(1, listOf(1L, 2L)))
            .thenReturn(listOf(RankedPlayerStat(playerId = 1, season = 1, br = 1000)))

        val ex = assertThrows<IllegalArgumentException> { service.balance(BalanceRequest("br", listOf(101, 102))) }
        assertEquals("No BR this season for: P2", ex.message)
    }

    @Test
    fun `balance validates the request`() {
        assertThrows<IllegalArgumentException> { service.balance(BalanceRequest("elo", listOf(1, 2))) }
        assertThrows<IllegalArgumentException> { service.balance(BalanceRequest("br", listOf(1, 1))) }
        assertThrows<IllegalArgumentException> { service.balance(BalanceRequest("br", listOf(1, 2, 3))) }
        assertThrows<IllegalArgumentException> { service.balance(BalanceRequest("br", emptyList())) }
        assertThrows<IllegalArgumentException> { service.balance(BalanceRequest("br", (1L..34L).toList())) }
    }
}
