package no.battlefront.balancer.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * JUnit test class for the [no.battlefront.balancer.model] package.
 *
 * Exercises all model classes and their properties so that Kover reports full coverage.
 * JPA entities are data holders; each test ensures constructors, field initializers and
 * property get/set are executed.
 */
@Tag("Model")
class ModelTest {

    /**
     * Test [User] constructor with arguments and property get/set.
     *
     * 1. Act: Create User with id, username, password, role; then update var properties.
     * 2. Assert: Verify all values before and after update.
     */
    @Test
    fun `User constructor and properties`() {
        val u = User(id = 1L, username = "admin", password = "hash", role = "admin")
        assertEquals(1L, u.id)
        assertEquals("admin", u.username)
        assertEquals("hash", u.password)
        assertEquals("admin", u.role)
        u.username = "supervisor"
        u.password = "newhash"
        u.role = "supervisor"
        assertEquals("supervisor", u.username)
        assertEquals("newhash", u.password)
        assertEquals("supervisor", u.role)
    }

    /**
     * Test [User] default constructor and default field values.
     *
     * 1. Act: Create User with no arguments.
     * 2. Assert: Verify id is 0 and string fields are empty.
     */
    @Test
    fun `User default constructor`() {
        val u = User()
        assertEquals(0L, u.id)
        assertEquals("", u.username)
        assertEquals("", u.password)
        assertEquals("", u.role)
    }

    /**
     * Test [CurrentSeason] constructor and property get/set.
     *
     * 1. Act: Create CurrentSeason with id and season; then update season.
     * 2. Assert: Verify values.
     */
    @Test
    fun `CurrentSeason constructor and properties`() {
        val c = CurrentSeason(id = 1, season = 2)
        assertEquals(1, c.id)
        assertEquals(2, c.season)
        c.season = 3
        assertEquals(3, c.season)
    }

    /**
     * Test [CurrentSeason] default constructor.
     *
     * 1. Act: Create CurrentSeason with no arguments.
     * 2. Assert: Verify id and season are 0.
     */
    @Test
    fun `CurrentSeason default constructor`() {
        val c = CurrentSeason()
        assertEquals(0, c.id)
        assertEquals(0, c.season)
    }

    /**
     * Test [Player] constructor with arguments and property get/set.
     *
     * 1. Act: Create Player; read all fields; update var properties; read again.
     * 2. Assert: Verify all values.
     */
    @Test
    fun `Player constructor and properties`() {
        val p = Player(
            id = 1L,
            nickname = "Test",
            nation = "no",
            rating = 80,
            dzrating = 80,
            elo = 900
        )
        assertEquals(1L, p.id)
        assertEquals("Test", p.nickname)
        assertEquals("no", p.nation)
        assertEquals(80, p.rating)
        assertEquals(80, p.dzrating)
        assertEquals(900, p.elo)
        p.nickname = "Updated"
        p.nation = "us"
        p.rating = 85
        p.dzrating = 85
        p.elo = 950
        assertEquals("Updated", p.nickname)
        assertEquals("us", p.nation)
        assertEquals(85, p.rating)
        assertEquals(85, p.dzrating)
        assertEquals(950, p.elo)
    }

    /**
     * Test [Player] default constructor.
     *
     * 1. Act: Create Player with no arguments.
     * 2. Assert: Verify id 0, empty strings and zero numeric fields.
     */
    @Test
    fun `Player default constructor`() {
        val p = Player()
        assertEquals(0L, p.id)
        assertEquals("", p.nickname)
        assertEquals("", p.nation)
        assertEquals(0, p.rating)
        assertEquals(0, p.dzrating)
        assertEquals(0, p.elo)
    }

    /**
     * Test [RankedMatch] constructor and property get/set including nullable mvpId and date.
     *
     * 1. Act: Create RankedMatch with all args; read fields; update vars including mvpId = null.
     * 2. Assert: Verify all values.
     */
    @Test
    fun `RankedMatch constructor and properties`() {
        val d = LocalDateTime.of(2025, 1, 15, 12, 0)
        val m = RankedMatch(
            id = 1L,
            map = "Jawa Refuge",
            rule = "DACE",
            season = 1,
            teamSize = 4,
            rebelScore = 3,
            imperialScore = 2,
            mvpId = 10L,
            supervisorId = 5L,
            date = d
        )
        assertEquals(1L, m.id)
        assertEquals("Jawa Refuge", m.map)
        assertEquals("DACE", m.rule)
        assertEquals(1, m.season)
        assertEquals(4, m.teamSize)
        assertEquals(3, m.rebelScore)
        assertEquals(2, m.imperialScore)
        assertEquals(10L, m.mvpId)
        assertEquals(5L, m.supervisorId)
        assertEquals(d, m.date)
        m.map = "Ice Caves"
        m.rule = "DSE"
        m.season = 2
        m.teamSize = 5
        m.rebelScore = 4
        m.imperialScore = 3
        m.mvpId = null
        m.supervisorId = 6L
        val d2 = LocalDateTime.now()
        m.date = d2
        assertEquals("Ice Caves", m.map)
        assertEquals("DSE", m.rule)
        assertEquals(2, m.season)
        assertEquals(5, m.teamSize)
        assertEquals(4, m.rebelScore)
        assertEquals(3, m.imperialScore)
        assertNull(m.mvpId)
        assertEquals(6L, m.supervisorId)
        assertEquals(d2, m.date)
    }

