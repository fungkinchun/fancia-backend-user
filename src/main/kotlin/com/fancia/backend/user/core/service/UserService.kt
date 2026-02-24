package com.fancia.backend.user.core.service

import com.fancia.backend.shared.user.core.entity.PasswordResetToken
import com.fancia.backend.shared.user.core.entity.VerificationCode
import com.fancia.backend.user.core.dto.CreateUserRequest
import com.fancia.backend.user.core.dto.UpdateUserPasswordRequest
import com.fancia.backend.user.core.dto.UpdateUserRequest
import com.fancia.backend.user.core.dto.UserResponse
import com.fancia.backend.user.core.event.PasswordResetTokenCreatedEvent
import com.fancia.backend.user.core.event.UserCreatedEvent
import com.fancia.backend.user.core.exception.*
import com.fancia.backend.user.core.job.SendResetPasswordEmailJob
import com.fancia.backend.user.core.job.SendWelcomeEmailJob
import com.fancia.backend.user.core.repository.PasswordResetTokenRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.core.repository.VerificationCodeRepository
import com.fancia.backend.user.mapper.UserMapper
import com.fancia.backend.user.storage.entity.UploadedFile
import com.fancia.backend.user.storage.repository.UploadedFileRepository
import com.fancia.backend.user.storage.service.FileStorageService
import jakarta.validation.Valid
import org.springframework.context.ApplicationEventPublisher
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository,
    private val verificationCodeRepository: VerificationCodeRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val userMapper: UserMapper,
    private val passwordEncoder: PasswordEncoder,
    private val uploadedFileRepository: UploadedFileRepository,
    private val fileUploadService: FileStorageService,
    private val applicationEventPublisher: ApplicationEventPublisher
) {
    fun findByEmail(email: String): UserResponse? {
        val user = userRepository.findByEmail(email)
            ?: throw UserWithEmailNotFoundException(email)

        return UserResponse(user)
    }

    @Transactional
    fun create(request: @Valid CreateUserRequest): UserResponse {
        val user = userMapper.toBean(request)
        user.setPassword(
            passwordEncoder.encode(user.password) ?: throw IllegalArgumentException("Password cannot be null")
        )
        userRepository.save(user)
        val verificationCode = VerificationCode(user)
        user.verificationCode = verificationCode
        verificationCodeRepository.save(verificationCode)
        user.id?.let {
            applicationEventPublisher.publishEvent(UserCreatedEvent(it))
        }
        return UserResponse(user)
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun sendVerificationEmail(event: UserCreatedEvent) {
        val user =
            userRepository.findById(event.id).orElseThrow { UserWithIdNotFoundException(event.id.toString()) }
        user.id?.let {
            SendWelcomeEmailJob.scheduleJob(it)
        }
    }

    @Transactional
    fun verifyEmail(code: String) {
        val verificationCode = verificationCodeRepository.findByCode(code)
            ?: throw InvalidVerificationCodeException()
        verificationCode.user?.apply {
            verified = true
            userRepository.save(this)
        }
        verificationCodeRepository.delete(verificationCode)
    }

    @Transactional
    fun forgotPassword(email: String) {
        val user = userRepository.findByEmail(email)
            ?: throw UserWithEmailNotFoundException(email)
        val passwordResetToken = PasswordResetToken(user)
        passwordResetTokenRepository.save(passwordResetToken)
        passwordResetToken.id?.let {
            applicationEventPublisher.publishEvent(PasswordResetTokenCreatedEvent(it))
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun sendResetPasswordEmail(event: PasswordResetTokenCreatedEvent) {
        val passwordResetToken =
            passwordResetTokenRepository.findById(event.id)
                .orElseThrow { PasswordResetTokenNotFoundException(event.id.toString()) }
        passwordResetToken.id?.let {
            SendResetPasswordEmailJob.scheduleJob(it)
        }
    }

    @Transactional
    fun resetPassword(request: UpdateUserPasswordRequest) {
        val passwordResetToken = passwordResetTokenRepository.findByToken(request.passwordResetToken)
            ?: throw PasswordResetTokenNotFoundException()

        if (passwordResetToken.isExpired) {
            throw PasswordResetTokenExpiredException()
        }

        passwordResetToken.user?.let { user ->
            passwordEncoder.encode(request.password)?.let {
                user.setPassword(it)
                userRepository.save(user)
            } ?: throw InvalidPasswordResetTokenException()
        } ?: throw UserNotFoundException()
    }

    @Transactional
    fun update(request: UpdateUserRequest, jwt: Jwt): UserResponse {
        val userId = jwt.claims["userId"] as UUID? ?: throw InvalidAuthenticationTokenException()
        val user = userRepository.findById(userId).orElseThrow()
        return UserResponse(userRepository.save(userMapper.toBean(request, user)))
    }

    @Transactional
    fun updatePassword(request: UpdateUserPasswordRequest, jwt: Jwt): UserResponse {
        val userId = jwt.claims["userId"] as UUID? ?: throw InvalidAuthenticationTokenException()
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException() }

        if (user.password != null && !passwordEncoder.matches(request.oldPassword, user.password)) {
            throw InvalidAuthenticationException()
        }
        passwordEncoder.encode(request.password)?.let {
            user.setPassword(it)
        }
        return UserResponse(userRepository.save(user))
    }

    fun updateProfilePicture(file: MultipartFile, jwt: Jwt): UserResponse {
        val userId = jwt.claims["userId"] as UUID? ?: throw InvalidAuthenticationTokenException()
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException() }
        val uploadedFile = UploadedFile(file.originalFilename, file.size, user)
        try {
            val url = fileUploadService.uploadFile(uploadedFile.buildPath("profile-picture"), file.bytes)
            url?.let {
                uploadedFile.onUploaded(url)
                user.profileImageUrl = url

                uploadedFileRepository.save(uploadedFile)
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
        return UserResponse(userRepository.save(user))
    }
}