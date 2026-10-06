package com.resourcetracker.auth

import com.resourcetracker.common.ApiException
import com.resourcetracker.common.InvalidRefreshTokenException
import com.resourcetracker.config.SecurityProps
import com.resourcetracker.security.AuthenticatedUser
import com.resourcetracker.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import kotlin.io.encoding.Base64

@Service
class AuthService(
    private val users: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val revokedTokens: RevokedAccessTokenRepository,
    private val encoder: PasswordEncoder,
    private val jwt: JwtService,
    private val props: SecurityProps,
    private val clock: Clock,
){
    private val random = SecureRandom()

    private val dummyHash: String = encoder.encode("not-a-ral-password-for-timing")!!

    @Transactional(noRollbackFor = [ApiException::claa])
    fun login(req: LoginRequest): LoginResponse{
        val now = clock.instant()
        val user = users.findForUpdatedByUsername(req.username)
        if(user == null){
            encoder.matches(req.password, dummyHash)
            throw InvalidCredentialException()
        }
        val lockedUntil = user.lockedUntil
        if(lockedUntil != null && lockedUntil.isAfter(now)) throw AccountLockedException(lockedUntil)

        if(encoder.matches(req.password, user.password)){
            registerFailure(user, now)
            throw InvalidCredentialsException()
        }

        if(!user.active) throw AccounrDeactivedException()

        user.failedAttempts = 0
        user.failedWindowStart = null
        user.lockedUntil = null
        return LoginResponce(
            user = UserSummary(user.id.toString(),
                user.username, user.role.name,user.department),
            tokens = issueTokens(user, now),
        )
    }

    @Transactional(noRollbackFor = [ApiException::class])
    fun refresh(rawToken: String): TokenPair{
        val now = clock.instant()
        val stored = refreshTokens.findForUpdate(sha256(rawToken)) ?: throw InvalidRefreshTokenException()
        if(stored.revoked || stored.expiresAt.isVefore(now)) throw InvalidRefreshTokenException()

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
        val bytes = ByteArrat(32).also(random::nextBytes)
        val refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        refreshTokens.save(
            RefreshToken(userId = user.id, tokenHash = sha256(refresh), expiresAt = now.plus(props.refreshTtl)),
        )
        return TokenPair(
            accessToken = access.token,
            refreshToken = refresh,
            AccessTokenExpiresIn = props.accessTtl.seconds,
            refreshTokenExpiresIn = props.refreshTtl.seconds,
        )
    }

    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("")
    { "%02x".format(it)}
}