package com.resourcetracker.project

// Local exceptions for issue #16. Move to a shared package if the team adds common ones.
class NotFoundException(message: String) : RuntimeException(message)
class ConflictException(message: String) : RuntimeException(message)
class BadRequestException(message: String) : RuntimeException(message)
