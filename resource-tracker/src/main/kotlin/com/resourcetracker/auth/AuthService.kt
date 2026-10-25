package com.resourcetracker.auth

import com.resourcetracker.common.ApiException
import com.resourcetracker.common.AccountLockedException
import com.resourcetracker.common.InvalidCredentialsException
import com.resourcetracker.common.InvalidRefreshTokenException
import com.resourcetracker.common.WeakPasswordException
import com.resourcetracker.config.SecurityProps
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.PasswordPolicy
import com.resourcetracker.user.User
import com.resourcetracker.user.UserRepository
import com.resourcetracker.user.PasswordSetRequest
import com.resourcetracker.user.PasswordSetResponse
import com.resourcetracker.user.ForgotPasswordRequest
import com.resourcetracker.user.PasswordResetResponse
import com.resourcetracker.common.AccountDeactivatedException
import org.springframework.stereotype.Service
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import kotlin.io.encoding.Base64

@Service
class AuthService(
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val revokedTokens: RevokedAccessTokenRepository,
    private val encoder: PasswordEncoder,
    private val jwt: JwtService,
    private val props: SecurityProps,
    private val clock: Clock,
    private val passwordTokens: PasswordTokenRepository,
    private val passwordTokenService: PasswordTokenService,
){
    private val random = SecureRandom()

    private val dummyHash: String = encoder.encode("not-a-ral-password-for-timing")!!

    @Transactional(noRollbackFor = [ApiException::class])
    fun login(req: LoginRequest): TokenPair{
        val now = clock.instant()
        val user = users.findForUpdateByUsername(req.username)
        if(user == null){
            encoder.matches(req.password, dummyHash)
            throw InvalidCredentialsException()
        }
        val lockedUntil = user.lockedUntil
        if (lockedUntil != null && lockedUntil.isAfter(now)) {
            throw AccountLockedException(lockedUntil)
        }
        val passwordHash = user.passwordHash
        val passwordMatches = encoder.matches(req.password, passwordHash ?: dummyHash)
        if(passwordHash == null || !passwordMatches){
            registerFailure(user, now)
            throw InvalidCredentialsException()
        }

        if(!user.active) throw AccountDeactivatedException()

        user.failedAttempts = 0
        user.failedWindowStart = null
        user.lockedUntil = null
        return issueTokens(user, now)
    }

    @Transactional(noRollbackFor = [ApiException::class])
    fun refresh(rawToken: String): TokenPair{
        val now = clock.instant()
        val stored = refreshTokens.findForUpdate(sha256(rawToken)) ?: throw InvalidRefreshTokenException()
        if(stored.revoked || stored.expiresAt.isBefore(now)) throw InvalidRefreshTokenException()

        stored.revoked  = true
        val user = users.findById(stored.userId).orElse(null)
        if(user == null || !user.active) throw InvalidRefreshTokenException()
        return issueTokens(user,now)
    }

    @Transactional
    fun logout(caller: AuthenticatedUser, rawRefreshToken: String){
        refreshTokens.findForUpdate(sha256(rawRefreshToken))
            ?. takeIf {it.userId == caller.id}
            ?. revoked = true

        revokedTokens.save(RevokedAccessToken(caller.jti, caller.tokenExpiresAt))
    }

    @Transactional
    fun setPassword(request: PasswordSetRequest): PasswordSetResponse {
        val now = clock.instant()
        val token = passwordTokens.findForUpdateByTokenHash(passwordTokenService.hash(request.code))
            ?: throw invalidPasswordToken()
        val expiresAt = token.expiresAt
        if (token.usedAt != null || (expiresAt != null && !expiresAt.isAfter(now))) {
            throw invalidPasswordToken()
        }

        val user = users.findById(token.userId).orElse(null)
        if (user == null || !user.active) throw invalidPasswordToken()

        val violations = PasswordPolicy.violations(request.password)
        if (violations.isNotEmpty()) throw WeakPasswordException(violations)

        user.passwordHash = encoder.encode(request.password)!!
        user.failedAttempts = 0
        user.failedWindowStart = null
        user.lockedUntil = null
        token.usedAt = now
        passwordTokens.deleteByUserIdAndPurposeAndUsedAtIsNull(user.id, PasswordTokenPurpose.SETUP)
        passwordTokens.deleteByUserIdAndPurposeAndUsedAtIsNull(user.id, PasswordTokenPurpose.RESET)
        refreshTokens.revokeAllByUserId(user.id)
        return PasswordSetResponse(passwordSet = true)
    }

    @Transactional
    fun forgotPassword(request: ForgotPasswordRequest): PasswordResetResponse {
        val user = users.findByEmailIgnoreCase(request.email.trim())
        if (user != null && user.active) {
            passwordTokens.deleteByUserIdAndPurposeAndUsedAtIsNull(user.id, PasswordTokenPurpose.RESET)
            passwordTokenService.issue(user, PasswordTokenPurpose.RESET, clock.instant().plus(props.resetTokenTtl))
        }
        return PasswordResetResponse("If an active account matches that email, a password reset link has been sent.")
    }

    private fun registerFailure(user: User, now:Instant){
        val windowStart = user.failedWindowStart
        val windowExpired = windowStart == null || windowStart.plus(props.failureWindow).isBefore(now)

        if(windowExpired || user.lockedUntil != null){
            user.failedAttempts = 0
            user.failedWindowStart = now
            user.lockedUntil = null
        }
        user.failedAttempts++
        if(user.failedAttempts >= props.maxFailedAttempts){
            user.lockedUntil = now.plus(props.lockDuration)
        }
    }

    private fun issueTokens(user: User, now: Instant):TokenPair{
        val access = jwt.issueAccessToken(user)
        val bytes = ByteArray(32).also(random::nextBytes)
        val refresh = Base64.UrlSafe.encode(bytes).trimEnd('=')
        refreshTokens.save(
            RefreshToken(userId = user.id, tokenHash = sha256(refresh), expiresAt = now.plus(props.refreshTtl)),
        )
        return TokenPair(
            accessToken = access.token,
            refreshToken = refresh,
            accessTokenExpiresIn = props.accessTtl.seconds,
            refreshTokenExpiresIn = props.refreshTtl.seconds,
        )
    }

    private fun invalidPasswordToken() =
        ApiException(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "INVALID_TOKEN",
            "Password token is invalid or unavailable",
        )

    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("")
    { "%02x".format(it)}
}
