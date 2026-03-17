package no.battlefront.balancer.security

import no.battlefront.balancer.model.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Adapts [User] to [UserDetails]. Exposes id for use in controllers/services; password is never returned.
 */
class AppUserDetails(private val user: User) : UserDetails {

    val userId: Long get() = user.id

    override fun getUsername(): String = user.username
    override fun getPassword(): String = user.password
    override fun getAuthorities(): MutableCollection<out GrantedAuthority> =
        mutableListOf(SimpleGrantedAuthority("ROLE_${user.role}"))
    override fun isEnabled(): Boolean = true
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
}
