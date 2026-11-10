package app.ulap.server.auth

import app.ulap.server.common.ApiException
import app.ulap.shared.api.ErrorCode
import app.ulap.shared.api.ErrorDetail
import app.ulap.shared.validation.PasswordRules
import de.mkammerer.argon2.Argon2
import de.mkammerer.argon2.Argon2Factory

/** Turns a password into a stored hash and checks a password against one. Never logs either. */
interface PasswordHasher {
    fun hash(password: String): String

    fun verify(hash: String, password: String): Boolean
}

/**
 * Argon2id with the parameters of api-spec.md §6.3: 64 MB of memory, 3 iterations, parallelism 1.
 * The hash string records them, so they can be raised later and old hashes still verify. The
 * password's characters are wiped from memory after each call.
 */
class Argon2PasswordHasher(private val memoryKb: Int = MEMORY_KB, private val iterations: Int = ITERATIONS) :
    PasswordHasher {
    private val argon2: Argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id)

    override fun hash(password: String): String = withChars(password) {
        argon2.hash(iterations, memoryKb, PARALLELISM, it)
    }

    override fun verify(hash: String, password: String): Boolean = withChars(password) { argon2.verify(hash, it) }

    private inline fun <T> withChars(password: String, block: (CharArray) -> T): T {
        val chars = password.toCharArray()
        try {
            return block(chars)
        } finally {
            argon2.wipeArray(chars)
        }
    }

    companion object {
        const val MEMORY_KB: Int = 64 * 1024
        const val ITERATIONS: Int = 3
        const val PARALLELISM: Int = 1
    }
}

/**
 * The common-password list of api-spec.md §6.3, from `resources/passwords/common-passwords.txt`
 * (SecLists' 10k most common, kept to the entries that pass the length rule). Compared lower-cased.
 */
class CommonPasswords(private val passwords: Set<String>) {
    operator fun contains(password: String): Boolean = password.trim().lowercase() in passwords

    companion object {
        private const val RESOURCE = "/passwords/common-passwords.txt"

        fun load(): CommonPasswords {
            val stream =
                requireNotNull(CommonPasswords::class.java.getResourceAsStream(RESOURCE)) { "$RESOURCE is missing" }
            val lines = stream.bufferedReader().useLines { lines ->
                lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            }
            return CommonPasswords(lines)
        }
    }
}

/**
 * The password rules (shared `PasswordRules`) plus hashing: what the register, reset and link
 * flows share. A password that breaks a rule is `422 WEAK_PASSWORD` with the rule in `details`.
 */
class PasswordPolicy(private val hasher: PasswordHasher, private val common: CommonPasswords) {
    /**
     * A real hash of a throwaway password. Login verifies against it when the email is unknown, so
     * a wrong email and a wrong password take the same time (api-spec.md §6.4: one error for both).
     */
    private val decoyHash: String by lazy { hasher.hash("ulap-decoy-" + OpaqueTokens.generate()) }

    fun requireStrong(password: String, email: String?) {
        val problem = PasswordRules.check(password, email) { it in common } ?: return
        throw ApiException(
            ErrorCode.WEAK_PASSWORD,
            "Password is ${problem.name}",
            listOf(ErrorDetail("password", problem.name)),
        )
    }

    fun hash(password: String): String = hasher.hash(password)

    /** False for a wrong password, and always false (after the same work) when [hash] is null. */
    fun verify(hash: String?, password: String): Boolean = hasher.verify(hash ?: decoyHash, password) && hash != null
}
