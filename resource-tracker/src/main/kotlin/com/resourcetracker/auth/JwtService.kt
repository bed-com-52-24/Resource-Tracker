package com.resourcetracker.auth
import com.resourcetracker.config.SecurityProps
import com.resourcetracker.user.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.util.Date
import java.util.UUID

data class IssuedAccessToken(val token: String, val jti: String, val expiresAt: Instant)

@Service
class JwtService(private val props: SecurityProps, private val clock: Clock){
    private val key = Keys.hmacShaKeyFor(props.jwtSecret.toByteArray())

    fun issueAccessToken(user: User): IssuedAccessToken{
        val now = clock.instant()
        val exp = now.plus(props.accessTtl)
        val jti = UUID.randomUUID().toString()
        val token = Jwts.builder()
            .id(jti)
            .subject(user.id.toString())
            .claim("role",user.role.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .signWith(key)
            .compact()
        return IssuedAccessToken(token, jti, exp)
    }

    fun parse(token: String): Claims? = try {
        Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload
    } catch (_: JwtException){
        null
    }catch(_: IllegalArgumentException){
        null
    }
}