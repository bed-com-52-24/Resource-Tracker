package com.resourcetracker.project

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/projects")
class ProjectController(private val service: ProjectService) {

    // TODO(#16): add @PreAuthorize("hasRole('ADMIN')") on create/update/deactivate once auth (#12) is merged

    @PostMapping
    fun create(@Valid @RequestBody body: CreateProjectRequest): ResponseEntity<ProjectResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(service.create(body))

    @PutMapping("/{id}")
    fun update(@PathVariable id: UUID, @Valid @RequestBody body: UpdateProjectRequest): ProjectResponse =
        service.update(id, body)

    @PostMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: UUID): ProjectResponse = service.deactivate(id)

    @GetMapping("/{id}")
    fun get(@PathVariable id: UUID): ProjectResponse = service.get(id)

    // Sort format: ?sort=name:asc&sort=startDate:desc (max 3)
    @GetMapping
    fun list(
        @RequestParam(required = false) departmentId: UUID?,
        @RequestParam(required = false) active: Boolean?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startTo: LocalDate?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(defaultValue = "name:asc") sort: List<String>
    ): Page<ProjectResponse> =
        service.list(departmentId, active, startFrom, startTo, search, page, size, sort)

    // ---- Local error handling (move to a shared @RestControllerAdvice when the team adds one) ----
    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = error(HttpStatus.NOT_FOUND, e.message)

    @ExceptionHandler(ConflictException::class)
    fun conflict(e: ConflictException) = error(HttpStatus.CONFLICT, e.message)

    @ExceptionHandler(BadRequestException::class)
    fun badRequest(e: BadRequestException) = error(HttpStatus.BAD_REQUEST, e.message)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalid(e: MethodArgumentNotValidException): ResponseEntity<Map<String, Any?>> {
        val details = e.bindingResult.fieldErrors.associate { it.field to it.defaultMessage }
        return ResponseEntity.badRequest()
            .body(mapOf("status" to 400, "error" to "Validation failed", "details" to details))
    }

    private fun error(status: HttpStatus, message: String?): ResponseEntity<Map<String, Any?>> =
        ResponseEntity.status(status).body(mapOf("status" to status.value(), "error" to message))
}
