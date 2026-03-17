package no.battlefront.balancer.model

import jakarta.persistence.*

/**
 * User account for authentication. Used by Spring Security and session-based login.
 *
 * @param role one of "admin", "supervisor" (stored without ROLE_ prefix; mapped to authority in [no.battlefront.balancer.security.AppUserDetails])
 * @param password bcrypt-hashed; never stored in plain text
 */
@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 100)
    var username: String = "",

    @Column(nullable = false, length = 255)
    var password: String = "",

    @Column(nullable = false, length = 50)
    var role: String = ""
)