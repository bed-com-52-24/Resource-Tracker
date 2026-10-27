package com.resourcetracker.request.repository

import com.resourcetracker.request.entity.WaitlistEntry
import org.springframework.data.jpa.repository.JpaRepository

interface WaitlistEntryRepository : JpaRepository<WaitlistEntry, Int>
