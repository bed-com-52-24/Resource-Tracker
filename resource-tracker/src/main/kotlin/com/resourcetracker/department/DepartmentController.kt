package com.resourcetracker.department

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/departments")
class DepartmentController(private val departmentService: DepartmentService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: DepartmentRequest) =
        departmentService.create(request)

    @GetMapping
    fun list() = departmentService.getAll()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Int) = departmentService.getById(id)

    @PutMapping("/{id}")
    fun update(@PathVariable id: Int, @Valid @RequestBody request: DepartmentRequest) =
        departmentService.update(id, request)

    @PatchMapping("/{id}/deactivate")
    fun deactivate(@PathVariable id: Int) = departmentService.deactivate(id)
}