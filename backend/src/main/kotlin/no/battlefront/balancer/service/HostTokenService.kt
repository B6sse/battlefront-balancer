package no.battlefront.balancer.service

import no.battlefront.balancer.dto.HostTokenCreateRequest
import no.battlefront.balancer.dto.HostTokenCreatedDto
import no.battlefront.balancer.dto.HostTokenDto
import no.battlefront.balancer.model.HostToken
import no.battlefront.balancer.repository.HostTokenRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.CurrentUserService
import no.battlefront.balancer.security.HostPrincipal
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.HexFormat

/**
 * Creates, lists, revokes and verifies host tokens. Tokens are 32 random bytes (base64url); only their
 * SHA-256 hash is stored, so a token can be shown once and never again.
 */
@Service
class HostTokenService(
    private val hostTokenRepository: HostTokenRepository,
    private val userRepository: UserRepository,
    private val currentUserService: CurrentUserService,
) {
    private val secureRandom = SecureRandom()
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun listTokens(): List<HostTokenDto> {
        val tokens = hostTokenRepository.findAllByOrderByIdDesc()
        val users = userRepository.findAllById(tokens.map { it.userId }.distinct()).associateBy { it.id }
        return tokens.map { t ->
            HostTokenDto(
                id = t.id,
                name = t.name,
                userId = t.userId,
                username = users[t.userId]?.username,
                createdAt = t.createdAt.format(isoFormatter),
                lastUsedAt = t.lastUsedAt?.format(isoFormatter),
                revokedAt = t.revokedAt?.format(isoFormatter),
            )
        }
    }

    /**
     * Creates a token owned by [HostTokenCreateRequest.userId] and returns it in plain text (the only time it is visible).
     *
     * @throws IllegalArgumentException if the name is blank or too long, or the owner is not an admin, supervisor or editor.
     */
    @Transactional
    fun createToken(request: HostTokenCreateRequest): HostTokenCreatedDto {
        val name = request.name.trim()
        require(name.isNotEmpty()) { "Name is required" }
        require(name.length <= 100) { "Name max 100 characters" }
        val owner = userRepository.findById(request.userId).orElse(null) ?: throw IllegalArgumentException("User not found")
        require(owner.role in HOST_ROLES) { "Token owner must be an admin, supervisor or editor" }

        val token = generateToken()
        val saved =
            hostTokenRepository.save(
                HostToken(name = name, tokenHash = hash(token), userId = owner.id, createdBy = currentUserService.currentUserId()),
            )
        return HostTokenCreatedDto(id = saved.id, name = saved.name, token = token, userId = owner.id, username = owner.username)
    }

    /**
     * Revokes a token. Revoking an already revoked token does nothing.
     *
     * @throws IllegalArgumentException if the token does not exist.
     */
    @Transactional
    fun revokeToken(id: Long) {
        val token = hostTokenRepository.findById(id).orElse(null) ?: throw IllegalArgumentException("Token not found")
        if (token.revokedAt != null) return
        token.revokedAt = LocalDateTime.now()
        hostTokenRepository.save(token)
    }

    /**
     * Verifies a plain token and records its use.
     *
     * @return the [HostPrincipal], or null if the token is unknown, revoked, or its owner no longer has a role that may own tokens.
     */
    @Transactional
    fun authenticate(rawToken: String): HostPrincipal? {
        if (rawToken.isBlank()) return null
        val token = hostTokenRepository.findByTokenHash(hash(rawToken)) ?: return null
        if (token.revokedAt != null) return null
        val owner = userRepository.findById(token.userId).orElse(null) ?: return null
        if (owner.role !in HOST_ROLES) return null
        token.lastUsedAt = LocalDateTime.now()
        hostTokenRepository.save(token)
        return HostPrincipal(tokenId = token.id, userId = owner.id, name = token.name)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    internal fun hash(token: String): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.toByteArray()))

    private companion object {
        val HOST_ROLES = setOf("admin", "supervisor", "editor")
        const val TOKEN_BYTES = 32
    }
}
