package dev.joyson.aiworkbench

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<PersonalAiWorkbenchApplication>().with(TestcontainersConfiguration::class).run(*args)
}
