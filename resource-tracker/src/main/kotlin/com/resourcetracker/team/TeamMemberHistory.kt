package com.resourcetracker.team

import com.resourcetracker.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

enum class TeamMemberAction { ADDED, REMOVED, TRANSFERRED }

@Entity
@Table(name = "team_member_history")
class TeamMemberHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    val id: Int = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_team_id")
    var fromTeam: Team?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_team_id")
    var toTeam: Team?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var action: TeamMemberAction,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "performed_by", nullable = false)
    var performedBy: User,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

interface TeamMemberHistoryRepository : JpaRepository<TeamMemberHistory, Int> {
    @Query(
        "select h from TeamMemberHistory h where h.toTeam.id = :teamId or h.fromTeam.id = :teamId order by h.createdAt desc"
    )
    fun findHistoryForTeam(teamId: Int): List<TeamMemberHistory>

    fun findByUserIdOrderByCreatedAtDesc(userId: Int): List<TeamMemberHistory>
}
