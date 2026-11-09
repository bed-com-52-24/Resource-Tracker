package com.resourcetracker.mail

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.annotation.Transactional
import jakarta.persistence.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.Query
import jakarta.persistence.LockModeType
import java.time.Instant
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface MailService {
    fun sendPasswordCode(email: String, username: String, code: String)
}

@Service
class SmtpMailService(
    private val sender: JavaMailSender,
    @Value("\${app.mail.from}") private val from: String,
) : MailService {
    override fun sendPasswordCode(email: String, username: String, code: String) {
        val message = SimpleMailMessage()
        message.setFrom(from)
        message.setTo(email)
        message.subject = "Resource Tracker | Password setup or reset code"
        message.text = """Hello $username,

We received a request to set up or reset the password for your Resource Tracker account.

Your verification code is:

$code

Enter this code in Resource Tracker App to continue. For your security, do not share this code with anyone. It can only be used once.

If you did not request this, you can ignore this email. Your password will not be changed unless the code is used.

Regards,
Resource Tracker Support
""".trimIndent()
        sender.send(message)
    }
}

data class PasswordCodeRequested(
    val email: String,
    val username: String,
    val code: String,
)

@Component
class PasswordLinkMailListener(
    private val mailService: MailService,
    private val retries: FailedMailService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun send(event: PasswordCodeRequested) {
        try {
            mailService.sendPasswordCode(event.email, event.username, event.code)
        } catch (exception: MailException) {
            log.error(
                "Unable to send password link to {} ({})",
                event.email,
                exception.javaClass.simpleName,
                exception,
            )
            try {
                retries.enqueue(event)
            } catch (storageException: Exception) {
                log.error("Unable to persist failed password email for retry", storageException)
            }
        }
    }
}

@Entity
@Table(name = "failed_password_emails")
class FailedPasswordEmail(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(nullable = false, length = 200) val email: String,
    @Column(nullable = false, length = 100) val username: String,
    @Column(nullable = false, length = 512) val encryptedCode: String,
    @Column(nullable = false) var attempts: Int = 0,
    @Column(nullable = false) var nextAttemptAt: Instant = Instant.now(),
    @Column(nullable = false) val createdAt: Instant = Instant.now(),
)

interface FailedPasswordEmailRepository : JpaRepository<FailedPasswordEmail, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from FailedPasswordEmail e where e.nextAttemptAt <= :now and e.attempts < :maxAttempts order by e.createdAt")
    fun findDueForUpdate(@Param("now") now: Instant, @Param("maxAttempts") maxAttempts: Int): List<FailedPasswordEmail>
}

@Service
class FailedMailService(
    private val records: FailedPasswordEmailRepository,
    private val mail: MailService,
    @Value("\${app.security.jwt-secret}") secret: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()), "AES")
    private val random = SecureRandom()
    private val maxAttempts = 8

    @Transactional
    fun enqueue(event: PasswordCodeRequested) {
        records.save(FailedPasswordEmail(email = event.email, username = event.username, encryptedCode = encrypt(event.code)))
    }

    @Scheduled(fixedDelayString = "\${app.mail.retry.delay-ms:60000}")
    @Transactional
    fun retryDue() {
        try {
            val due = records.findDueForUpdate(Instant.now(), maxAttempts)
            due.forEach { entry ->
                try {
                    mail.sendPasswordCode(entry.email, entry.username, decrypt(entry.encryptedCode))
                    records.delete(entry)
                } catch (exception: Exception) {
                    entry.attempts += 1
                    entry.nextAttemptAt = Instant.now().plusSeconds((60L shl entry.attempts.coerceAtMost(10)))
                    log.warn("Password email retry {}/{} failed for {} ({})", entry.attempts, maxAttempts, entry.email, exception.javaClass.simpleName)
                }
            }
        } catch (exception: Exception) {
            log.error("Failed password email retry job encountered an error", exception)
        }
    }

    private fun encrypt(code: String): String {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return java.util.Base64.getEncoder().encodeToString(iv + cipher.doFinal(code.toByteArray()))
    }

    private fun decrypt(value: String): String {
        val bytes = java.util.Base64.getDecoder().decode(value)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes, 0, 12))
        return cipher.doFinal(bytes, 12, bytes.size - 12).toString(Charsets.UTF_8)
    }
}
