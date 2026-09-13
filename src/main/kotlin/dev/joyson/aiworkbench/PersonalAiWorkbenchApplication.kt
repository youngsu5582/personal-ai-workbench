package dev.joyson.aiworkbench

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PersonalAiWorkbenchApplication

fun main(args: Array<String>) {
	runApplication<PersonalAiWorkbenchApplication>(*args)
}
