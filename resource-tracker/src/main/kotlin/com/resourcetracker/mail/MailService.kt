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
    fun sendPasswordCode(email: String, username: String, code: String)
}

@Service
class SmtpMailService(
    private val sender: JavaMailSender,
    @Value("\${app.mail.from}") private val from: String,
) : MailService {
    override fun sendPasswordCode(email: String, username: String, code: String) {
        val message = sender.createMimeMessage()
        val helper = MimeMessageHelper(message, true, StandardCharsets.UTF_8.name())
        helper.setFrom(from)
        helper.setTo(email)
        helper.setSubject("Resource Tracker | Password setup or reset code")
        helper.setText(plainText(username, code), htmlMessage(username, code))
        sender.send(message)
    }

    private fun plainText(username: String, code: String) = """Hello $username,

We received a request to set up or reset the password for your Resource Tracker account.

Your verification code: $code

Enter this code in the Resource Tracker app. For your security, do not share it with anyone. It can only be used once.

If you did not request this, you can ignore this email. Your password will not be changed unless the code is used.

Resource Tracker Support""".trimIndent()

    private fun htmlMessage(username: String, code: String): String {
        val safeUsername = HtmlUtils.htmlEscape(username)
        val safeCode = HtmlUtils.htmlEscape(code)
        return """<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Password setup or reset code</title>
</head>
<body style="margin:0;padding:0;background-color:#f1f5f9;font-family:Arial,Helvetica,sans-serif;color:#172033;">
  <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f1f5f9;padding:32px 12px;">
    <tr><td align="center">
      <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="width:100%;max-width:600px;background-color:#ffffff;border:1px solid #e2e8f0;border-radius:12px;overflow:hidden;">
        <tr><td style="background-color:#123b32;padding:24px 32px;">
          <p style="margin:0;color:#b8e8d7;font-size:12px;font-weight:bold;letter-spacing:1.5px;text-transform:uppercase;">Resource Tracker</p>
          <h1 style="margin:8px 0 0;color:#ffffff;font-size:22px;line-height:1.3;font-weight:700;">Password security code</h1>
        </td></tr>
        <tr><td style="padding:32px;">
          <p style="margin:0 0 16px;font-size:16px;line-height:1.6;">Hello $safeUsername,</p>
          <p style="margin:0 0 24px;color:#475569;font-size:15px;line-height:1.7;">We received a request to set up or reset the password for your account. Use this one-time verification code to continue:</p>
          <table role="presentation" width="100%" cellspacing="0" cellpadding="0" border="0" style="margin:0 0 24px;background-color:#f0f9f6;border:1px solid #cce9dd;border-radius:8px;">
            <tr><td align="center" style="padding:20px 16px;">
              <p style="margin:0 0 8px;color:#42645b;font-size:12px;font-weight:bold;letter-spacing:1.2px;text-transform:uppercase;">Your verification code</p>
              <p style="margin:0;color:#123b32;font-family:Consolas,'Courier New',monospace;font-size:32px;line-height:1.3;font-weight:bold;letter-spacing:8px;">$safeCode</p>
            </td></tr>
          </table>
          <p style="margin:0 0 16px;color:#475569;font-size:14px;line-height:1.7;">Enter the code in the Resource Tracker app. It can only be used once. For your security, do not share it with anyone.</p>
          <p style="margin:0;padding:16px;background-color:#f8fafc;border-left:3px solid #94a3b8;color:#64748b;font-size:13px;line-height:1.6;">If you did not request this, you can ignore this message. Your password will not change unless the code is used.</p>
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
    @Async("mailTaskExecutor")
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
