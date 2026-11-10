package com.resourcetracker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
class ResourceTrackerApplication

fun main(args: Array<String>) {
	runApplication<ResourceTrackerApplication>(*args)
}
