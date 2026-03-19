package no.battlefront.balancer.service

import no.battlefront.balancer.dto.MatchSubmitRequest
import no.battlefront.balancer.dto.PlayerMatchStatDto
import no.battlefront.balancer.model.Player
import no.battlefront.balancer.model.RankedMatch
import no.battlefront.balancer.model.RankedMatchStat
import no.battlefront.balancer.model.RankedPlayerStat
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
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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
    private val service =
        MatchService(
            rankedMatchRepository = rankedMatchRepository,
            rankedMatchStatRepository = rankedMatchStatRepository,
            playerRepository = playerRepository,
            rankedPlayerStatRepository = rankedPlayerStatRepository,
            currentSeasonRepository = currentSeasonRepository,
            currentUserService = currentUserService,
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
        val request =
            MatchSubmitRequest(
                matchData = listOf("Map", 4, 0L, 3, 2, "Rule"),
                rebels = emptyList(),
                imperials = emptyList(),
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
        val request =
            MatchSubmitRequest(
                matchData = listOf("Map", 4),
                rebels = emptyList(),
                imperials = emptyList(),
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
        val match =
            RankedMatch(
                id = 1L,
                map = "Imperial Station",
                rule = "DACE",
                season = 1,
                teamSize = 4,
                rebelScore = 3,
                imperialScore = 2,
                mvpId = 10L,
                supervisorId = 1L,
            )
        val stat =
            RankedMatchStat(
                id = 1L,
                playerId = 10L,
                matchId = 1L,
                season = 1,
                faction = "Rebel",
                result = "Won",
                score = 100,
                perf = 1.2,
                updateBr = 25,
                newBr = 925,
            )
        val player =
            Player(
                id = 10L,
                nickname = "Player1",
                nation = "no",
                rating = 80,
                dzrating = 80,
                elo = 900,
            )
        val pstat =
            RankedPlayerStat(
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
                mvp = 1,
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

    @Test
    fun `submitMatch persists match and updates player stats for Won and Lost with MVP`() {
        // Arrange
        `when`(currentUserService.currentUserId()).thenReturn(10L)
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(2)
        `when`(rankedMatchRepository.save(org.mockito.ArgumentMatchers.any(RankedMatch::class.java))).thenReturn(RankedMatch(id = 123L))

        val mvpId = 999L
        val season = 2

        val rebelId = 1L
        val imperialId = 999L

        val rebelPstat =
            RankedPlayerStat(
                id = 1L,
                playerId = rebelId,
                season = season,
                br = 800,
                best = 850,
                played = 1,
                won = 0,
                lost = 0,
                draw = 0,
                score = 10,
                mvp = 0,
            )
        val imperialPstat =
            RankedPlayerStat(
                id = 2L,
                playerId = imperialId,
                season = season,
                br = 820,
                best = 900,
                played = 2,
                won = 1,
                lost = 0,
                draw = 0,
                score = 30,
                mvp = 0,
            )

        `when`(rankedPlayerStatRepository.findByPlayerIdAndSeason(rebelId, season)).thenReturn(rebelPstat)
        `when`(rankedPlayerStatRepository.findByPlayerIdAndSeason(imperialId, season)).thenReturn(imperialPstat)

        val request =
            MatchSubmitRequest(
                matchData = listOf("Imperial Station", 4, mvpId, 3, 2, "DSE"),
                rebels =
                    listOf(
                        PlayerMatchStatDto(
                            id = rebelId,
                            faction = "Rebel",
                            outcome = "Won",
                            score = 100,
                            perf = 1.2,
                            change = 25,
                            newBR = 900,
                        ),
                    ),
                imperials =
                    listOf(
                        PlayerMatchStatDto(
                            id = imperialId,
                            faction = "Imperial",
                            outcome = "Lost",
                            score = 50,
                            perf = 0.8,
                            change = -10,
                            newBR = 890,
                        ),
                    ),
            )

        val matchCaptor = ArgumentCaptor.forClass(RankedMatch::class.java)
        val statCaptor = ArgumentCaptor.forClass(RankedMatchStat::class.java)
        val pstatCaptor = ArgumentCaptor.forClass(RankedPlayerStat::class.java)

        // Act
        service.submitMatch(request)

        // Assert
        verify(rankedMatchRepository).save(matchCaptor.capture())
        assertEquals(10L, matchCaptor.value.supervisorId)
        assertEquals(mvpId, matchCaptor.value.mvpId)
        assertEquals("Imperial Station", matchCaptor.value.map)
        assertEquals("DSE", matchCaptor.value.rule)
        assertEquals(season, matchCaptor.value.season)

        verify(rankedMatchStatRepository, times(2)).save(statCaptor.capture())
        val statsByPlayer = statCaptor.allValues.associateBy { it.playerId }
        assertEquals(rebelId, statsByPlayer[rebelId]!!.playerId)
        assertEquals(imperialId, statsByPlayer[imperialId]!!.playerId)
        assertEquals(123L, statsByPlayer[rebelId]!!.matchId)
        assertEquals(season, statsByPlayer[rebelId]!!.season)

        // BR/best update + win/loss mapping + MVP
        verify(rankedPlayerStatRepository, times(2)).save(pstatCaptor.capture())
        val pstatsByPlayer = pstatCaptor.allValues.associateBy { it.playerId }
        val updatedRebel = pstatsByPlayer[rebelId]!!
        assertEquals(900, updatedRebel.br)
        assertEquals(900, updatedRebel.best) // maxOf(850, 900)
        assertEquals(2, updatedRebel.played)
        assertEquals(1, updatedRebel.won)
        assertEquals(0, updatedRebel.lost)
        assertEquals(0, updatedRebel.draw)
        assertEquals(110, updatedRebel.score) // 10 + 100
        assertEquals(0, updatedRebel.mvp)

        val updatedImperial = pstatsByPlayer[imperialId]!!
        assertEquals(890, updatedImperial.br)
        assertEquals(900, updatedImperial.best) // maxOf(900, 890)
        assertEquals(3, updatedImperial.played)
        assertEquals(1, updatedImperial.won) // still had 1 before; increment by outcome Lost does not touch won
        assertEquals(1, updatedImperial.lost) // increment by 1
        assertEquals(0, updatedImperial.draw)
        assertEquals(80, updatedImperial.score) // 30 + 50
        assertEquals(1, updatedImperial.mvp) // MVP increment
    }

    @Test
    fun `submitMatch skips updating season stats when RankedPlayerStat missing`() {
        // Arrange
        `when`(currentUserService.currentUserId()).thenReturn(10L)
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(1)
        `when`(rankedMatchRepository.save(org.mockito.ArgumentMatchers.any(RankedMatch::class.java))).thenReturn(RankedMatch(id = 77L))

        val missingPlayerId = 5L
        `when`(rankedPlayerStatRepository.findByPlayerIdAndSeason(missingPlayerId, 1)).thenReturn(null)

        val request =
            MatchSubmitRequest(
                matchData = listOf("Map", 4, 0L, 1, 1, "DSE"),
                rebels =
                    listOf(
                        PlayerMatchStatDto(
                            id = missingPlayerId,
                            faction = "Rebel",
                            outcome = "Won",
                            score = 10,
                            perf = 1.0,
                            change = 5,
                            newBR = 20,
                        ),
                    ),
                imperials = emptyList(),
            )

        // Act
        service.submitMatch(request)

        // Assert
        // match stats row is still persisted, but aggregated season stats are skipped
        verify(rankedMatchStatRepository, times(1)).save(org.mockito.ArgumentMatchers.any(RankedMatchStat::class.java))
        verify(rankedPlayerStatRepository, never()).save(org.mockito.ArgumentMatchers.any(RankedPlayerStat::class.java))
    }

    @Test
    fun `submitMatch handles Draw and unknown outcome and uses default season when repository returns null`() {
        // Arrange
        `when`(currentUserService.currentUserId()).thenReturn(10L)
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(null)
        `when`(rankedMatchRepository.save(org.mockito.ArgumentMatchers.any(RankedMatch::class.java))).thenReturn(RankedMatch(id = 55L))

        // mvpIdRaw = 0 => mvpId = null => mvp always 0
        val season = 1
        val drawPlayerId = 1L
        val unknownPlayerId = 2L

        val drawPstat =
            RankedPlayerStat(
                id = 1L,
                playerId = drawPlayerId,
                season = season,
                br = 100,
                best = 100,
                played = 0,
                won = 0,
                lost = 0,
                draw = 0,
                score = 0,
                mvp = 0,
            )
        val unknownPstat =
            RankedPlayerStat(
                id = 2L,
                playerId = unknownPlayerId,
                season = season,
                br = 200,
                best = 300,
                played = 3,
                won = 1,
                lost = 1,
                draw = 1,
                score = 50,
                mvp = 0,
            )

        `when`(rankedPlayerStatRepository.findByPlayerIdAndSeason(drawPlayerId, season)).thenReturn(drawPstat)
        `when`(rankedPlayerStatRepository.findByPlayerIdAndSeason(unknownPlayerId, season)).thenReturn(unknownPstat)

        val request =
            MatchSubmitRequest(
                matchData = listOf("Map", 4, 0L, 3, 2, "DSE"),
                rebels =
                    listOf(
                        PlayerMatchStatDto(
                            id = drawPlayerId,
                            faction = "Rebel",
                            outcome = "Draw",
                            score = 10,
                            perf = 1.0,
                            change = 1,
                            newBR = 110,
                        ),
                    ),
                imperials =
                    listOf(
                        PlayerMatchStatDto(
                            id = unknownPlayerId,
                            faction = "Imperial",
                            outcome = "Weird",
                            score = 20,
                            perf = 2.0,
                            change = -5,
                            newBR = 190,
                        ),
                    ),
            )

        val pstatCaptor = ArgumentCaptor.forClass(RankedPlayerStat::class.java)
        val matchCaptor = ArgumentCaptor.forClass(RankedMatch::class.java)

        // Act
        service.submitMatch(request)

        // Assert
        verify(rankedMatchRepository).save(matchCaptor.capture())
        assertEquals(season, matchCaptor.value.season) // default season used

        verify(rankedPlayerStatRepository, times(2)).save(pstatCaptor.capture())
        val pstatsByPlayer = pstatCaptor.allValues.associateBy { it.playerId }

        val updatedDraw = pstatsByPlayer[drawPlayerId]!!
        assertEquals(110, updatedDraw.br)
        assertEquals(110, updatedDraw.best) // maxOf(100, 110)
        assertEquals(1, updatedDraw.draw)
        assertEquals(0, updatedDraw.won)
        assertEquals(0, updatedDraw.lost)
        assertEquals(10, updatedDraw.score)
        assertEquals(0, updatedDraw.mvp)

        val updatedUnknown = pstatsByPlayer[unknownPlayerId]!!
        assertEquals(190, updatedUnknown.br)
        assertEquals(300, updatedUnknown.best) // maxOf(300, 190)
        assertEquals(0, updatedUnknown.won - 1) // won unchanged
        assertEquals(0, updatedUnknown.lost - 1) // lost unchanged
        assertEquals(0, updatedUnknown.draw - 1) // draw unchanged
        assertEquals(70, updatedUnknown.score) // 50 + 20
        assertEquals(0, updatedUnknown.mvp)
    }

    @Test
    fun `getPlayersInLastMatch skips players when RankedPlayerStat is missing`() {
        // Arrange
        val match =
            RankedMatch(
                id = 1L,
                map = "Imperial Station",
                rule = "DACE",
                season = 1,
                teamSize = 4,
                rebelScore = 3,
                imperialScore = 2,
                mvpId = 10L,
                supervisorId = 1L,
            )
        val stat =
            RankedMatchStat(
                id = 1L,
                playerId = 10L,
                matchId = 1L,
                season = 1,
                faction = "Rebel",
                result = "Won",
                score = 100,
                perf = 1.2,
                updateBr = 25,
                newBr = 925,
            )
        val player =
            Player(
                id = 10L,
                nickname = "Player1",
                nation = "no",
                rating = 80,
                dzrating = 80,
                elo = 900,
            )

        `when`(rankedMatchRepository.findTop1ByOrderByIdDesc()).thenReturn(match)
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(1)
        `when`(rankedMatchStatRepository.findByMatchId(1L)).thenReturn(listOf(stat))
        `when`(playerRepository.findById(10L)).thenReturn(java.util.Optional.of(player))
        // covers: `?: return@mapNotNull null`
        `when`(rankedPlayerStatRepository.findByPlayerIdAndSeason(10L, 1)).thenReturn(null)

        // Act
        val result = service.getPlayersInLastMatch()

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getPlayersInLastMatch skips players when player is missing and uses default season`() {
        // Arrange
        val match =
            RankedMatch(
                id = 1L,
                map = "Imperial Station",
                rule = "DACE",
                season = 1,
                teamSize = 4,
                rebelScore = 3,
                imperialScore = 2,
                mvpId = 10L,
                supervisorId = 1L,
            )
        val stat =
            RankedMatchStat(
                id = 1L,
                playerId = 10L,
                matchId = 1L,
                season = 1,
                faction = "Rebel",
                result = "Won",
                score = 100,
                perf = 1.2,
                updateBr = 25,
                newBr = 925,
            )

        `when`(rankedMatchRepository.findTop1ByOrderByIdDesc()).thenReturn(match)
        // covers: `?: 1` in getPlayersInLastMatch
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(null)
        `when`(rankedMatchStatRepository.findByMatchId(1L)).thenReturn(listOf(stat))
        // covers: `?: return@mapNotNull null` when player is missing
        `when`(playerRepository.findById(10L)).thenReturn(java.util.Optional.empty())

        // Act
        val result = service.getPlayersInLastMatch()

        // Assert
        assertTrue(result.isEmpty())
        verify(
            rankedPlayerStatRepository,
            never(),
        ).findByPlayerIdAndSeason(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt())
    }
}
