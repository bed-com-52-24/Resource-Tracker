package com.resourcetracker.team

import com.resourcetracker.common.ApiException
import org.springframework.http.HttpStatus

class InvalidTeamRequestException(message: String) :
    ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message)
