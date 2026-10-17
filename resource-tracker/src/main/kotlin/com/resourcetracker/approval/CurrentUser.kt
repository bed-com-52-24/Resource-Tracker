package com.resourcetracker.approval

import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Finds the id of the logged-in user.
 * ADAPT THIS to match how the team's login works.
 */
@Component
class CurrentUser(private val userRepository: com.resourcetracker.user.UserRepository) {

    fun id(): Int {
        val username = SecurityContextHolder.getContext().authentication?.name
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not logged in")
        val user = userRepository.findByUsername(username)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user")
        return user.id
    }
}