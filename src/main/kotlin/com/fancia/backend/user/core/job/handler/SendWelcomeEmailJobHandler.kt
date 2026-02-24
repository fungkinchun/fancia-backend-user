package com.fancia.backend.user.core.job.handler

import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.entity.VerificationCode
import com.fancia.backend.user.config.ApplicationProperties
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.core.repository.VerificationCodeRepository
import com.fancia.backend.user.email.EmailService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.thymeleaf.context.Context
import org.thymeleaf.spring6.SpringTemplateEngine
import java.util.*

@Component
class SendWelcomeEmailJobHandler(
    private val userRepository: UserRepository,
    private val verificationCodeRepository: VerificationCodeRepository,
    private val templateEngine: SpringTemplateEngine,
    private val emailService: EmailService,
    private val applicationProperties: ApplicationProperties
) {
    @Transactional
    @Throws(Exception::class)
    fun run(id: UUID) {
        val user = userRepository.findById(id)
            .orElseThrow { IllegalArgumentException(id.toString()) }
        val verificationCode = user.verificationCode
        if (verificationCode != null && !verificationCode.emailSent) {
            sendWelcomeEmail(user, verificationCode)
        }
    }

    private fun sendWelcomeEmail(user: User, code: VerificationCode) {
        val verificationLink = "${applicationProperties.baseUrl}/api/users/verify-email?token=${code.code}"
        val thymeleafContext = Context().apply {
            setVariable("user", user)
            setVariable("verificationLink", verificationLink)
            setVariable("applicationName", applicationProperties.applicationName)
        }
        val htmlBody = templateEngine.process("welcome-email", thymeleafContext)
        emailService.sendHtmlMessage(listOf(user.email), "Welcome to our platform", htmlBody)
        code.emailSent = true
        verificationCodeRepository.save(code)
    }
}