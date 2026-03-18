package no.battlefront.balancer.service

import no.battlefront.balancer.dto.PlayerCreateRequest
import no.battlefront.balancer.dto.PlayerUpdateRequest
import no.battlefront.balancer.model.Player
import no.battlefront.balancer.model.RankedPlayerStat
import no.battlefront.balancer.repository.CurrentSeasonRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

/**
 * JUnit test class for [PlayerService].
 */
@Tag("PlayerService")
class PlayerServiceTest {
    private val playerRepository: PlayerRepository = mock(PlayerRepository::class.java)
    private val rankedPlayerStatRepository: RankedPlayerStatRepository =
        mock(RankedPlayerStatRepository::class.java)
    private val currentSeasonRepository: CurrentSeasonRepository = mock(CurrentSeasonRepository::class.java)
    private val service = PlayerService(playerRepository, rankedPlayerStatRepository, currentSeasonRepository)

    /**
     * Test that getPlayersWithCurrentSeasonStats returns an empty list when there are no stats for the current season.
     *
     * 1. Arrange: Mock current season and empty stats list.
     * 2. Act: Call getPlayersWithCurrentSeasonStats().
     * 3. Assert: Verify result is empty.
     */
    @Test
    fun `getPlayersWithCurrentSeasonStats returns empty list when no stats for season`() {
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(1)
        `when`(rankedPlayerStatRepository.findBySeason(1)).thenReturn(emptyList())

        val result = service.getPlayersWithCurrentSeasonStats()

        assertEquals(0, result.size)
    }

    /**
     * Test that getPlayersWithCurrentSeasonStats returns players with their season stats when data exists.
     *
     * 1. Arrange: Mock season, one stat row and corresponding player.
     * 2. Act: Call getPlayersWithCurrentSeasonStats().
     * 3. Assert: Verify one DTO with correct nickname, br and played.
     */
    @Test
    fun `getPlayersWithCurrentSeasonStats returns players with stats when data exists`() {
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(1)
        val player = Player(id = 10L, nickname = "Test", nation = "no", rating = 80, dzrating = 80, elo = 900)
        val stat =
            RankedPlayerStat(
                id = 1L,
                playerId = 10L,
                season = 1,
                br = 900,
                best = 950,
                played = 5,
                won = 3,
                lost = 1,
                draw = 1,
                score = 100,
                mvp = 1,
            )
        `when`(rankedPlayerStatRepository.findBySeason(1)).thenReturn(listOf(stat))
        `when`(playerRepository.findById(10L)).thenReturn(java.util.Optional.of(player))

        val result = service.getPlayersWithCurrentSeasonStats()

        assertEquals(1, result.size)
        assertEquals("Test", result[0].nickname)
        assertEquals(900, result[0].br)
        assertEquals(5, result[0].played)
    }

    /**
     * Test that createPlayer persists a new player and initial season stats and returns the saved player.
     *
     * 1. Arrange: Mock current season and repository save to return a player.
     * 2. Act: Call createPlayer with valid request.
     * 3. Assert: Verify returned player fields and that season stats were saved.
     */
    @Test
    fun `createPlayer succeeds and returns saved player with initial stats`() {
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(1)
        val savedPlayer =
            Player(
                id = 1L,
                nickname = "NewPlayer",
                nation = "no",
                rating = 65,
                dzrating = 65,
                elo = 0,
            )
        `when`(playerRepository.save(any(Player::class.java))).thenReturn(savedPlayer)
        `when`(rankedPlayerStatRepository.save(any(RankedPlayerStat::class.java))).thenAnswer { it.getArgument(0) }

        val request = PlayerCreateRequest(nickname = "NewPlayer", nation = "no", rating = 65)
        val result = service.createPlayer(request)

        assertEquals(1L, result.id)
        assertEquals("NewPlayer", result.nickname)
        assertEquals("no", result.nation)
        assertEquals(65, result.rating)
        verify(rankedPlayerStatRepository).save(any(RankedPlayerStat::class.java))
    }

    /**
     * Test that createPlayer throws when nickname is blank.
     *
     * 1. Act: Call createPlayer with nickname containing only whitespace.
     * 2. Assert: Verify IllegalArgumentException is thrown.
     */
    @Test
    fun `createPlayer throws when nickname is empty`() {
        val request = PlayerCreateRequest(nickname = "  ", nation = "no", rating = 50)
        assertThrows<IllegalArgumentException> { service.createPlayer(request) }
    }

