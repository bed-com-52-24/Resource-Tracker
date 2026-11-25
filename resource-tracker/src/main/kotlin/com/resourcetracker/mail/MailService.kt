package com.resourcetracker.mail

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
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
import java.util.concurrent.ThreadPoolExecutor
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import org.springframework.web.util.HtmlUtils

interface MailService {
    fun sendPasswordLink(email: String, username: String, token: String, purpose: String)
}

@Service
class SmtpMailService(
    private val sender: JavaMailSender,
    @Value("\${app.mail.from}") private val from: String,
    @Value("\${app.frontend-base-url}") private val frontendBaseUrl: String,
) : MailService {
    override fun sendPasswordLink(email: String, username: String, token: String, purpose: String) {
        val link = org.springframework.web.util.UriComponentsBuilder.fromUriString(frontendBaseUrl.trimEnd('/') + "/reset-password")
            .queryParam("token", token)
            .build()
            .encode()
            .toUriString()
        val message = sender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, StandardCharsets.UTF_8.name())
        helper.setFrom(from)
        helper.setTo(email)
        val setup = purpose == "SETUP"
        val action = if (setup) "set up" else "reset"
        helper.setSubject("Resource Tracker | Password $action")
        helper.setText(plainText(username, link, action), htmlMessage(username, link, action))
        sender.send(message)
    }

    private fun plainText(username: String, link: String, action: String) = """Hello $username,

We received a request to $action the password for your Resource Tracker account.

Open this one-time link to continue:
$link

This link can only be used once. Password reset links expire automatically.

If you did not request this, you can ignore this email. Your password will not be changed unless the link is used.

Resource Tracker Support""".trimIndent()

    private fun htmlMessage(username: String, link: String, action: String): String {
        val safeUsername = HtmlUtils.htmlEscape(username)
        val safeLink = HtmlUtils.htmlEscape(link)
        val safeAction = HtmlUtils.htmlEscape(action)
        return """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Password $safeAction</title>
</head>
<body style="margin:0;padding:0;background-color:#f1f5f9;font-family:Arial,Helvetica,sans-serif;color:#172033;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f1f5f9;padding:32px 12px;">
    <tr><td align="center">
      <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="width:100%;max-width:600px;background-color:#ffffff;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden;">
        <tr><td style="background-color:#123b32;padding:24px 32px;">
          <p style="margin:0;color:#b8e8d7;font-size:12px;font-weight:bold;letter-spacing:1.5px;text-transform:uppercase;">Resource Tracker</p>
          <h1 style="margin:8px 0 0;color:#ffffff;font-size:22px;line-height:1.3;font-weight:700;">Password $safeAction</h1>
        </td></tr>
        <tr><td style="padding:32px;">
          <p style="margin:0 0 16px;font-size:16px;line-height:1.6;">Hello $safeUsername,</p>
          <p style="margin:0 0 24px;color:#475569;font-size:15px;line-height:1.7;">We received a request to $safeAction the password for your account. Use this one-time link to continue:</p>
          <p style="margin:0 0 24px;"><a href="$safeLink" style="display:inline-block;padding:13px 22px;background-color:#087c5b;border-radius:6px;color:#ffffff;text-decoration:none;font-size:15px;font-weight:bold;">$safeAction password</a></p>
          <p style="margin:0 0 16px;color:#475569;font-size:14px;line-height:1.7;">If the button does not work, copy and paste this URL into your browser:<br><a href="$safeLink">$safeLink</a><br>The link can only be used once.</p>
          <p style="margin:0;padding:16px;background-color:#f8fafc;border-left:3px solid #94a3b8;color:#64748b;font-size:13px;line-height:1.6;">If you did not request this, you can ignore this message. Your password will not change unless the link is used.</p>
        </td></tr>
        <tr><td style="padding:18px 32px;background-color:#f8fafc;border-top:1px solid #e2e8f0;">
          <p style="margin:0;color:#64748b;font-size:12px;line-height:1.6;">Resource Tracker Support</p>
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>"""
    }
}

data class PasswordLinkRequested(
    val email: String,
    val username: String,
    val token: String,
    val purpose: com.resourcetracker.auth.PasswordTokenPurpose,
)

@Component
class PasswordLinkMailListener(
    private val mailService: MailService,
    private val retries: FailedMailService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("mailTaskExecutor")
    fun send(event: PasswordLinkRequested) {
        try {
            mailService.sendPasswordLink(event.email, event.username, event.token, event.purpose.name)
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

@Configuration
class MailTaskConfiguration {
    @Bean("mailTaskExecutor")
    fun mailTaskExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 4
        setQueueCapacity(500)
        setThreadNamePrefix("mail-")
        setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        initialize()
    }
}

@Entity
@Table(name = "failed_password_emails")
class FailedPasswordEmail(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(nullable = false, length = 200) val email: String,
    @Column(nullable = false, length = 100) val username: String,
    @Column(name = "encrypted_code", nullable = false, length = 512) val encryptedToken: String,
    @Column(nullable = false, length = 10) val purpose: String = "RESET",
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
    fun enqueue(event: PasswordLinkRequested) {
        records.save(FailedPasswordEmail(email = event.email, username = event.username, encryptedToken = encrypt(event.token), purpose = event.purpose.name))
    }

    @Scheduled(fixedDelayString = "\${app.mail.retry.delay-ms:60000}")
    @Transactional
    fun retryDue() {
        try {
            val due = records.findDueForUpdate(Instant.now(), maxAttempts)
            due.forEach { entry ->
                try {
                    mail.sendPasswordLink(entry.email, entry.username, decrypt(entry.encryptedToken), entry.purpose)
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
