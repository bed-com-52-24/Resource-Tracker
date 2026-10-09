package com.resourcetracker.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "app.security")
data class SecurityProps(
    val jwtSecret: String,
    val accessTtl: Duration = Duration.ofMinutes(15),
    val refreshTtl: Duration = Duration.ofDays(7),
    val maxFailedAttempts: Int = 5,
    val failureWindow: Duration = Duration.ofMinutes(15),
    val lockDuration: Duration = Duration.ofMinutes(15),
)