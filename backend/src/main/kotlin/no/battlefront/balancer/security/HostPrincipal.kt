package no.battlefront.balancer.security

/**
 * Principal for requests authenticated with a host token (authority `ROLE_host`).
 *
 * @param tokenId id of the [HostToken][no.battlefront.balancer.model.HostToken] used
 * @param userId the token owner's user id; used as the supervisor of uploaded matches
 */
data class HostPrincipal(
    val tokenId: Long,
    val userId: Long,
    val name: String,
)
