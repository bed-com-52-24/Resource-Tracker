package com.resourcetracker.auth

import com.resourcetracker.mail.PasswordCodeRequested
import com.resourcetracker.user.User
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

@Service
class PasswordTokenService(
    private val tokens: PasswordTokenRepository,
    private val events: ApplicationEventPublisher,
) {
    private val random = SecureRandom()

    fun issue(user: User, purpose: PasswordTokenPurpose, expiresAt: Instant?): String {
        for (attempt in 1..10) {
            val code = random.nextInt(100_000_000).toString().padStart(8, '0')
            val hash = sha256(code)
            if (tokens.findByTokenHash(hash) == null) {
                tokens.save(
                    PasswordToken(
                        userId = user.id,
                        tokenHash = hash,
                        purpose = purpose,
                        expiresAt = expiresAt,
                    ),
                )
                events.publishEvent(PasswordCodeRequested(user.email, user.username, code))
                return code
            }
        }
        throw IllegalStateException("Unable to generate a unique password code")
    }

    fun hash(rawCode: String): String = sha256(rawCode)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
