package no.battlefront.balancer.service

import no.battlefront.balancer.model.RecoveryCode
import no.battlefront.balancer.model.User
import no.battlefront.balancer.repository.RecoveryCodeRepository
import no.battlefront.balancer.repository.UserRepository
import no.battlefront.balancer.security.Totp
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.LocalDateTime
import java.util.HexFormat

/**
 * Two-factor authentication with an authenticator app (TOTP), required for admins and editors, plus one-time
 * recovery codes for a lost phone.
 */
@Service
class TwoFactorService(
    private val userRepository: UserRepository,
    private val recoveryCodeRepository: RecoveryCodeRepository,
) {
    /** Replaced in tests to control the time. */
    internal var clock: Clock = Clock.systemUTC()
    private val secureRandom = SecureRandom()

    fun isRequired(user: User): Boolean = user.role in REQUIRED_ROLES

    fun newSecret(): String = Totp.generateSecret()

    fun otpauthUri(
        username: String,
        secret: String,
    ): String = Totp.otpauthUri(ISSUER, username, secret)

    /**
     * Turns on two-factor login with [secret] if [code] from the authenticator app matches, and returns new
     * recovery codes (shown once). Returns null if the code is wrong.
     */
    @Transactional
    fun enable(
        userId: Long,
        secret: String,
        code: String,
    ): List<String>? {
        val step = Totp.matchingStep(secret, normalize(code), now(), lastUsedStep = null) ?: return null
        val user = findUser(userId)
        user.totpSecret = secret
        user.totpEnabled = true
        user.totpLastStep = step
        userRepository.save(user)
        return replaceRecoveryCodes(userId)
    }

    /**
     * Checks a login code: a 6-digit code from the authenticator app, or an unused recovery code (which is then
     * used up).
     */
    @Transactional
    fun verifyLoginCode(
        userId: Long,
        code: String,
    ): Boolean {
        val normalized = normalize(code)
        if (normalized.length == Totp.DIGITS && normalized.all { it.isDigit() }) return verifyAppCode(userId, normalized)
        val recovery = recoveryCodeRepository.findByUserIdAndCodeHashAndUsedAtIsNull(userId, hash(normalized)) ?: return false
        recovery.usedAt = LocalDateTime.now()
        recoveryCodeRepository.save(recovery)
        return true
    }

    /** Checks a 6-digit code from the authenticator app only (no recovery codes). */
    @Transactional
    fun verifyAppCode(
        userId: Long,
        code: String,
    ): Boolean {
        val user = findUser(userId)
        val secret = user.totpSecret
        if (!user.totpEnabled || secret == null) return false
        val step = Totp.matchingStep(secret, normalize(code), now(), user.totpLastStep) ?: return false
        user.totpLastStep = step
        userRepository.save(user)
        return true
    }

    /** Removes two-factor login; the user sets it up again at the next login. */
    @Transactional
    fun reset(userId: Long) {
        val user = findUser(userId)
        user.totpSecret = null
        user.totpEnabled = false
        user.totpLastStep = null
        userRepository.save(user)
        recoveryCodeRepository.deleteByUserId(userId)
    }

    private fun replaceRecoveryCodes(userId: Long): List<String> {
        recoveryCodeRepository.deleteByUserId(userId)
        val codes = List(RECOVERY_CODE_COUNT) { newRecoveryCode() }
        recoveryCodeRepository.saveAll(codes.map { RecoveryCode(userId = userId, codeHash = hash(normalize(it))) })
        return codes
    }

    private fun newRecoveryCode(): String {
        val chars = List(8) { RECOVERY_ALPHABET[secureRandom.nextInt(RECOVERY_ALPHABET.length)] }.joinToString("")
        return "${chars.take(4)}-${chars.drop(4)}"
    }

    private fun normalize(code: String): String = code.lowercase().filter { it.isLetterOrDigit() }

    private fun hash(normalizedCode: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalizedCode.toByteArray()))

    private fun now(): Long = clock.instant().epochSecond

    private fun findUser(userId: Long): User = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

    companion object {
        val REQUIRED_ROLES = setOf("admin", "editor")
        const val ISSUER = "Battlefront Balancer"
        const val RECOVERY_CODE_COUNT = 10

        /** No 0/o, 1/l/i, to avoid mix-ups when typing. */
        private const val RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"
    }
}
