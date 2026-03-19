package no.battlefront.balancer.repository

import no.battlefront.balancer.model.User
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA repository for [User] entities.
 */
interface UserRepository : JpaRepository<User, Long> {
    /**
     * Returns the user with the given username, or null if none.
     */
    fun findByUsername(username: String): User?
}
