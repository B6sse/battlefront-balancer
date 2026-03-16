package no.battlefront.balancer.model

import jakarta.persistence.*

@Entity
@Table(name = "users")  // "user" is reserved in PostgreSQL
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 100)
    var username: String = "",

    @Column(nullable = false, length = 255)
    var password: String = "",  // bcrypt-hashed

    @Column(nullable = false, length = 50)
    var role: String = ""  // e.g. "admin", "supervisor"
)