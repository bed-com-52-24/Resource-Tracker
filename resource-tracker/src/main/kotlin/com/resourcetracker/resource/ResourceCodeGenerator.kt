package com.resourcetracker.resource

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.LockModeType
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Entity @Table(name = "code_sequence")
class CodeSequence(
    @Id @Column(length = 30) var name: String,
    @Column(name = "next_value", nullable = false) var nextValue: Long = 1
)

interface CodeSequenceRepository : JpaRepository<CodeSequence, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CodeSequence c where c.name = :name")
    fun lockByName(@Param("name") name: String): CodeSequence?
}

@Service
class ResourceCodeGenerator(private val repo: CodeSequenceRepository) {
    @Transactional(propagation = Propagation.Mandatory)
    fun next(): String {
        val seq = repo.lockByName("RESOURCE") ?: repo.save(CodeSequence("RESOURCE"))
        val value = seq.nextValue++
        return "RES-%04d".format(value)
    }
}
