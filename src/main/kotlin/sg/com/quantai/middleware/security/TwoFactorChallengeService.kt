package sg.com.quantai.middleware.security

import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Service
class TwoFactorChallengeService {

    private data class Challenge(
        val uid: String,
        val expiresAt: Instant,
        val attemptsRemaining: Int
    )

    private val random = SecureRandom()
    private val challenges = ConcurrentHashMap<String, Challenge>()

    fun create(uid: String): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)

        val token = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes)

        val tokenHash = hash(token)

        challenges[tokenHash] = Challenge(
            uid = uid,
            expiresAt = Instant.now().plusSeconds(300),
            attemptsRemaining = 5
        )

        return token
    }

    fun verify(
        token: String,
        code: String,
        verifyCode: (String, String) -> Boolean
    ): String? {
        val tokenHash = hash(token)
        var authenticatedUid: String? = null

        challenges.computeIfPresent(tokenHash) { _, challenge ->
            if (Instant.now().isAfter(challenge.expiresAt)) {
                return@computeIfPresent null
            }

            if (challenge.attemptsRemaining <= 0) {
                return@computeIfPresent null
            }

            if (verifyCode(challenge.uid, code)) {
                authenticatedUid = challenge.uid
                null
            } else {
                val remaining = challenge.attemptsRemaining - 1

                if (remaining <= 0) {
                    null
                } else {
                    challenge.copy(attemptsRemaining = remaining)
                }
            }
        }

        return authenticatedUid
    }

    private fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))

        return Base64.getEncoder().encodeToString(digest)
    }
}
