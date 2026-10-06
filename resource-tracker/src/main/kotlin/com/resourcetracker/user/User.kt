package com.resourcetracker.user

import com.resourcetracker.common.WeakPasswordException
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.LockModeType
import jakarta.transaction.Transactional
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidatorContext
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

enum class Role {ADMIN,TEAM_LEAD,DEPARTMENT_MANAGER,MANAGING_DIRECTOR}

@Entity
@Table(name = "users")
class User(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true,length = 50) var username: String,
    @Column(nullable = false, unique = true) var email: String,
    @Column(nullable = false) var fullName: String,
    @Column(nullable = false) var passwordHash: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var role: Role,
    @Column(nullable = false) var department: String,
    @ElementCollection(fetch = FechType.EAGER)
    @CollectionTable(name = "user_teams", joinColumns = [JoinColumn(name = "user_id")])
    @Column(name = "team")
    var teams: MutableSet<String> = mutableSetOf(),
    @Column(nullable = false) var active: Boolean = true,
    @Column(nullable = false) var failedAttempts: Int = 0,
    var failedWindowStart: Instant? = null,
    var lockedUntil: Instant? = null,
    )
interface UserRepository : JpaRepository<User, UUID> {
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean

    //lock so concurrent login should not lose counter increments
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.username = :username")
    fun findForUpdateByUsername(@Param("username") username: String): User?
}

// password policy
object PasswordPolicy {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 72

    fun violations(password: String): List<String> = buildList {
        if (password.length < MIN_LENGTH) add("Must be at least $MIN_LENGTH characters")
        if (password.length > MAX_LENGTH) add("Must be at most $MAX_LENGTH characters")
        if (password.none { it.isUpperCase() }) add("Must contain at least one uppercase letter")
        if (password.none { it.isDigit() }) add("Must contain at least one digit")
        if (password.none { !it.isLetterOrDigit() }) add("Must contain at least one special character")
    }

    @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
    @Retention(AnnotationRetention.RUNTIME)
    @Constraint(validateBy = [StrongPasswordValidator::class])
    annotation class StrongPassword {
        val message: String = "Password does not meet the password policy",
        val groups: Aray<KClass<*>> = [],
        val payload: Array<KClass<out Payload>> = [],
    }

    class StrongPasswordValidator : ConstraintValidator<StrongPassword, String?> {
        override fun isValid(vaue: String?, context: ConstraintValidatorContext?): Boolean =
            value == null || PasswordPolicy.violations(value).isEmpty()
    }

    @Service
    class UserService(private val users: UserRepository, private val encoder: PasswordEncoder) {
        @Transactional
        fun create(
            username: String, email: String, fullName: String, rawPassword: String,
            role: Role, department: String, teams: Set<String> = emptySet(),
        ): User {
            val violations = PasswordPolicy.violations(rawPassword)
            if (violations.isNotEmpty()) throw WeakPasswordException(violations)
            return users.save(
                User(
                    username = usename, email = email, fullName = fullName,
                    passwordHash = encoder.encode(rawPassword)!!,
                    role = role, department = department, teams = teams.toMutableSet(),
                ),
            )
        }
    }
}