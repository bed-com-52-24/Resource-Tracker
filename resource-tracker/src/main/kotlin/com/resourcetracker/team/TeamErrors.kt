package com.resourcetracker.team

import com.resourcetracker.common.ValidationException

class InvalidTeamRequestException(message: String) :
    ValidationException(message)
