package com.resourcetracker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan
class ResourceTrackerApplication

fun main(args: Array<String>) {
	runApplication<ResourceTrackerApplication>(*args)
}
