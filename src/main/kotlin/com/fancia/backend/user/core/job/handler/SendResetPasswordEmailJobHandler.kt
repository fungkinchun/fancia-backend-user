package com.fancia.backend.user.core.job.handler

import com.fancia.backend.shared.user.core.entity.PasswordResetToken
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.user.config.ApplicationProperties
import com.fancia.backend.user.core.exception.InvalidPasswordResetTokenException
import com.fancia.backend.user.core.repository.PasswordResetTokenRepository
import com.fancia.backend.user.email.EmailService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import java.util.*

@Component
class SendResetPasswordEmailJobHandler(
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val emailService: EmailService,
    private val applicationProperties: ApplicationProperties,
    private val templateEngine: SpringTemplateEngine
) {
    @Transactional
    @Throws(Exception::class)
    fun run(id: UUID) {
        val resetToken = passwordResetTokenRepository.findById(id)
            .orElseThrow { InvalidPasswordResetTokenException() }

        if (!resetToken.emailSent) {
            val user = resetToken.user ?: throw IllegalArgumentException("User associated with the token not found")
            sendResetPasswordEmail(user, resetToken)
        }
    }

    private fun sendResetPasswordEmail(user: User, token: PasswordResetToken) {
        val link = "${applicationProperties.baseUrl}/auth/reset-password?token=${token.token}"
        val thymeleafContext = Context().apply {
            setVariable("user", user)
            setVariable("link", link)
        }
        val htmlBody = templateEngine.process("password-reset", thymeleafContext)
        emailService.sendHtmlMessage(listOf(user.email), "Password reset requested", htmlBody)
        token.onEmailSent()
        passwordResetTokenRepository.save(token)
    }
}