package no.battlefront.balancer.model

import jakarta.persistence.*
import java.time.LocalDateTime

/**
 * A single ranked match. Stores map, rule, season, scores and references to supervisor and optional MVP.
 *
 * @param mvpId optional FK to [Player]; null if no MVP was set
 * @param supervisorId FK to [User] who submitted/oversaw the match
 */
@Entity
@Table(name = "ranked_matches")
class RankedMatch(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    var map: String = "",

    @Column(nullable = false, length = 50)
    var rule: String = "",

    @Column(nullable = false)
    var season: Int = 0,

    @Column(name = "team_size", nullable = false)
    var teamSize: Int = 0,

    @Column(name = "rebel_score", nullable = false)
    var rebelScore: Int = 0,

    @Column(name = "imperial_score", nullable = false)
    var imperialScore: Int = 0,

    @Column(name = "mvp", nullable = true)
    var mvpId: Long? = null,

    @Column(name = "supervisor", nullable = false)
    var supervisorId: Long = 0,

    @Column(nullable = false)
    var date: LocalDateTime = LocalDateTime.now()
)