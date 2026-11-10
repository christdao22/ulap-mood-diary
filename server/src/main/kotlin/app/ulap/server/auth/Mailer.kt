package app.ulap.server.auth

import app.ulap.server.config.AppConfig
import jakarta.mail.Message
import jakarta.mail.MessagingException
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One plain-text email. `toString()` hides the address and the body, which carries a link with a token. */
class MailMessage(val to: String, val subject: String, val text: String) {
    override fun toString(): String = "MailMessage(subject=$subject, to=***)"
}

/** Sends email. The SMTP one is real; tests record instead. */
interface Mailer {
    /** Delivers [message] to the mail server, or throws [MailException]. */
    suspend fun send(message: MailMessage)
}

/** The mail server refused or couldn't be reached. The message names neither the address nor the body. */
class MailException(cause: Throwable) : RuntimeException("Email could not be sent", cause)

/**
 * SMTP through Jakarta Mail (ulap-reference.md §6.7): Mailpit in development (no login, plain),
 * a real provider in production (login and STARTTLS). `AppConfig` already refuses to start
 * production without the login.
 */
class SmtpMailer(private val config: AppConfig.MailConfig) : Mailer {
    private val session: Session = Session.getInstance(
        Properties().apply {
            put("mail.smtp.host", config.smtpHost)
            put("mail.smtp.port", config.smtpPort.toString())
            put("mail.smtp.auth", (config.smtpUser != null).toString())
            put("mail.smtp.starttls.enable", (config.smtpUser != null).toString())
            put("mail.smtp.starttls.required", (config.smtpUser != null).toString())
            put("mail.smtp.connectiontimeout", TIMEOUT_MS)
            put("mail.smtp.timeout", TIMEOUT_MS)
            put("mail.smtp.writetimeout", TIMEOUT_MS)
        },
    )

    override suspend fun send(message: MailMessage) = withContext(Dispatchers.IO) {
        try {
            val mime = MimeMessage(session).apply {
                setFrom(InternetAddress(config.from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(message.to))
                setSubject(message.subject, "UTF-8")
                setText(message.text, "UTF-8")
            }
            val user = config.smtpUser
            if (user != null) {
                Transport.send(mime, user, config.smtpPassword?.value)
            } else {
                Transport.send(mime)
            }
        } catch (e: MessagingException) {
            throw MailException(e)
        }
    }

    private companion object {
        const val TIMEOUT_MS = "10000"
    }
}
