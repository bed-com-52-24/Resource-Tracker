package com.resourcetracker.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "Public service health check")
class HealthController {
    @GetMapping("/health")
    @Operation(summary = "Check service health", 
                description = "Public endpoint. Returns the service status. No authentication required.", 
                security = []
    )
    @ApiResponses(
        value = [
        ApiResponse(responseCode = "200", description = "Service is up"),
    ])
    fun ping() = mapOf(
        "status" to "UP",
        "timestamp" to Instant.now().toString())
}
