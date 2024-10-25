package com.vemeet.backend.service

import java.time.format.DateTimeFormatter
import com.amazonaws.services.cognitoidp.model.UserNotFoundException
import com.vemeet.backend.cache.UserCache
import com.vemeet.backend.dto.*
import com.vemeet.backend.exception.ResourceNotFoundException
import com.vemeet.backend.model.Image
import com.vemeet.backend.model.User
import com.vemeet.backend.repository.UserRepository
import com.vemeet.backend.security.CognitoService
import jakarta.transaction.Transactional
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val cognitoService: CognitoService,
    private val userCache: UserCache
) {

    fun createUser(regReq: RegisterRequest, awsCognitoId: String):  RegisterResponse  {
        val user = User(
            username = regReq.username,
            awsCognitoId = awsCognitoId,
            birthday = regReq.birthday,
        )


        regReq.imageUrl?.let { url ->
            val image = Image(url = url, user = user)
            user.profileImage = image
        }

        val newUser = userRepository.save(user)

        return RegisterResponse(
            username = newUser.username,
            email = regReq.email,
            id = newUser.id,
            createdAt = DateTimeFormatter.ISO_INSTANT.format(newUser.createdAt),
            imageUrl = newUser.profileImage?.url
        )
    }

    fun login(logReq : LoginRequest): ResponseEntity<LoginResponse> {

        val authResult = try {
            cognitoService.initiateAuth(logReq.email, logReq.password)
        } catch (e: Exception) {
            throw BadCredentialsException("Invalid email or password")
        }

        val cognitoId = try {
            cognitoService.getUserSub(authResult.authenticationResult.accessToken)
        } catch (e: Exception) {
            throw BadCredentialsException("Invalid email or password")
        }

        val user = findByAwsCognitoId(cognitoId)
            ?: throw UserNotFoundException("User not found in the database")

        userCache.cacheUserSession(
            authResult.authenticationResult.accessToken,
            authResult.authenticationResult.expiresIn.toLong(),
            user
        )

        val now = Instant.now()
        val thirtyDaysLater = now.plus(30, ChronoUnit.DAYS)
        val accessTokenExpiry =  now.plusSeconds(authResult.authenticationResult.expiresIn.toLong())
        val loginResponse = LoginResponse(
            cognitoId = cognitoId,
            refreshToken = authResult.authenticationResult.refreshToken,
            refreshTokenExpiry = thirtyDaysLater,
            accessToken = authResult.authenticationResult.accessToken,
            accessTokenExpiry = accessTokenExpiry
        )

        val accessTokenCookie = createSecureCookie("access_token", loginResponse.accessToken, accessTokenExpiry)
        val refreshTokenCookie = createSecureCookie("refresh_token", loginResponse.refreshToken, thirtyDaysLater)
        val cognitoIdCookie = createSecureCookie("cognito_id", loginResponse.cognitoId, thirtyDaysLater)

        val headers = HttpHeaders()
        headers.add(HttpHeaders.SET_COOKIE, accessTokenCookie.toString())
        headers.add(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
        headers.add(HttpHeaders.SET_COOKIE, cognitoIdCookie.toString())

        return ResponseEntity.ok().headers(headers).body(loginResponse)
    }

    @Transactional
    fun markUserAsVerified(id: String) {
        val user = userRepository.findUserByAwsCognitoId(id) ?: throw UserNotFoundException("User not found with")
        user.verified = true
        userRepository.save(user)
    }

    fun refreshAccessToken(refreshToken: String, awsId: String): RefreshResponse {
        val authResult = cognitoService.refreshAccessToken(refreshToken, awsId)

        val cognitoId = cognitoService.getUserSub(authResult.authenticationResult.accessToken)

        val user = findByAwsCognitoId(cognitoId)
            ?: throw UserNotFoundException("User not found in the database")

        userCache.cacheUserSession(
            authResult.authenticationResult.accessToken,
            authResult.authenticationResult.expiresIn.toLong(),
            user
        )

        val accessTokenExpiry = Instant.now().plusSeconds(authResult.authenticationResult.expiresIn.toLong())

        return RefreshResponse(
            accessToken = authResult.authenticationResult.accessToken,
            accessTokenExpiry = accessTokenExpiry
        )
    }

    fun initiatePasswordReset(email: String) {
        cognitoService.getCognitoUserByEmail(email)
        cognitoService.forgotPassword(email)
    }

    fun completePasswordReset(email: String, newPassword: String, confirmationCode: String) {
        cognitoService.confirmForgotPassword(email, newPassword, confirmationCode)
    }


    fun resendVerificationEmail(email: String) {
        cognitoService.resendConfirmationCode(email)
    }

    fun changePassword(accessToken: String, oldPassword: String, newPassword: String) {
        cognitoService.changePassword(accessToken, oldPassword, newPassword)
    }

    @Transactional
    fun deleteAccount(accessToken: String) {
        val cognitoId = cognitoService.getUserSub(accessToken)
        val user = userRepository.findUserByAwsCognitoId(cognitoId)
            ?: throw ResourceNotFoundException("User not found")

        cognitoService.deleteUser(accessToken)
        userRepository.delete(user)
        userCache.deleteUserSession(accessToken)
    }

    fun initiateEmailChange(accessToken: String, newEmail: String) {
        cognitoService.getCognitoUserByEmail(newEmail)
        cognitoService.initiateUpdateUserAttribute(accessToken, "email", newEmail)
    }

    fun confirmEmailChange(accessToken: String, confirmationCode: String) {
        cognitoService.confirmUpdateUserAttribute(accessToken, "email", confirmationCode)
    }

    fun findByUsername(username: String): User? = userRepository.findUserByUsername(username)
    fun findByAwsCognitoId(awsCognitoId: String): User? =  userRepository.findUserByAwsCognitoId(awsCognitoId)


    private fun createSecureCookie(name: String, value: String, expiryDate: Instant): ResponseCookie {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(true)
            .path("/")
            .maxAge(Duration.between(Instant.now(), expiryDate))
            .sameSite("Strict")
            .build()
    }

}
