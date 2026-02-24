package com.fancia.backend.user.email

import jakarta.mail.MessagingException
import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

@Service
class EmailService() {
    @Autowired
    private lateinit var emailSender: JavaMailSender
    fun sendHtmlMessage(to: List<String>, subject: String, htmlBody: String) {
        try {
            val message: MimeMessage = emailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")
            helper.setTo(to.toTypedArray())
            helper.setSubject(subject)
            helper.setText(htmlBody, true)
            emailSender.send(message)
        } catch (e: MessagingException) {
            throw RuntimeException("Error sending email", e)
        }
    }

    fun sendSimpleEmail(to: List<String>, subject: String, content: String) {
        val message = SimpleMailMessage()
        message.setTo(*to.toTypedArray())
        message.subject = subject
        message.text = content
        emailSender.send(message)
    }
}