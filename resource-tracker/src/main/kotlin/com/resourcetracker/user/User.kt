package com.resourcetracker.user

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.LockModeType
import jakarta.persistence.FetchType
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import com.resourcetracker.department.Department
import com.resourcetracker.team.Team
import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import kotlin.reflect.KClass

enum class Role { ADMIN, TEAM_LEAD, DEPARTMENT_MANAGER, MANAGING_DIRECTOR }

@Entity
@Table(name = "users")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "user_id") val id: Int = 0,
    @Column(nullable = false, unique = true, length = 50) var username: String,
    @Column(nullable = false, unique = true) var email: String,
    @Column(nullable = false) var fullName: String,
    @Column(nullable = false) var passwordHash: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false) var role: Role,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    var department: Department,
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_teams",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "team_id")]
    )
    var teams: MutableSet<Team> = mutableSetOf(),
    @Column(nullable = false) var active: Boolean = true,
    @Column(nullable = false) var failedAttempts: Int = 0,
    var failedWindowStart: Instant? = null,
    var lockedUntil: Instant? = null,
)

interface UserRepository : JpaRepository<User, Int> {
    fun findByUsername(username: String): User?
    fun existsByUsernameIgnoreCase(username: String): Boolean
    fun existsByEmailIgnoreCase(email: String): Boolean
    fun existsByEmailIgnoreCaseAndIdNot(email: String, id: Int): Boolean

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role and u.active = true")
    fun findActiveByRoleForUpdate(@Param("role") role: Role): List<User>

    @Query(
        """select u from User u
          where lower(u.username) like lower(concat('%', :q, '%'))
             or lower(u.email) like lower(concat('%', :q, '%'))
             or lower(u.fullName) like lower(concat('%', :q, '%'))"""
    )
    fun search(@Param("q") q: String, pageable: Pageable): Page<User>

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

    @Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY_GETTER)
    @Retention(AnnotationRetention.RUNTIME)
    @Constraint(validatedBy = [StrongPasswordValidator::class])
    annotation class StrongPassword(
        val message: String = "Password does not meet the password policy",
        val groups: Array<KClass<*>> = [],
        val payload: Array<KClass<out Payload>> = [],
    )

    class StrongPasswordValidator : ConstraintValidator<StrongPassword, String?> {
        override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean =
            value == null || PasswordPolicy.violations(value).isEmpty()
    }

}
