package no.battlefront.balancer.service

import no.battlefront.balancer.dto.MatchSubmitRequest
import no.battlefront.balancer.model.RankedMatch
import no.battlefront.balancer.model.RankedMatchStat
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedMatchRepository
import no.battlefront.balancer.repository.RankedMatchStatRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import no.battlefront.balancer.security.CurrentUserService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.springframework.security.access.AccessDeniedException

/**
 * JUnit test class for [MatchService].
 */
@Tag("MatchService")
class MatchServiceTest {

    private val rankedMatchRepository: RankedMatchRepository = mock(RankedMatchRepository::class.java)
    private val rankedMatchStatRepository: RankedMatchStatRepository =
        mock(RankedMatchStatRepository::class.java)
    private val playerRepository: PlayerRepository = mock(PlayerRepository::class.java)
    private val rankedPlayerStatRepository: RankedPlayerStatRepository =
        mock(RankedPlayerStatRepository::class.java)
    private val currentSeasonRepository: CurrentSeasonRepository = mock(CurrentSeasonRepository::class.java)
    private val currentUserService: CurrentUserService = mock(CurrentUserService::class.java)
    private val service = MatchService(
        rankedMatchRepository = rankedMatchRepository,
        rankedMatchStatRepository = rankedMatchStatRepository,
        playerRepository = playerRepository,
        rankedPlayerStatRepository = rankedPlayerStatRepository,
        currentSeasonRepository = currentSeasonRepository,
        currentUserService = currentUserService
    )

    /**
     * Test that submitMatch throws AccessDeniedException when no user is authenticated.
     *
     * 1. Arrange: Mock currentUserService to return null.
     * 2. Act: Call submitMatch with valid-shaped request.
     * 3. Assert: Verify AccessDeniedException is thrown.
     */
    @Test
    fun `submitMatch throws when not authenticated`() {
        `when`(currentUserService.currentUserId()).thenReturn(null)
        val request = MatchSubmitRequest(
            matchData = listOf("Map", 4, 0L, 3, 2, "Rule"),
            rebels = emptyList(),
            imperials = emptyList()
        )
        assertThrows<AccessDeniedException> { service.submitMatch(request) }
    }

    /**
     * Test that submitMatch throws when matchData has fewer than 6 elements.
     *
     * 1. Arrange: Mock authenticated user; request with matchData size < 6.
     * 2. Act: Call submitMatch.
     * 3. Assert: Verify IllegalArgumentException is thrown.
     */
    @Test
    fun `submitMatch throws when matchData has fewer than 6 elements`() {
        `when`(currentUserService.currentUserId()).thenReturn(100L)
        val request = MatchSubmitRequest(
            matchData = listOf("Map", 4),
            rebels = emptyList(),
            imperials = emptyList()
        )
        assertThrows<IllegalArgumentException> { service.submitMatch(request) }
    }

    /**
     * Test that getPlayersInLastMatch returns an empty list when no match exists.
     *
     * 1. Arrange: Mock repository to return null for latest match.
     * 2. Act: Call getPlayersInLastMatch().
     * 3. Assert: Verify result is empty.
     */
    @Test
    fun `getPlayersInLastMatch returns empty list when no match exists`() {
        `when`(rankedMatchRepository.findTop1ByOrderByIdDesc()).thenReturn(null)
        val result = service.getPlayersInLastMatch()
        assertTrue(result.isEmpty())
    }

    /**
     * Test that getPlayersInLastMatch returns players with stats when a match and stats exist.
     *
     * 1. Arrange: Mock latest match, match stats, player and season stats.
     * 2. Act: Call getPlayersInLastMatch().
     * 3. Assert: Verify one DTO with correct nickname and br.
     */
    @Test
    fun `getPlayersInLastMatch returns players when match and stats exist`() {
        val match = RankedMatch(
            id = 1L,
            map = "Imperial Station",
            rule = "DACE",
            season = 1,
            teamSize = 4,
            rebelScore = 3,
            imperialScore = 2,
            mvpId = 10L,
            supervisorId = 1L
        )
        val stat = RankedMatchStat(
            id = 1L,
            playerId = 10L,
            matchId = 1L,
            season = 1,
            faction = "Rebel",
            result = "Won",
            score = 100,
            perf = 1.2,
            updateBr = 25,
            newBr = 925
        )
        val player = no.battlefront.balancer.model.Player(
            id = 10L,
            nickname = "Player1",
            nation = "no",
            rating = 80,
            dzrating = 80,
            elo = 900
        )
        val pstat = no.battlefront.balancer.model.RankedPlayerStat(
            id = 1L,
            playerId = 10L,
            season = 1,
            br = 925,
            best = 950,
            played = 6,
            won = 4,
            lost = 1,
            draw = 1,
            score = 150,
            mvp = 1
        )
        `when`(rankedMatchRepository.findTop1ByOrderByIdDesc()).thenReturn(match)
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(1)
        `when`(rankedMatchStatRepository.findByMatchId(1L)).thenReturn(listOf(stat))
        `when`(playerRepository.findById(10L)).thenReturn(java.util.Optional.of(player))
        `when`(rankedPlayerStatRepository.findByPlayerIdAndSeason(10L, 1)).thenReturn(pstat)

        val result = service.getPlayersInLastMatch()

        assertEquals(1, result.size)
        assertEquals("Player1", result[0].nickname)
        assertEquals(925, result[0].br)
    }
}
