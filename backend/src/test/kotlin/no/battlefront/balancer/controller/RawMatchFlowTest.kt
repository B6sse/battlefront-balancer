package no.battlefront.balancer.controller

import no.battlefront.balancer.dto.HostTokenCreateRequest
import no.battlefront.balancer.model.Player
import no.battlefront.balancer.model.RankedPlayerStat
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.HostTokenRepository
import no.battlefront.balancer.repository.PlayerRepository
import no.battlefront.balancer.repository.RandomizerRepository
import no.battlefront.balancer.repository.RankedMatchRepository
import no.battlefront.balancer.repository.RankedMatchStatRepository
import no.battlefront.balancer.repository.RankedPlayerStatRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.AppUserDetails
import no.battlefront.balancer.service.HostTokenService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

/**
 * End-to-end test of POST /api/matches/raw through HTTP, security and the database (H2).
 *
 * The players and results are the "clear win" case from RatingServiceTest, whose expected BR changes were checked
 * against the original JavaScript calculation.
 */
@SpringBootTest
@ActiveProfiles("test")
class RawMatchFlowTest {
    @Autowired private lateinit var context: WebApplicationContext

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var hostTokenRepository: HostTokenRepository

    @Autowired private lateinit var hostTokenService: HostTokenService

    @Autowired private lateinit var playerRepository: PlayerRepository

    @Autowired private lateinit var rankedPlayerStatRepository: RankedPlayerStatRepository

    @Autowired private lateinit var rankedMatchRepository: RankedMatchRepository

    @Autowired private lateinit var rankedMatchStatRepository: RankedMatchStatRepository

    @Autowired private lateinit var randomizerRepository: RandomizerRepository

    private lateinit var mvc: MockMvc
    private lateinit var supervisor: User
    private lateinit var editor: User
    private lateinit var token: String
    private lateinit var players: List<Player>

