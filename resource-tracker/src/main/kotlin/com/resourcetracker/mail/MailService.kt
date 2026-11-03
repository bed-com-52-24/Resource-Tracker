package com.resourcetracker.mail

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

interface MailService {
    fun sendPasswordCode(email: String, username: String, code: String)
}

@Service
@Profile("!dev")
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

@Service
@Profile("dev")
class ConsoleMailService : MailService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendPasswordCode(email: String, username: String, code: String) {
        log.info("DEV ONLY: password setup/reset code for {} ({}): {}", username, email, code)
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
        }
    }
}
