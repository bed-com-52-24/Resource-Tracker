package com.resourcetracker.auth

import com.resourcetracker.mail.PasswordLinkRequested
import com.resourcetracker.user.User
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

@Service
class PasswordTokenService(
    private val tokens: PasswordTokenRepository,
    private val events: ApplicationEventPublisher,
) {
    private val random = SecureRandom()

    fun issue(user: User, purpose: PasswordTokenPurpose, expiresAt: Instant?): String {
        val raw = ByteArray(32).also(random::nextBytes)
        val rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        tokens.save(
            PasswordToken(
                userId = user.id,
                tokenHash = sha256(rawToken),
                purpose = purpose,
                expiresAt = expiresAt,
            )
        )
        events.publishEvent(PasswordLinkRequested(user.email, user.username, rawToken))
        return rawToken
    }

    fun hash(rawToken: String): String = sha256(rawToken)

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
