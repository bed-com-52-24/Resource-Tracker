package com.resourcetracker.auth

import jakarta.persistence.*
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Int = 0,
    @Column(nullable = false) val userId: Int,
    @Column(nullable = false, unique = true, length = 64) val tokenHash: String,
    @Column(nullable = false) val expiresAt: Instant,
    @Column(nullable = false) var revoked: Boolean = false,
)

@Entity
@Table(name = "revoked_access_tokens")
class RevokedAccessToken(
    @Id val jti: String,
    @Column(nullable = false) val expiresAt: Instant,
)

interface RefreshTokenRepository : JpaRepository<RefreshToken, Int> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    fun findForUpdate(@Param("hash") hash: String): RefreshToken?

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    fun deleteExpired(@Param("now") time: Instant): Int

    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.userId = :userId and t.revoked = false")
    fun revokeAllByUserId(@Param("userId") userId: Int): Int

}

interface RevokedAccessTokenRepository : JpaRepository<RevokedAccessToken, String> {
    @Modifying
    @Query("delete from RevokedAccessToken t where t.expiresAt < :now")
    fun deleteExpired(@Param("now") time: Instant): Int
}

@Component
class TokenCleanupJob(
    private val refreshTokens: RefreshTokenRepository,
    private val revoked: RevokedAccessTokenRepository,
    private val clock: Clock,
) {
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    fun purge() {
        val now = clock.instant()
        refreshTokens.deleteExpired(now)
        revoked.deleteExpired(now)
    }
}
