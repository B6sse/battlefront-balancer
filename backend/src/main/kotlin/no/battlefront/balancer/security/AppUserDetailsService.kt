package no.battlefront.balancer.security

import no.battlefront.balancer.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

/**
 * Loads user details from the database for Spring Security authentication.
 *
 * Used by the authentication flow to resolve a username (e.g. from login form) into a
 * [UserDetails] instance. Throws [UsernameNotFoundException] if the user does not exist.
 */
@Service
class AppUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {
    /**
     * Loads the user by username and wraps it as [AppUserDetails].
     *
     * @param username the login name to look up
     * @return [AppUserDetails] for the user
     * @throws UsernameNotFoundException if no user exists with the given username
     */
    override fun loadUserByUsername(username: String): UserDetails {
        val user =
            userRepository.findByUsername(username)
                ?: throw UsernameNotFoundException("User not found: $username")
        return AppUserDetails(user)
    }
}
