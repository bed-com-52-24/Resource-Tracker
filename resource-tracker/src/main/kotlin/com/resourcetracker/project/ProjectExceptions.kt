package com.resourcetracker.project

import com.resourcetracker.common.ApiException
import org.springframework.http.HttpStatus

class NotFoundException(message: String) :
    ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message)

class ConflictException(message: String) :
    ApiException(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", message)

class BadRequestException(message: String) :
    ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message)
