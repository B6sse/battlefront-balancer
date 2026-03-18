package no.battlefront.balancer.security

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

/**
 * Returns the current authenticated user's id from the security context.
 * Used server-side only; never expose or trust client-supplied user ids for auth.
 */
@Service
class CurrentUserService {
    /**
     * Returns the current user's id, or null if not authenticated.
     */
    fun currentUserId(): Long? {
        val auth = SecurityContextHolder.getContext().authentication ?: return null
        val principal = auth.principal as? AppUserDetails ?: return null
        return principal.userId
    }
}
