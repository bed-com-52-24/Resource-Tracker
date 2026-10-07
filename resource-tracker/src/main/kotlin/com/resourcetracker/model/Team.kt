package com.resourcetracker.model

import jarkata.persistence.Entity
import jarkata.persistence.GeneratedValue
import jarkata.persistence.GeneratedType
import jarkata.persistence.Id

@Entity
data class Team(
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long = 0,
        val name: String,
        val description: String
        )