    // (persona ID, BR, score, kills, deaths); the first four are Rebels
    private val lineup =
        listOf(
            listOf(101L, 1100, 12000, 20, 10),
            listOf(102L, 1050, 9500, 15, 12),
            listOf(103L, 980, 8000, 12, 14),
            listOf(104L, 900, 6000, 8, 15),
            listOf(105L, 1080, 10500, 18, 12),
            listOf(106L, 1000, 9000, 14, 13),
            listOf(107L, 950, 7000, 10, 15),
            listOf(108L, 920, 5500, 6, 16),
        )

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<DefaultMockMvcBuilder>(springSecurity()).build()
        supervisor = userRepository.save(User(username = "sup", password = "x", role = "supervisor"))
        editor = userRepository.save(User(username = "editor", password = "x", role = "editor"))
        token = hostTokenService.createToken(HostTokenCreateRequest(name = "Sup PC", userId = supervisor.id)).token
        players =
            lineup.map { row ->
                val p =
                    playerRepository.save(
                        Player(nickname = "P${row[0]}", nation = "no", rating = 80, dzrating = 80, personaId = row[0].toLong()),
                    )
                rankedPlayerStatRepository.save(RankedPlayerStat(playerId = p.id, season = 1, br = row[1].toInt(), best = row[1].toInt()))
                p
            }
    }

    @AfterEach
    fun tearDown() {
        rankedMatchStatRepository.deleteAll()
        rankedMatchRepository.deleteAll()
        rankedPlayerStatRepository.deleteAll()
        playerRepository.deleteAll()
        randomizerRepository.deleteAll()
        hostTokenRepository.deleteAll()
        userRepository.deleteAll()
    }

    private fun playersJson(
        idField: String = "personaId",
        rows: List<List<Number>> = lineup,
    ) = rows
        .mapIndexed { i, r ->
            val id = if (idField == "personaId") r[0] else players[i].id
            val faction = if (i < 4) "Rebel" else "Imperial"
            """{"$idField":$id,"name":"ig${r[0]}","faction":"$faction","score":${r[2]},"kills":${r[3]},"deaths":${r[4]}}"""
        }.joinToString(",", "[", "]")

    private fun upload(
        body: String,
        asHost: Boolean = true,
    ): ResultActions {
        val request = post("/api/matches/raw").contentType(MediaType.APPLICATION_JSON).content(body)
        return mvc.perform(
            if (asHost) request.header(HttpHeaders.AUTHORIZATION, "Bearer $token") else request.with(user(AppUserDetails(editor))),
        )
    }

    private fun ResultActions.andExpectChanges(
        team: String,
        vararg changes: Int,
    ): ResultActions = changes.foldIndexed(this) { i, acc, c -> acc.andExpect(jsonPath("$.$team[$i].change").value(c)) }

    @Test
    fun `host uploads a map result and the server rates and stores it`() {
        val body =
            """{"map":"Levels/Desert/Desert_04/Desert_04","rebelScore":5,"imperialScore":2,
               "endedAt":"2026-10-06T19:14:40Z","players":${playersJson()}}"""

        upload(body)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.map").value("Dune Sea"))
            .andExpect(jsonPath("$.rule").value("?"))
            .andExpect(jsonPath("$.mvpPlayerId").value(players[0].id))
            .andExpect(jsonPath("$.rebels[0].newBr").value(1124))
            .andExpect(jsonPath("$.nextMap").value("Dune Sea"))
            .andExpectChanges("rebels", 24, 20, 17, 14)
            .andExpectChanges("imperials", -12, -15, -20, -25)

        val match = rankedMatchRepository.findAll().single()
        assertEquals(supervisor.id, match.supervisorId)
        assertEquals(4, match.teamSize)
        // 19:14 UTC is 21:14 in Oslo (CEST)
        assertEquals(LocalDateTime.of(2026, 10, 6, 21, 14, 40), match.date)
        assertEquals(8, rankedMatchStatRepository.findByMatchId(match.id).size)

        val first = rankedPlayerStatRepository.findByPlayerIdAndSeason(players[0].id, 1)!!
        assertEquals(1124, first.br)
        assertEquals(1, first.played)
        assertEquals(1, first.won)
        assertEquals(1, first.mvp)
        assertEquals("ig101", playerRepository.findById(players[0].id).get().lastSeenName)
    }

    @Test
    fun `dry run returns the result without storing anything`() {
        val body = """{"map":"Dune Sea","rebelScore":5,"imperialScore":2,"players":${playersJson("playerId")}}"""
        mvc
            .perform(
                post("/api/matches/raw")
                    .param("dryRun", "true")
                    .with(user(AppUserDetails(editor)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.matchId").doesNotExist())
            .andExpect(jsonPath("$.nextMap").doesNotExist())
            .andExpectChanges("rebels", 24, 20, 17, 14)

        assertEquals(0, rankedMatchRepository.count())
        assertEquals(0, randomizerRepository.count())
        assertEquals(1100, rankedPlayerStatRepository.findByPlayerIdAndSeason(players[0].id, 1)!!.br)
    }

    @Test
    fun `editor can upload on the website using player ids`() {
        upload("""{"map":"Dune Sea","rule":"DSE","rebelScore":3,"imperialScore":3,"players":${playersJson("playerId")}}""", asHost = false)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.rule").value("DSE"))

        assertEquals(editor.id, rankedMatchRepository.findAll().single().supervisorId)
    }

    @Test
    fun `unknown persona IDs reject the match and nothing is stored`() {
        val rows = lineup.toMutableList().also { it[7] = listOf(999L, 920, 5500, 6, 16) }
        upload("""{"map":"Dune Sea","rebelScore":5,"imperialScore":2,"players":${playersJson(rows = rows)}}""")
            .andExpect(status().isUnprocessableContent)
            .andExpect(jsonPath("$.unknown[0]").value(999))

        assertEquals(0, rankedMatchRepository.count())
        assertEquals(1100, rankedPlayerStatRepository.findByPlayerIdAndSeason(players[0].id, 1)!!.br)
    }

    @Test
    fun `invalid uploads are rejected with 400`() {
        val sevenPlayers = playersJson(rows = lineup.dropLast(1))
        upload("""{"map":"Dune Sea","rebelScore":5,"imperialScore":2,"players":$sevenPlayers}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Teams must be the same size (4 vs 3)"))

        val duplicate = playersJson(rows = lineup.toMutableList().also { it[7] = lineup[6] })
        upload("""{"map":"Dune Sea","rebelScore":5,"imperialScore":2,"players":$duplicate}""").andExpect(status().isBadRequest)
        upload("""{"map":"Dune Sea","rebelScore":6,"imperialScore":2,"players":${playersJson()}}""").andExpect(status().isBadRequest)
        upload("""{"map":"Dune Sea","rebelScore":5,"imperialScore":2,"endedAt":"yesterday","players":${playersJson()}}""")
            .andExpect(status().isBadRequest)

        assertEquals(0, rankedMatchRepository.count())
    }
}
