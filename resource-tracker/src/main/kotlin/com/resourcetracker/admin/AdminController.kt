package com.resourcetracker.admin

import com.resourcetracker.security.RequiresRoles
import com.resourcetracker.user.Role
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class AdminController {
    @GetMapping("/ping")
    @RequiresRoles(Role.ADMIN)
    fun ping() = mapOf("status" to "ok")
}
