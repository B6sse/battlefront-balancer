package no.battlefront.balancer.security

/**
 * Rules for passwords set by admins and for usernames of new accounts.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 10

    /** bcrypt only uses the first 72 bytes of a password. */
    private const val MAX_BYTES = 72
    private val USERNAME_RE = Regex("^[A-Za-z0-9_.-]{3,32}$")

    const val RULES = "Password must be at least $MIN_LENGTH characters"

    /**
     * @throws IllegalArgumentException if [password] breaks the rules.
     */
    fun requireValid(password: String) {
        require(password.trim().length >= MIN_LENGTH) { RULES }
        require(password.toByteArray().size <= MAX_BYTES) { "Password is too long" }
    }

    /**
     * @throws IllegalArgumentException if [username] is not 3–32 letters, digits, '.', '_' or '-'.
     */
    fun requireValidUsername(username: String) {
        require(USERNAME_RE.matches(username)) { "Username must be 3–32 characters: letters, digits, '.', '_' or '-'" }
    }
}
