package com.resourcetracker.mail

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.MailException
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

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
        message.subject = "Your Resource Tracker password code"
        message.text = "Hello $username, your Resource Tracker password code is: $code"
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
