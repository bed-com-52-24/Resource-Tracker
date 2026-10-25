package com.resourcetracker.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Query
import java.time.Instant

enum class PasswordTokenPurpose {
    SETUP,
    RESET,
}

@Entity
@Table(
    name = "password_tokens",
    indexes = [Index(name = "idx_password_tokens_user", columnList = "user_id, purpose")],
)
class PasswordToken(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    val id: Int = 0,

    @Column(name = "user_id", nullable = false)
    val userId: Int,

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    val tokenHash: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 10)
    val purpose: PasswordTokenPurpose,

    @Column(name = "expires_at")
    val expiresAt: Instant? = null,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    val createdAt: Instant? = null,
)

interface PasswordTokenRepository : JpaRepository<PasswordToken, Int> {
    fun findByTokenHash(tokenHash: String): PasswordToken?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordToken t where t.tokenHash = :tokenHash")
    fun findForUpdateByTokenHash(@Param("tokenHash") tokenHash: String): PasswordToken?

    fun deleteByUserIdAndPurposeAndUsedAtIsNull(userId: Int, purpose: PasswordTokenPurpose): Int
}
