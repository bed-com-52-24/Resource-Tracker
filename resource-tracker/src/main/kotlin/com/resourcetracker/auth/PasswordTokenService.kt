package com.resourcetracker.auth

import com.resourcetracker.mail.PasswordLinkRequested
import com.resourcetracker.user.User
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.time.Instant

@Service
class PasswordTokenService(
    private val tokens: PasswordTokenRepository,
    private val events: ApplicationEventPublisher,
) {
    private val random = SecureRandom()

    fun issue(user: User, purpose: PasswordTokenPurpose, expiresAt: Instant?): String {
        for (attempt in 1..10) {
            val token = ByteArray(32).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
            val hash = sha256(token)
            if (tokens.findByTokenHash(hash) == null) {
                tokens.save(
                    PasswordToken(
                        userId = user.id,
                        tokenHash = hash,
                        purpose = purpose,
                        expiresAt = expiresAt,
                    ),
                )
                events.publishEvent(PasswordLinkRequested(user.email, user.username, token, purpose))
                return token
            }
        }
        throw IllegalStateException("Unable to generate a unique password token")
    }

    fun hash(rawToken: String): String = sha256(rawToken)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