    /**
     * Test [RankedMatch] default constructor.
     *
     * 1. Act: Create RankedMatch with no arguments.
     * 2. Assert: Verify default id, empty strings, zero numbers and null mvpId.
     */
    @Test
    fun `RankedMatch default constructor`() {
        val m = RankedMatch()
        assertEquals(0L, m.id)
        assertEquals("", m.map)
        assertEquals("", m.rule)
        assertEquals(0, m.season)
        assertEquals(0, m.teamSize)
        assertEquals(0, m.rebelScore)
        assertEquals(0, m.imperialScore)
        assertNull(m.mvpId)
        assertEquals(0L, m.supervisorId)
    }

    /**
     * Test [RankedMatchStat] constructor and property get/set.
     *
     * 1. Act: Create RankedMatchStat; read all fields; update all var properties; read again.
     * 2. Assert: Verify all values.
     */
    @Test
    fun `RankedMatchStat constructor and properties`() {
        val s = RankedMatchStat(
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
        assertEquals(1L, s.id)
        assertEquals(10L, s.playerId)
        assertEquals(1L, s.matchId)
        assertEquals(1, s.season)
        assertEquals("Rebel", s.faction)
        assertEquals("Won", s.result)
        assertEquals(100, s.score)
        assertEquals(1.2, s.perf)
        assertEquals(25, s.updateBr)
        assertEquals(925, s.newBr)
        s.playerId = 11L
        s.matchId = 2L
        s.season = 2
        s.faction = "Imperial"
        s.result = "Lost"
        s.score = 50
        s.perf = 0.8
        s.updateBr = -15
        s.newBr = 910
        assertEquals(11L, s.playerId)
        assertEquals(2L, s.matchId)
        assertEquals(2, s.season)
        assertEquals("Imperial", s.faction)
        assertEquals("Lost", s.result)
        assertEquals(50, s.score)
        assertEquals(0.8, s.perf)
        assertEquals(-15, s.updateBr)
        assertEquals(910, s.newBr)
    }

    /**
     * Test [RankedMatchStat] default constructor.
     *
     * 1. Act: Create RankedMatchStat with no arguments.
     * 2. Assert: Verify all default values.
     */
    @Test
    fun `RankedMatchStat default constructor`() {
        val s = RankedMatchStat()
        assertEquals(0L, s.id)
        assertEquals(0L, s.playerId)
        assertEquals(0L, s.matchId)
        assertEquals(0, s.season)
        assertEquals("", s.faction)
        assertEquals("", s.result)
        assertEquals(0, s.score)
        assertEquals(0.0, s.perf)
        assertEquals(0, s.updateBr)
        assertEquals(0, s.newBr)
    }

    /**
     * Test [RankedPlayerStat] constructor and property get/set.
     *
     * 1. Act: Create RankedPlayerStat; read fields; update all var properties; read again.
     * 2. Assert: Verify all values.
     */
    @Test
    fun `RankedPlayerStat constructor and properties`() {
        val s = RankedPlayerStat(
            id = 1L,
            playerId = 10L,
            season = 1,
            br = 900,
            best = 950,
            played = 10,
            won = 5,
            lost = 3,
            draw = 2,
            score = 200,
            mvp = 1
        )
        assertEquals(1L, s.id)
        assertEquals(10L, s.playerId)
        assertEquals(1, s.season)
        assertEquals(900, s.br)
        assertEquals(950, s.best)
        assertEquals(10, s.played)
        assertEquals(5, s.won)
        assertEquals(3, s.lost)
        assertEquals(2, s.draw)
        assertEquals(200, s.score)
        assertEquals(1, s.mvp)
        s.playerId = 11L
        s.season = 2
        s.br = 925
        s.best = 975
        s.played = 11
        s.won = 6
        s.lost = 3
        s.draw = 2
        s.score = 220
        s.mvp = 2
        assertEquals(11L, s.playerId)
        assertEquals(2, s.season)
        assertEquals(925, s.br)
        assertEquals(975, s.best)
        assertEquals(11, s.played)
        assertEquals(6, s.won)
        assertEquals(3, s.lost)
        assertEquals(2, s.draw)
        assertEquals(220, s.score)
        assertEquals(2, s.mvp)
    }

    /**
     * Test [RankedPlayerStat] default constructor.
     *
     * 1. Act: Create RankedPlayerStat with no arguments.
     * 2. Assert: Verify all default values.
     */
    @Test
    fun `RankedPlayerStat default constructor`() {
        val s = RankedPlayerStat()
        assertEquals(0L, s.id)
        assertEquals(0L, s.playerId)
        assertEquals(0, s.season)
        assertEquals(0, s.br)
        assertEquals(0, s.best)
        assertEquals(0, s.played)
        assertEquals(0, s.won)
        assertEquals(0, s.lost)
        assertEquals(0, s.draw)
        assertEquals(0, s.score)
        assertEquals(0, s.mvp)
    }

    /**
     * Test [Randomizer] constructor and property get/set.
     *
     * 1. Act: Create Randomizer; read fields; update map and rule; read again.
     * 2. Assert: Verify all values.
     */
    @Test
    fun `Randomizer constructor and properties`() {
        val r = Randomizer(id = 1L, map = "Imperial Station", rule = "DSE")
        assertEquals(1L, r.id)
        assertEquals("Imperial Station", r.map)
        assertEquals("DSE", r.rule)
        r.map = "Imperial Hangar"
        r.rule = "DACE"
        assertEquals("Imperial Hangar", r.map)
        assertEquals("DACE", r.rule)
    }

    /**
     * Test [Randomizer] default constructor.
     *
     * 1. Act: Create Randomizer with no arguments.
     * 2. Assert: Verify id 0 and empty map and rule.
     */
    @Test
    fun `Randomizer default constructor`() {
        val r = Randomizer()
        assertEquals(0L, r.id)
        assertEquals("", r.map)
        assertEquals("", r.rule)
    }
}
