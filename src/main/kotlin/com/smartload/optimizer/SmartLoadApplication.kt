package com.smartload.optimizer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SmartLoadApplication

fun main(args: Array<String>) {
    runApplication<SmartLoadApplication>(*args)
}
