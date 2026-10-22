package com.resourcetracker.mail

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

interface MailService {
    fun sendPasswordLink(email: String, username: String, link: String)
}

@Service
@Profile("!dev")
class SmtpMailService(
    private val sender: JavaMailSender,
    @Value("\${app.mail.from}") private val from: String,
) : MailService {
    override fun sendPasswordLink(email: String, username: String, link: String) {
        val message = SimpleMailMessage()
        message.setFrom(from)
        message.setTo(email)
        message.subject = "Set your Resource Tracker password"
        message.text = "Hello $username, set your Resource Tracker password using this link: $link"
        sender.send(message)
    }
}

@Service
@Profile("dev")
class LoggingMailService : MailService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun sendPasswordLink(email: String, username: String, link: String) {
        log.info("Password setup/reset link for {} ({}): {}", username, email, link)
    }
}

data class PasswordLinkRequested(
    val email: String,
    val username: String,
    val rawToken: String,
)

@Component
class PasswordLinkMailListener(
    private val mailService: MailService,
    @Value("\${app.frontend-base-url}") private val frontendBaseUrl: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun send(event: PasswordLinkRequested) {
        val link = "${frontendBaseUrl.trimEnd('/')}/set-password?token=${event.rawToken}"
        try {
            mailService.sendPasswordLink(event.email, event.username, link)
        } catch (exception: MailException) {
            log.error("Unable to send password link to {} ({})", event.email, exception.javaClass.simpleName)
        }
    }
}
