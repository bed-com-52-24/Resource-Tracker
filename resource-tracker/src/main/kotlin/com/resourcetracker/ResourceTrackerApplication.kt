package com.resourcetracker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ResourceTrackerApplication

fun main(args: Array<String>) {
	runApplication<ResourceTrackerApplication>(*args)
}