    /**
     * Test that createPlayer throws when rating is outside 1–99.
     *
     * 1. Act: Call createPlayer with rating 0 and with rating 100.
     * 2. Assert: Verify IllegalArgumentException is thrown in both cases.
     */
    @Test
    fun `createPlayer throws when rating is out of range`() {
        assertThrows<IllegalArgumentException> {
            service.createPlayer(PlayerCreateRequest(nickname = "X", nation = "no", rating = 0))
        }
        assertThrows<IllegalArgumentException> {
            service.createPlayer(PlayerCreateRequest(nickname = "X", nation = "no", rating = 100))
        }
    }

    /**
     * Test that createPlayer throws when nation is not exactly two characters.
     *
     * 1. Act: Call createPlayer with nation "nor" and with nation "n".
     * 2. Assert: Verify IllegalArgumentException is thrown in both cases.
     */
    @Test
    fun `createPlayer throws when nation is not 2 letters`() {
        assertThrows<IllegalArgumentException> {
            service.createPlayer(PlayerCreateRequest(nickname = "X", nation = "nor", rating = 50))
        }
        assertThrows<IllegalArgumentException> {
            service.createPlayer(PlayerCreateRequest(nickname = "X", nation = "n", rating = 50))
        }
    }

    /**
     * Test that updatePlayer updates player profile and season BR successfully.
     *
     * 1. Arrange: Mock existing player, season stats and repository save.
     * 2. Act: Call updatePlayer with valid request.
     * 3. Assert: Verify returned player and updated BR on stats.
     */
    @Test
    fun `updatePlayer succeeds and updates BR`() {
        val player = Player(id = 2L, nickname = "Old", nation = "us", rating = 70, dzrating = 70, elo = 850)
        val stats =
            RankedPlayerStat(
                id = 1L,
                playerId = 2L,
                season = 1,
                br = 850,
                best = 900,
                played = 10,
                won = 5,
                lost = 3,
                draw = 2,
                score = 200,
                mvp = 0,
            )
        `when`(playerRepository.findById(2L)).thenReturn(java.util.Optional.of(player))
        `when`(playerRepository.save(any(Player::class.java))).thenAnswer { it.getArgument(0) }
        `when`(currentSeasonRepository.findCurrentSeason()).thenReturn(1)
        `when`(rankedPlayerStatRepository.findByPlayerIdAndSeason(2L, 1)).thenReturn(stats)
        `when`(rankedPlayerStatRepository.save(any(RankedPlayerStat::class.java))).thenAnswer { it.getArgument(0) }

        val request =
            PlayerUpdateRequest(
                nickname = "Updated",
                nation = "no",
                rating = 72,
                dzrating = 72,
                br = 900,
            )
        val result = service.updatePlayer(2L, request)

        assertEquals("Updated", result.nickname)
        assertEquals("no", result.nation)
        assertEquals(900, stats.br)
    }

    /**
     * Test that updatePlayer throws when the player id does not exist.
     *
     * 1. Arrange: Mock findById to return empty.
     * 2. Act: Call updatePlayer with non-existent id.
     * 3. Assert: Verify IllegalArgumentException is thrown.
     */
    @Test
    fun `updatePlayer throws when player not found`() {
        `when`(playerRepository.findById(999L)).thenReturn(java.util.Optional.empty())
        val request = PlayerUpdateRequest("X", "no", 50, 50, 800)
        assertThrows<IllegalArgumentException> { service.updatePlayer(999L, request) }
    }

    /**
     * Test that deletePlayer does not call delete when the player does not exist.
     *
     * 1. Arrange: Mock existsById to return false.
     * 2. Act: Call deletePlayer.
     * 3. Assert: Verify existsById was called; deleteById must not be called.
     */
    @Test
    fun `deletePlayer does nothing when player does not exist`() {
        `when`(playerRepository.existsById(99L)).thenReturn(false)
        service.deletePlayer(99L)
        verify(playerRepository).existsById(99L)
    }

    /**
     * Test that deletePlayer deletes the player when they exist.
     *
     * 1. Arrange: Mock existsById to return true.
     * 2. Act: Call deletePlayer.
     * 3. Assert: Verify deleteById was called with the id.
     */
    @Test
    fun `deletePlayer deletes when player exists`() {
        `when`(playerRepository.existsById(1L)).thenReturn(true)
        service.deletePlayer(1L)
        verify(playerRepository).deleteById(1L)
    }
}
